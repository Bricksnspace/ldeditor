/*
	Copyright 2014 Mario Pascucci <mpascucci@gmail.com>
	This file is part of LDEditor

	LDEditor is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	LDEditor is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with LDEditor.  If not, see <http://www.gnu.org/licenses/>.

*/


package bricksnspace.ldeditor;


import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import bricksnspace.j3dgeom.JSimpleGeom;
import bricksnspace.j3dgeom.Matrix3D;
import bricksnspace.j3dgeom.Point3D;
import bricksnspace.ldraw3d.DrawHelpers;
import bricksnspace.ldraw3d.Gadget3D;
import bricksnspace.ldraw3d.HandlingListener;
import bricksnspace.ldraw3d.LDRenderedPart;
import bricksnspace.ldraw3d.LDrawGLDisplay;
import bricksnspace.ldraw3d.PickMode;
import bricksnspace.ldraw3d.ProgressUpdater;
import bricksnspace.ldraw3d.DrawHelpers.PointerMode;
import bricksnspace.ldrawlib.ConnectionHandler;
import bricksnspace.ldrawlib.ConnectionPoint;
import bricksnspace.ldrawlib.ConnectionTypes;
import bricksnspace.ldrawlib.LDPrimitive;
import bricksnspace.ldrawlib.LDrawCommand;
import bricksnspace.ldrawlib.LDrawPart;
import bricksnspace.ldrawlib.LDrawPartType;
import bricksnspace.ldrawlib.PartQueryable;
import bricksnspace.simpleundo.Undo;
import bricksnspace.simpleundo.UndoableAction;
import bricksnspace.simpleundo.UndoableOperation;





/**
 * LDraw model editing and rendering handling
 * 
 * @author Mario Pascucci
 *
 */
public class LDConnectionEditor implements Runnable, UncaughtExceptionHandler, 
	HandlingListener, KeyListener, PartQueryable {
	
	private LDrawPart mainModel;
	private ProgressUpdater updater;
	private boolean completed;
	private LDrawGLDisplay display;
	private ConnectionHandler connHandler;
	private ConnectionPoint currentConnection;
	private ConnectionPoint selectedConnection;
	private Gadget3D currentConnRendered;
	
	// GUI notification listener
	private EditChangeListener listener = null;
	
	// editing options and settings
	private static boolean snapping = true;
	private static boolean autoconnect = true;
	private static boolean repeatBrick = true;
	private static boolean axisEnabled = true;
	private static boolean gridEnabled = true;
	private static float snapSize = 4;
	private static float gridSize = 20;
	private static float rotateStep = (float) (Math.PI/2);
	
	// display feedback
	Point3D prevCursor = new Point3D(0,0,0);
	
	// rotate point
	ConnectionPoint rotatePoint = null;
	
	// internal status
	private boolean movingConnHead = false;
	private boolean movingConnBase = false;
	private boolean deleteConn = false;
	
	// drawing helper
	private DrawHelpers dh;
	
	// undo subsystem
	private Undo<ConnectionPoint> undo;
	
	// selecting and hiding
	private Set<Integer> selectedParts = new HashSet<Integer>();
	private List<Integer> hiddenParts = new ArrayList<Integer>();
	
	// duplicate check
	private List<ConnectionPoint> duplicateList;


	
	
	
	
	private LDConnectionEditor(LDrawPart model, LDrawGLDisplay gldisplay) {
		
		mainModel = model;
		display = gldisplay;
		connHandler = new ConnectionHandler(this);
		ConnectionPoint.clearCache();
		if (LDrawPart.isLdrPart(model.getLdrawId())) {
			connHandler.addPartConnections(model.getLdrawId());
		}
		else {
			connHandler.addAllConnections(model);
		}
		undo = new Undo<ConnectionPoint>();
		dh = new DrawHelpers(display);
		display.resetView();
		display.resetZoom();
		display.rotateY(15);
		display.rotateX(-25);
		dh.setSnap(snapSize);
		dh.setGridSize(gridSize);
		dh.addAxis();
		dh.addPointer();
		dh.addGrid();
		dh.enableAxis(axisEnabled);
		dh.enableGrid(gridEnabled);
		display.enableSelection(true);
		display.addPickListener(this);
		display.getCanvas().addKeyListener(this);
		displayConnections();
	}
	
	
	
	public static LDConnectionEditor newLDConnectionEditor(LDrawPart m, LDrawGLDisplay gld) {
		
		LDConnectionEditor model = new LDConnectionEditor(m, gld);
		return model;
	}

	
	
	public void closeEditor() {
		
		undo = null;
		mainModel = null;
		connHandler = null;
		dh = null;
		display.getCanvas().removeKeyListener(this);
		display.removePickListener(this);
	}
	
	
	
	public void loadConnections(List<ConnectionPoint> lp) {

		ConnectionPoint.clearCache();
		connHandler = new ConnectionHandler(this);
		for (ConnectionPoint p: lp) {
			connHandler.addSingleConn(p);
		}
		displayConnections();
	}

	
	/////////////////////
	//
	//  Editor options and settings
	//
	/////////////////////


	
	public void registerChangeListener(EditChangeListener listener) {
		this.listener = listener;
	}



	/**
	 * Returns snap-to-grid status
	 * @return true if enabled
	 */
	public static boolean isSnapping() {
		return snapping;
	}



	/**
	 * Enable/disable snapping
	 * @param snapping status
	 */
	public static void setSnapping(boolean enable) {
		snapping = enable;
	}



	/**
	 * Return autoconnect status
	 * @return true if enabled 
	 */
	public boolean isAutoconnect() {
		return autoconnect;
	}



	/**
	 * Enable/disable autoconnect
	 * @param autoconnect status
	 */
	public static void setAutoconnect(boolean enable) {
		autoconnect = enable;
	}
	

	
	public static boolean isRepeatBrick() {
		return repeatBrick;
	}



	public static void setRepeatBrick(boolean repeatBrick) {
		LDConnectionEditor.repeatBrick = repeatBrick;
	}



	/**
	 * Returns snap size
	 * @return snap size in LDU
	 */
	public float getSnap() {
		return dh.getSnap();
	}



	/**
	 * Sets snap size
	 * @param snap size in LDU
	 */
	public void setSnap(float snap) {
		snapSize = snap;
		dh.setSnap(snapSize);
	}



	/**
	 * Grid size in LDU
	 * @return grid size
	 */
	public float getGridSize() {
		return dh.getGridSize();
	}
	
	
	

	/**
	 * Set grid size
	 * @param size
	 */
	public void setGridSize(float size) {
		gridSize = size;
		dh.setGridSize(gridSize);
	}


	
	public static float getRotateStep() {
		return rotateStep;
	}



	public static void setRotateStep(float rotateStep) {
		LDConnectionEditor.rotateStep = rotateStep;
	}




	
	
	/////////////////////
	//
	//  Edit helpers
	//
	/////////////////////

	
	public void downY() {
		dh.downY();
	}



	public void upY() {
		dh.upY();
	}



	public void downX() {
		dh.downX();
	}



	public void upX() {
		dh.upX();
	}



	public void downZ() {
		dh.downZ();
	}



	public void upZ() {
		dh.upZ();
	}



	public void alignXY() {
		dh.alignXY();
	}



	public void alignYZ() {
		dh.alignYZ();
	}



	public void alignXZ() {
		dh.alignXZ();
	}



	public void enableAxis(boolean enable) {
		
		axisEnabled = enable;
		dh.enableAxis(axisEnabled);
	}


	public void enableGrid(boolean enable) {
		gridEnabled = enable;
		dh.enableGrid(gridEnabled);
	}



	public void resetGrid() {
		dh.resetGrid();
		//updateMovingPart();
	}



	public void resetPointerMatrix() {
		dh.resetPointerMatrix();
		//updateMovingPart();
	}

	
	
	public void resetView() {
		display.resetView();
		display.rotateY(15);
		display.rotateX(-25);
	}


	/**
	 * Align grid and gridplane to a primitive or a transformation matrix
	 * 
	 * if primitive isn't suitable to align, do nothing
	 */
	public void alignGridToSelectedPart() {
		
		if (getSelected().size() != 1)
			return;
		// gets first part selected
		LDPrimitive dp = getPart(getSelected().iterator().next());
		if (dp.getType() == LDrawCommand.REFERENCE) {
			// align to reference transformation matrix
			dh.setGridMatrix(dp.getTransformation());
		}
		else if (dp.getType() == LDrawCommand.TRIANGLE ||
				dp.getType() == LDrawCommand.QUAD) {
			// align to plane where quad or triangle lies
			dh.setGridMatrix(JSimpleGeom.alignMatrix(new Point3D(dp.getPointsFV(), 0),
					new Point3D(dp.getPointsFV(), 3),
					new Point3D(dp.getPointsFV(), 6)));
		}
		else return;
	}
	
	



	/////////////////////
	//
	//  Model handling
	//
	/////////////////////




	/**
	 * @return
	 * @see bricksnspace.ldrawlib.LDrawPart#getPartType()
	 */
	public LDrawPartType getPartType() {
		return mainModel.getPartType();
	}



	

	public String getDescription() {
		return mainModel.getDescription();
	}



	/**
	 * @return
	 * @see bricksnspace.ldrawlib.LDrawPart#getLdrawId()
	 */
	public String getLdrawid() {
		return mainModel.getLdrawId();
	}



	/**
	 * @param id
	 * @return
	 * @see bricksnspace.ldrawlib.LDrawPart#getPartById(int)
	 */
	private LDPrimitive getPart(int id) {
		return mainModel.getPartById(id);
	}



	/**
	 * Add or replace a part to model and to rendered model display
	 * @param p
	 */
	public LDPrimitive addPart(LDPrimitive p) {
		
		LDPrimitive old = mainModel.addPart(p);
		LDRenderedPart rp = LDRenderedPart.newRenderedPart(p);
		display.addRenderedPart(rp);
		if (old != null) {
			connHandler.delConnections(old);
		}
		connHandler.addConnections(p);
		return old;
	}
	
	
	
	/**
	 * Deletes part p from model, connections and display
	 * @param p part to remove
	 * @return removed part
	 */
	public LDPrimitive delPart(LDPrimitive p) {
		
		display.delRenderedPart(p.getId());
		connHandler.delConnections(p);
		return mainModel.delPart(p);
	}
	

	
	private void addConnection(ConnectionPoint p) {
		
		connHandler.addSingleConn(p);
		Gadget3D rp = DrawHelpers.getConnectionPoint(p);
		display.addGadget(rp);
	}
	

	
	private void delConnection(ConnectionPoint p) {
		
		connHandler.delSingleConn(p);
		display.removeGadget(p.getId());
	}
	
	
	
	/////////////////////
	//
	//  Connections query
	//
	/////////////////////

	
	public List<ConnectionPoint> getConnectionsByType(int ct) {
		return connHandler.getConnectionsByType(ct);
	}


	
	public void dumpConnections() {
		connHandler.dumpConnections();
	}
	
	
	
	/////////////////////
	//
	//  Editing functions
	//
	/////////////////////

	
	
	public void startAddConnection(ConnectionTypes type) {
		
		releaseMovingPart();
		switch (type.getFamily()) {
		case POINT:
			dh.setPointerMode(PointerMode.CROSS,Color.MAGENTA);
			break;
		case RAIL:
			dh.setPointerMode(PointerMode.CLIP,Color.MAGENTA);
			break;
		case VECTOR:
			dh.setPointerMode(PointerMode.STUD,Color.MAGENTA);
			break;
		default:
			dh.setPointerMode(PointerMode.STUD,Color.MAGENTA);
			break;
		}
		dh.movePointer(prevCursor);
		movingConnBase = true;
		currentConnection = ConnectionPoint.createCPoint(type.getId());
	}
	

	
	public void startDeleteConnection() {
		
		releaseMovingPart();
		deleteConn = true;
		dh.setPointerMode(PointerMode.STUD,0x2ff8080);
		dh.movePointer(prevCursor);
	}
	
	
//	public void startDuplicateMode() { 
//		
//		releaseMovingPart();
//		duplicate = true;
//		dh.setPointerMode(PointerMode.STUD,Color.RED);
//		dh.movePointer(prevCursor);
//	}
//
//	
//	
//	public void startRotate(JPanel tool) {
//		
//		toolPanel = tool;
//		releaseMovingPart();
//		dh.setPointerMode(PointerMode.STUD, Color.ORANGE);
//		startRotation = true;
//	}
//

	
	
	public int dupCheck() {
		duplicateList = new ArrayList<ConnectionPoint>();
		for (ConnectionTypes ct: ConnectionTypes.listTypes()) {
			List<ConnectionPoint> lc = connHandler.getConnectionsByType(ct.getId());
			for (int ci=0;ci<lc.size()-1;ci++) {
				ConnectionPoint cu = lc.get(ci);
				for (int cj=ci+1;cj<lc.size();cj++) {
					ConnectionPoint ck = lc.get(cj);
					if (Math.abs(cu.getP1().x-ck.getP1().x) < 0.001 &&
							Math.abs(cu.getP1().y-ck.getP1().y) < 0.001 &&
							Math.abs(cu.getP1().z-ck.getP1().z) < 0.001 &&
							Math.abs(cu.getP2().x-ck.getP2().x) < 0.001 &&
							Math.abs(cu.getP2().y-ck.getP2().y) < 0.001 &&
							Math.abs(cu.getP2().z-ck.getP2().z) < 0.001 ) {
						// it is a duplicated connection point
						duplicateList.add(ck);
					}
				}
			}
		}
		return duplicateList.size();
	}
	
	
	
	public void dupRemove() {
		
		undo.startUndoRecord();
		for (ConnectionPoint cp : duplicateList) {
			delConnection(cp);
			undo.recordDelete(cp);
		}
		undo.endUndoRecord();
		duplicateList.clear();
	}
	
	
//
//	/**
//	 * Deletes selected parts from model, connections and display 
//	 */
//	public int deleteSelected() {
//		
//		display.disableAutoRedraw();
//		releaseMovingPart();
//		if (getSelected().size() > 0) {
//			undo.startUndoRecord();
//			for (int id : getSelected()) {
//				undo.recordDelete(delPart(getPart(id)));
//			}
//			undo.endUndoRecord();
//		}
//		display.enableAutoRedraw();
//		return clearSelected();
//	}
//
	
	
//	public int changeColorSelected(int colorid) {
//
//		display.disableAutoRedraw();
//		if (getSelected().size() > 0) {
//			undo.startUndoRecord();
//			for (int id : getSelected()) {
//				LDPrimitive pp = getPart(id).setColorIndex(colorid);
//				undo.recordDelete(addPart(pp));
//				undo.recordAdd(pp);
//			}
//			undo.endUndoRecord();
//			clearSelected();
//		}
//		display.enableAutoRedraw();
//		return clearSelected();		
//	}




//	public void startMoveSelected() {
//		releaseMovingPart();
//		if (getSelected().size() > 0) {
//			inMovingPart = true;
//			// generate a name that do not collides with other parts
//			movedPartName = "__move__" + System.currentTimeMillis();
//			movedPart = LDrawPart.newCustomPart(movedPartName);
//			savedPart = new ArrayList<LDPrimitive>();
//			List<LDPrimitive> tempPart = new ArrayList<LDPrimitive>();
//			for (int i: getSelected()) {
//				// copy moving part to "moving block"
//				tempPart.add(getPart(i).getClone());
//				// save original part for recover
//				savedPart.add(getPart(i));
//				// remove part from model
//				//delPart(getPart(i));
//				display.delRenderedPart(i);
//			}
//			// computes moved part "center of gravity"
//			float x=0, y=0, z=0;
//			for (LDPrimitive p: tempPart) {
//				x += p.getTransformation().getX();
//				y += p.getTransformation().getY();
//				z += p.getTransformation().getZ();
//			}
//			// median point
//			x /= tempPart.size();
//			y /= tempPart.size();
//			z /= tempPart.size();
//			Matrix3D origin = new Matrix3D().moveTo(-x,-y,-z);
//			for (LDPrimitive p: tempPart) {
//				p = p.transform(origin);
//				movedPart.addPart(p);
//			}
//			newCurrentPart(movedPartName, LDrawColor.CURRENT);
//			display.disableHover();
//			moveCurrentPart(prevCursor);
//			movingPart = true;			
//		}
//	}
	
	
	
	public int explodeSelected() {
		
		if (getSelected().size() == 0)
			return 0;
		int expanded = 0;
		display.disableAutoRedraw();
		for (int id : getSelected()) {
			//LDRenderedPart rp = LDRenderedPart.getByGlobalId(id);
			LDPrimitive pp = getPart(id);
			if (pp.getType() != LDrawCommand.REFERENCE 
					) {
//			if (LDrawPart.getPartFromPrimitive(pp).getPartType() == LDrawPartType.COMMAND
//					|| LDrawPart.getPartFromPrimitive(pp).getPartType() == LDrawPartType.GEOM_PRIMITIVE
//					|| LDrawPart.getPartFromPrimitive(pp).getPartType() == LDrawPartType.PRIMITIVE48
//					|| LDrawPart.getPartFromPrimitive(pp).getPartType() == LDrawPartType.PRIMITIVE8
//					|| LDrawPart.getPartFromPrimitive(pp).getPartType() == LDrawPartType.UNOFF8
//					|| LDrawPart.getPartFromPrimitive(pp).getPartType() == LDrawPartType.UNOFF48
//					|| LDrawPart.getPartFromPrimitive(pp).getPartType() == LDrawPartType.PRIMITIVE
//					|| LDrawPart.getPartFromPrimitive(pp).getPartType() == LDrawPartType.UNOFF_PRIM
//					) {
				continue;
			}
			delPart(pp);
			expandSubModel(pp);
			expanded++;
		}
		clearSelected();
		//resetPartInfo();
		display.update();
		return expanded;
	}
	
	

	/** 
	 * expand submodel identified by rendered part rp
	 * does not deletes parts, connections and rendered part from display
	 * @param rp
	 */
	private void expandSubModel(LDPrimitive rp) {
		LDrawPart m = LDrawPart.getPart(rp.getLdrawId());
		Matrix3D t = rp.getTransformation();
		int color = rp.getColorIndex();
		for (LDPrimitive p: m.getPrimitives()) {
			if (p.getType() != LDrawCommand.REFERENCE) {
				// FIXME: if primitive is a triangle/quad, transformation must be applied to individual 3D point.
				// ignore non-reference elements
				addPart(p);
				continue;
			}
			if (p.getColorIndex() == 16) {
				color = rp.getColorIndex();
			}
			else {
				color = p.getColorIndex();
			}
			LDPrimitive np = LDPrimitive.newGlobalPart(p.getLdrawId(), color, p.getTransformation().transform(t));
			addPart(np);
		}
	}
	
	
//	public void saveSelectedAsBlock() {
//		if (getSelected().size() > 0) {
//			// generate a name that do not collides with other parts
//			String blockPartName = "blk-" + System.currentTimeMillis() + ".ldr";
//			if (LDrawPart.existsCustomPart(blockPartName)) {
//				blockPartName = "blk-" + (System.currentTimeMillis()+12) + ".ldr";			
//			}
//			LDrawPart blockPart = LDrawPart.newCustomPart(blockPartName);
//			blockPart.setDescription("Saved block");
//			List<LDPrimitive> tempPart = new ArrayList<LDPrimitive>();
//			for (int i: getSelected()) {
//				// copy moving part to "moving block"
//				tempPart.add(getPart(i).getCopy());
//			}
//			// computes moved part "center of gravity"
//			float x=0, y=0, z=0;
//			for (LDPrimitive p: tempPart) {
//				x += p.getTransformation().getX();
//				y += p.getTransformation().getY();
//				z += p.getTransformation().getZ();
//			}
//			// median point
//			x /= tempPart.size();
//			y /= tempPart.size();
//			z /= tempPart.size();
//			for (LDPrimitive p: tempPart) {
//				p.moveTo(-x,-y,-z);
//				blockPart.addPart(p);
//			}
//		}
//	}
//	
//	


	
	
	
	private void releaseMovingPart() {
		
		display.disableAutoRedraw();
		movingConnHead = false;
		movingConnBase = false;
		deleteConn = false;
		if (currentConnRendered != null) {
			display.removeGadget(currentConnRendered.getId());
			currentConnRendered = null;
		}
		currentConnection = null;
		dh.resetPointer();
		display.enableAutoRedraw();
		dh.movePointer(prevCursor);
	}
	
	
	

	
	
	
	/////////////////////
	//
	//  Current part handling
	//
	/////////////////////

	
//	private LDPrimitive newCurrentPart(String ldrid, int colorIndex) {
//		
//		currentPart = LDPrimitive.newGlobalPart(ldrid,colorIndex,dh.getCurrentMatrix());
//		currPartRendered = LDRenderedPart.newRenderedPart(currentPart);
//		return currentPart;
//	}
//
//	
//	
//	private void moveCurrentPart(Point3D p) {
//		
//		display.addRenderedPart(currPartRendered.fastMove(p));
//	}
//	
//	
//	
//	private void transformCurrentPart(Matrix3D m) {
//		
//		currentPart = currentPart.setTransform(m);
//		currPartRendered = LDRenderedPart.newRenderedPart(currentPart);
//	}
//	
//	
//	
//	public void colorCurrentPart(int c) {
//		
//		if (movingPart) {
//			currentPart = currentPart.setColorIndex(c);
//			currPartRendered = LDRenderedPart.newRenderedPart(currentPart);
//			moveCurrentPart(prevCursor);
//		}
//	}
//	
//	
//	
//	private void snapCurrentPart(boolean autoconnect, Matrix3D grid, Point3D cursor, Point3D eye) {
//		
//		if (autoconnect) {
//			ConnectionPoint p = connHandler.getTarget();
//			if (p != null) {
//				display.unselectPart(p.getPartId());
//			}
//			//System.out.println(currentPartRendered.getConnections());
//			if (connHandler.getConnectionPoint(currentPart,grid,cursor, eye)) {
//				// needs alignment
//				currentPart = currentPart.setTransform(connHandler.getAlignMatrix());
//				display.addRenderedPart(LDRenderedPart.newRenderedPart(currentPart)
//						.fastMove(connHandler.getLastConn()));
//			}
//			else {
//				// no alignment, only translation of rendered part
//				display.addRenderedPart(currPartRendered.fastMove(connHandler.getLastConn()));
//			}
//			if (connHandler.isLocked()) {
//				display.selectPart(connHandler.getTarget().getPartId());
//			}
//			
//		}
//		else {
//			display.addRenderedPart(currPartRendered.fastMove(cursor));
//		}
//
//	}
//	
//	
//	
//	private void updateMovingPart() {
//		if (movingPart) {
//			transformCurrentPart(dh.getCurrentMatrix());
//			display.disableAutoRedraw();
//			moveCurrentPart(prevCursor);
//			display.enableAutoRedraw();
//			dh.movePointer(prevCursor);
//		}
//	}
	
//	
//
//	private void releaseCurrentPart() {
//		
//		display.delRenderedPart(currentPart.getId());
//		currentPart = null;
//		if (connHandler.getTarget() != null) {
//			display.unselectPart(connHandler.getTarget().getPartId());
//		}
//
//	}
	
	
	
	/////////////////////
	//
	//  Selection and hiding
	//
	/////////////////////

	
	private void select(int partId) {
		
		selectedParts.add(partId);
		display.getPart(partId).select();
	}
	
	
	private void unSelect(int partId) {
		
		selectedParts.remove(partId);
		display.getPart(partId).unSelect();
	}
	
	

	private Set<Integer> getSelected() {
		return selectedParts;
	}
	
	
	
	public int clearSelected() {
		
		int s = selectedParts.size();
		if (s > 0) {
			for (int id : selectedParts) {
				display.getPart(id).unSelect();
			}
			selectedParts.clear();
			display.update();
		}
		return s;
	}
	
	

	public void hide(int partId) {
		
		hiddenParts.add(partId);
		display.getPart(partId).hide();
	}
	
	
	
	public void hideSelected() {
		
		if (selectedParts.size() > 0) {
			for (int id : selectedParts) {
				display.getPart(id).hide();
				hiddenParts.add(id);
			}
			clearSelected();
			display.update();
		}
	}
	
	
	
	public void show(int partId) {
		
		hiddenParts.remove(partId);
		display.getPart(partId).show();
	}
	

	
	/**
	 * returns always false in connection editor
	 * no hidden parts for connection editing
	 */
	@Override
	public boolean isHidden(int id) {
		return false;
	}

	

	public List<Integer> getHidden() {
		return hiddenParts;
	}
	
	
	
	public void showAll() {
		
		if (hiddenParts.size() > 0) {
			for (int id : hiddenParts) {
				display.getPart(id).show();
			}
			hiddenParts.clear();
			display.update();
		}
	}
	
	

	/**
	 * Select and highlight parts with same LDraw ID of current selected part 
	 * @return number of parts selected
	 */
	public int selectByPartId() {
		
		releaseMovingPart();
		if (getSelected().size() == 1) {
			int id = getSelected().iterator().next();
			LDPrimitive p = getPart(id);
			clearSelected();
			if (p != null) {
				for (LDPrimitive pt: mainModel.getPrimitives()) {
					if (pt.getLdrawId().equals(p.getLdrawId())) {
						select(pt.getId());
					}
				}
			}
		}
		return selectedParts.size();
	}


	/**
	 * Select and highlightr parts with color index given by current selected part
	 * @return number of parts selected
	 */
	public int selectByColor() {
		
		releaseMovingPart();
		if (getSelected().size() == 1) {
			int id = getSelected().iterator().next();
			LDPrimitive p = getPart(id);
			clearSelected();
			if (p != null) {
				for (LDPrimitive pt: mainModel.getPrimitives()) {
					if (pt.getColorIndex() == p.getColorIndex()) {
						select(pt.getId());
					}
				}
			}
		}
		return selectedParts.size();
	}

	
	
	/////////////////////
	//
	//  Undo interface
	//
	/////////////////////

	
	
	public boolean isModified() {
		return undo.isModified();
	}



	public void markSave() {
		undo.markSave();
	}



	public boolean isUndoAvailable() {
		return undo.isUndoAvailable();
	}



	public boolean isRedoAvailable() {
		return undo.isRedoAvailable();
	}



	
	public void undoLastEdit() {
		
		List<UndoableAction<ConnectionPoint>> actionList = undo.peekNextUndoActions();
		display.disableAutoRedraw();
		// do first deletion, next adds to avoid problem with modify
		for (UndoableAction<ConnectionPoint> a : actionList) {
			if (a.getOp() == UndoableOperation.DEL) {
				addConnection(a.getObject());
			}
		}
		for (UndoableAction<ConnectionPoint> a : actionList) {
			if (a.getOp() == UndoableOperation.ADD) {
				delConnection(a.getObject());
			}
		}
		undo.undo();
		display.enableAutoRedraw();
		display.update();
	}
	

	
	public void redoLastEdit() {
		
		List<UndoableAction<ConnectionPoint>> actionList = undo.peekNextRedoActions();
		display.disableAutoRedraw();
		for (UndoableAction<ConnectionPoint> a : actionList) {
			if (a.getOp() == UndoableOperation.DEL) {
				delConnection(a.getObject());
			}
		}
		for (UndoableAction<ConnectionPoint> a : actionList) {
			if (a.getOp() == UndoableOperation.ADD) {
				addConnection(a.getObject());
			}
		}
		undo.redo();
		display.enableAutoRedraw();
		display.update();
	}
	

	
	

	/////////////////////
	//
	//  Rendering
	//
	/////////////////////

	
	
	private void displayConnections() {
		
		display.clearGadgets();
		for (ConnectionPoint cp: connHandler.getConnectionList()) {
			Gadget3D cm = DrawHelpers.getConnectionPoint(cp);
			display.addGadget(cm);
		}
	}
	
	
	
	/**
	 * WARNING! May be a LOOOOONG task
	 * @throws IOException 
	 */
	private void render() {
		
		display.disableAutoRedraw();
		display.clearAllParts();
		int counter = 0;
		// for every part in model
		for (LDPrimitive p : mainModel.getPrimitives()) {
			if (p.getType() != LDrawCommand.AUXLINE
					&& p.getType() != LDrawCommand.LINE
					&& p.getType() != LDrawCommand.REFERENCE
					&& p.getType() != LDrawCommand.TRIANGLE
					&& p.getType() != LDrawCommand.QUAD
					) {
				continue;
			}
			// render part
			display.addRenderedPart(LDRenderedPart.newRenderedPart(p));
			// every 100 parts updates display
			if (counter++ % 100 == 0) {
				display.update();
			}
		}
		display.enableAutoRedraw();
		display.update();
	}
	
	
	
	
	@Override
	public void run() {
		
		if (updater != null) updater.updateStart();
		completed = false;
		render();
		completed = true;
		if (updater != null) updater.updateComplete();
	}
	
	
	
	public Thread getRenderTask(ProgressUpdater p) {
		
		updater = p;
		Thread t = new Thread(this,"RenderTask");
		t.setUncaughtExceptionHandler(this);
		return t;
	}



	public boolean isCompleted() {
		return completed;
	}



	@Override
	public void uncaughtException(Thread t, Throwable e) {
		
		updater.updateIncomplete();
		e.printStackTrace();
	}


	

	/////////////////////
	//
	//  Selection and moving callbacks from display 
	//
	/////////////////////


	
	@Override
	public void picked(int partId, Point3D snapFar, Point3D snapNear,
			PickMode mode) {

		boolean needRefresh = false;
		
		if (movingConnBase && mode == PickMode.NONE) {
			// we are placing a new connection
			currentConnection.setP1(prevCursor);
			currentConnection.setP2(prevCursor);
			//currentConnection.setDelta(p);
			movingConnBase = false;
			movingConnHead = true;
			dh.setPointerColor(Color.CYAN);
			dh.movePointer(prevCursor);
			currentConnRendered = DrawHelpers.getConnectionPoint(currentConnection);
			display.addGadget(currentConnRendered);
		}
		else if (movingConnHead && mode == PickMode.NONE) {
			// we are placing a connection head
			currentConnection.setP2(prevCursor);
			movingConnBase = true;
			movingConnHead = false;
			//dh.resetPointer();
			dh.movePointer(prevCursor);
			display.removeGadget(currentConnRendered.getId());
			addConnection(currentConnection);
			undo.startUndoRecord();
			undo.recordAdd(currentConnection);
			undo.endUndoRecord();
			dh.setPointerColor(Color.MAGENTA);
			currentConnection = ConnectionPoint.createCPoint(currentConnection.getType().getId());
			currentConnRendered = null;
		}
		else if (deleteConn && mode == PickMode.NONE) {
			if (selectedConnection != null) {
				delConnection(selectedConnection);
				undo.startUndoRecord();
				undo.recordDelete(selectedConnection);
				undo.endUndoRecord();
				selectedConnection = null;
				deleteConn = true;
				//dh.resetPointer();
			}
		}
		else {
			if (mode == PickMode.NONE) {
				if (getSelected().size() > 0) {
					clearSelected();
					needRefresh = true;
				}
			}
			if (partId != 0) {
				if (getPart(partId) == null) {
					return;
				}
				switch (mode) {
				case ADD:
					if (!getSelected().contains(partId)) {
						select(partId);
						needRefresh = true;
					}
					break;
				case TOGGLE:
					if (!getSelected().contains(partId)) {
						select(partId);
					}
					else {
						unSelect(partId);
					}
					needRefresh = true;
					break;
				case NONE:
					select(partId);
					needRefresh = true;
					break;
				case CENTER_TO:
					LDPrimitive pp = getPart(partId);
					if (pp.getType() == LDrawCommand.REFERENCE) {
						float[] p = pp.getTransformation().transformPoint(0, 0, 0);
						display.setOrigin(p[0],p[1],p[2]);
					}
					else if (pp.getType() == LDrawCommand.LINE ||
							pp.getType() == LDrawCommand.AUXLINE) {
						float[] p = pp.getPointsFV();
						float x = (p[0]+p[3]) / 2; 
						float y = (p[1]+p[4]) / 2; 
						float z = (p[2]+p[5]) / 2;
						display.setOrigin(x,y,z);
					}
					else if (pp.getType() == LDrawCommand.TRIANGLE) {
						float[] p = pp.getPointsFV();
						float x = (p[0]+p[3]+p[6]) / 3; 
						float y = (p[1]+p[4]+p[7]) / 3; 
						float z = (p[2]+p[5]+p[8]) / 3;
						display.setOrigin(x,y,z);
					}
					else if (pp.getType() == LDrawCommand.QUAD) {
						float[] p = pp.getPointsFV();
						float x = (p[0]+p[3]+p[6]+p[9]) / 4; 
						float y = (p[1]+p[4]+p[7]+p[10]) / 4; 
						float z = (p[2]+p[5]+p[8]+p[11]) / 4;
						display.setOrigin(x,y,z);
					}
					break;
				default:
					break;
				}
				if (listener != null) {
					listener.selectedPartChanged(getPart(partId));
				}
			}
		}
		if (listener != null) {
			listener.modifiedNotification(undo.isModified());
			listener.undoAvailableNotification(undo.isUndoAvailable());
			listener.redoAvailableNotification(undo.isRedoAvailable());
		}
		if (needRefresh)
			display.update();
		
	}



	@Override
	public void moved(int partId, Point3D eyeNear, Point3D eyeFar) {
		
		// get intersection point between plane and ray casted from mouse
		float[] pos = dh.getTargetPoint(eyeNear,eyeFar);
		if (pos[0] < -0.99) // line is parallel, no intersection 
			return;
		Point3D cursor = null;
		if (snapping) {
			float snap = dh.getSnap();
			 cursor = new Point3D(
					Math.round(pos[1]/snap)*snap,
					Math.round(pos[2]/snap)*snap,
					Math.round(pos[3]/snap)*snap);
		}
		else {
			cursor = new Point3D(pos, 1);
		}
		if (! prevCursor.coincident(cursor)) {
			display.disableAutoRedraw();
			prevCursor = cursor;
			if (!movingConnHead && !movingConnBase) {
				if (selectedConnection != null) {
					// remove previous highlight
					display.unselectGadget(selectedConnection.getId());
				}
				selectedConnection = connHandler.getNearestConnection(cursor, eyeNear);
				if (selectedConnection != null) {
					//System.out.println("lock-"+selectedConnection); // DB
					display.selectGadget(selectedConnection.getId());
					if (listener != null) {
						listener.selectedConnChanged(selectedConnection);
					}
				}
			}
			else if (movingConnHead) {
				currentConnection.setP2(cursor);
				currentConnRendered = DrawHelpers.getConnectionPoint(currentConnection);
				display.addGadget(currentConnRendered);
			}
			display.enableAutoRedraw();
			dh.movePointer(cursor);
		}
	}



	@Override
	public void endDragSelectionWindow() {}
	


	
	
//	@Override
//	public void actionPerformed(ActionEvent e) {
//		
//		if (e.getSource() == angleEntry) {
//			if (inRotation) {
//				String n = angleEntry.getText();
//				try {
//					float a = Float.parseFloat(n);
//					inRotation = false;
//					startRotation = false;
//					dh.removeRotGadgets();
//					display.delRenderedPart(rotatePoint.getPartId());
//					LDPrimitive p = getPart(rotatePoint.getPartId());
//					undo.startUndoRecord();
//					p = p.transform(
//							new Matrix3D(-rotatePoint.getP1().x, -rotatePoint.getP1().y, -rotatePoint.getP1().z)
//							.transform(JSimpleGeom.axisRotMatrix(rotatePoint.getP1(), rotatePoint.getP2(), a))
//							.moveTo(rotatePoint.getP1()));
//					undo.recordAdd(p);
//					undo.recordDelete(addPart(p));
//					undo.endUndoRecord();
//					//gldisplay.addRenderedPart(LDRenderedPart.newRenderedPart(p));
//					toolPanel.remove(angleLabel);
//					toolPanel.remove(angleEntry);
//					for (Component c: previousTools)
//						toolPanel.add(c);
//					toolPanel.revalidate();
//					dh.resetPointer();
//				}
//				catch (NumberFormatException ex) {
//					angleEntry.setBorder(BorderFactory.createLineBorder(Color.RED));
//				}
//			}
//		}
//		
//	}
	
	
	
	
	@Override
	public void keyTyped(KeyEvent e) {

		//System.out.println(e.getKeyChar());
		if (e.getKeyChar() == 27) {
			// ESC -> release moving part
			releaseMovingPart();
		}
		else if (e.getKeyChar() == 0x7f) {
			// delete -> remove selected parts
//			deleteSelected();
		}
//		else if (e.getKeyChar() == 'm' || e.getKeyChar() == 'M') {
//			// move selected parts
//			startMoveSelected();
//		}
	}



	@Override
	public void keyPressed(KeyEvent e) { 
		
//		if (movingPart) {
//			if (e.getKeyCode() == KeyEvent.VK_KP_LEFT || e.getKeyCode() == KeyEvent.VK_LEFT) {
//				dh.rotPointerY(rotateStep);
//			}
//			else if (e.getKeyCode() == KeyEvent.VK_KP_RIGHT || e.getKeyCode() == KeyEvent.VK_RIGHT) {
//				dh.rotPointerY(-rotateStep);
//			}
//			else if (e.getKeyCode() == KeyEvent.VK_KP_UP || e.getKeyCode() == KeyEvent.VK_UP) {
//				dh.rotPointerX(rotateStep);
//			}
//			else if (e.getKeyCode() == KeyEvent.VK_KP_DOWN || e.getKeyCode() == KeyEvent.VK_DOWN) {
//				dh.rotPointerX(-rotateStep);
//			}
//			else return;
//			updateMovingPart();
//		}
	}


	@Override
	public void keyReleased(KeyEvent e) { /* do nothing */ }



	@Override
	public void startDragParts(int partId) {
		/* do nothing */
	}

	
	
	


}
