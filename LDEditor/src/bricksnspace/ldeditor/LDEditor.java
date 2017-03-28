/*
	Copyright 2014-2015 Mario Pascucci <mpascucci@gmail.com>
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


import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import bricksnspace.j3dgeom.JSimpleGeom;
import bricksnspace.j3dgeom.Matrix3D;
import bricksnspace.j3dgeom.Point3D;
import bricksnspace.ldraw3d.DrawHelpers;
import bricksnspace.ldraw3d.HandlingListener;
import bricksnspace.ldraw3d.LDRenderedPart;
import bricksnspace.ldraw3d.LDrawGLDisplay;
import bricksnspace.ldraw3d.PickMode;
import bricksnspace.ldraw3d.ProgressUpdater;
import bricksnspace.ldrawlib.ConnectionHandler;
import bricksnspace.ldrawlib.LDPrimitive;
import bricksnspace.ldrawlib.LDrawColor;
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
public class LDEditor implements Runnable, UncaughtExceptionHandler, 
	HandlingListener, KeyListener, PartQueryable {
	
	// plugin handling
	public static final String ADDPLUGIN = "Add";
	public static final String DUPPLUGIN = "Dup";
	public static final String DELPLUGIN = "Del";
	public static final String RECOLORPLUGIN = "ReCol";
	public static final String ROTATEPLUGIN = "Rotate";
	public static final String FLEXPLUGIN = "Flex";
	public static final String STEPPLUGIN = "Step";
	public static final String DRAGPLUGIN = "Drag";
	
	// plugin list
	private Map<String,LDEditorPlugin> plugins = new HashMap<String, LDEditorPlugin>();
	
	// current plugin
	LDEditorPlugin currentPlugin = null;
	
	private LDrawPart mainModel;
	private ProgressUpdater updater;
	private boolean completed;
	private LDrawGLDisplay display;
	private ConnectionHandler connHandler;
	

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
	private static int currentColor = LDrawColor.RED;
	
	// display feedback
	private Point3D prevCursor = new Point3D(0,0,0);
	
	// cut/copy/paste handling
	private final static String savedPartName = "__internal_cutpaste__";
	
	// drawing helper
	private DrawHelpers dh;
	
	// undo subsystem
	private Undo<LDPrimitive> undo;
	
	// editor generated subparts/submodels
	private static Set<String> unsavedParts = new HashSet<String>();
	
	// selecting and hiding
	private Set<Integer> selectedParts = new HashSet<Integer>();
	private Set<Integer> hiddenParts = new HashSet<Integer>();


	
	
	
	
	private LDEditor(LDrawPart model, LDrawGLDisplay gldisplay) {
		
		mainModel = model;
		display = gldisplay;
		connHandler = new ConnectionHandler(this);
		connHandler.addAllConnections(mainModel);
		undo = new Undo<LDPrimitive>();
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
		gldisplay.enableHover();
		gldisplay.enableSelection(true);
		display.addPickListener(this);
		display.getCanvas().addKeyListener(this);
		// register known plugins
		plugins.put(ADDPLUGIN, new AddPartModePlugin(this, dh, connHandler, undo, display));
		plugins.put(DUPPLUGIN, new DuplicatePartModePlugin(this, dh, connHandler, undo, display));
		plugins.put(DELPLUGIN, new DeleteModePlugin(this, dh, connHandler, undo, display));
		plugins.put(RECOLORPLUGIN, new RecolorModePlugin(this, dh, connHandler, undo, display));
		plugins.put(ROTATEPLUGIN, new RotatePartModePlugin(this, dh, connHandler, undo, display));
		plugins.put(FLEXPLUGIN, new FlexPartPlugin(this, dh, connHandler, undo, display));
		plugins.put(STEPPLUGIN, new BuildStepPlugin(this, dh, connHandler, undo, display));
		plugins.put(DRAGPLUGIN, new DragPartModePlugin(this, dh, connHandler, undo, display));
	}
	
	
	
	public static LDEditor newLDModelEditor(LDrawPart m, LDrawGLDisplay gld) {
		
		LDEditor model = new LDEditor(m, gld);
		return model;
	}

	
	
	public void closeEditor() {
		
		resetCurrentAction();
		undo = null;
		mainModel = null;
		connHandler = null;
		dh = null;
		display.getCanvas().removeKeyListener(this);
		display.removePickListener(this);
		plugins.clear();
		plugins = null;
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
	public static boolean isAutoconnect() {
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
		LDEditor.repeatBrick = repeatBrick;
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
		LDEditor.rotateStep = rotateStep;
	}




	
	
	/////////////////////
	//
	//  Edit helpers
	//
	/////////////////////

	
	public void downY() {
		display.disableAutoRedraw();
		dh.downY();
		matrixChanged();
		display.enableAutoRedraw();
	}



	public void upY() {
		display.disableAutoRedraw();
		dh.upY();
		matrixChanged();
		display.enableAutoRedraw();
	}



	public void downX() {
		display.disableAutoRedraw();
		dh.downX();
		matrixChanged();
		display.enableAutoRedraw();
	}



	public void upX() {
		display.disableAutoRedraw();
		dh.upX();
		matrixChanged();
		display.enableAutoRedraw();
	}



	public void downZ() {
		display.disableAutoRedraw();
		dh.downZ();
		matrixChanged();
		display.enableAutoRedraw();
	}



	public void upZ() {
		display.disableAutoRedraw();
		dh.upZ();
		matrixChanged();
		display.enableAutoRedraw();
	}



	public void alignXY() {
		display.disableAutoRedraw();
		dh.alignXY();
		matrixChanged();
		display.enableAutoRedraw();
	}



	public void alignYZ() {
		display.disableAutoRedraw();
		dh.alignYZ();
		matrixChanged();
		display.enableAutoRedraw();
	}



	public void alignXZ() {
		display.disableAutoRedraw();
		dh.alignXZ();
		matrixChanged();
		display.enableAutoRedraw();
	}



	public void enableAxis(boolean enable) {
		
		axisEnabled = enable;
		dh.enableAxis(axisEnabled);
	}


	public void enableGrid(boolean enable) {
		gridEnabled = enable;
		dh.enableGrid(gridEnabled);
		matrixChanged();
		display.enableAutoRedraw();
	}



	public void resetGrid() {
		display.disableAutoRedraw();
		dh.resetGrid();
		matrixChanged();
		display.enableAutoRedraw();
	}



	public void resetPointerMatrix() {
		display.disableAutoRedraw();
		dh.resetPointerMatrix();
		matrixChanged();
		display.enableAutoRedraw();

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
		display.disableAutoRedraw();
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
		matrixChanged();
		display.enableAutoRedraw();
	}
	
	

	public void resetView() {
		display.resetView();
		display.rotateY(15);
		display.rotateX(-25);
	}




	/////////////////////
	//
	//  Model handling
	//
	/////////////////////



	/** register a clone and detach old model
	 * @param ldrid
	 * @see bricksnspace.ldrawlib.LDrawPart#registerCustomPart(java.lang.String)
	 */
	public void registerCustomPart(String ldrid) {
		
		LDrawPart p = mainModel.getCopy();
		if (p.getLdrawId() != null && p.getLdrawId() != "") {
			p.registerCustomPart(p.getLdrawId());
		}
		mainModel.registerCustomPart(ldrid);
	}



	public static Set<String> getUnsavedParts() {
		return unsavedParts;
	}




	/**
	 * @param f
	 * @throws IOException
	 * @see bricksnspace.ldrawlib.LDrawPart#saveAsLdr(java.io.File)
	 */
	public void saveAsLdr(File f) throws IOException {
		mainModel.saveAsLdr(f);
	}



	/**
	 * @param f
	 * @throws IOException
	 * @see bricksnspace.ldrawlib.LDrawPart#saveAsMpd(java.io.File)
	 */
	public void saveAsMpd(File f) throws IOException {
		mainModel.saveAsMpd(f);
	}



	/**
	 * @return
	 * @see bricksnspace.ldrawlib.LDrawPart#getPartName()
	 */
	public String getPartName() {
		return mainModel.getPartName();
	}



	/**
	 * @param partName
	 * @see bricksnspace.ldrawlib.LDrawPart#setPartName(java.lang.String)
	 */
	public void setPartName(String partName) {
		mainModel.setPartName(partName);
	}



	/**
	 * @return
	 * @see bricksnspace.ldrawlib.LDrawPart#getAuthor()
	 */
	public String getAuthor() {
		return mainModel.getAuthor();
	}



	/**
	 * @param author
	 * @see bricksnspace.ldrawlib.LDrawPart#setAuthor(java.lang.String)
	 */
	public void setAuthor(String author) {
		mainModel.setAuthor(author);
	}



	/**
	 * @return
	 * @see bricksnspace.ldrawlib.LDrawPart#getDescription()
	 */
	public String getDescription() {
		return mainModel.getDescription();
	}



	/**
	 * @return
	 * @see bricksnspace.ldrawlib.LDrawPart#getPartType()
	 */
	public LDrawPartType getPartType() {
		return mainModel.getPartType();
	}



	/**
	 * @param partType
	 * @see bricksnspace.ldrawlib.LDrawPart#setPartType(bricksnspace.ldrawlib.LDrawPartType)
	 */
	public void setPartType(LDrawPartType partType) {
		mainModel.setPartType(partType);
	}

		


	public void setLicense(String license) {
		mainModel.setLicense(license);
	}



	/**
	 * @param parseDescription
	 * @see bricksnspace.ldrawlib.LDrawPart#setDescription(java.lang.String)
	 */
	public void setDescription(String parseDescription) {
		mainModel.setDescription(parseDescription);
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
	public LDPrimitive getPart(int id) {
		return mainModel.getPartById(id);
	}



	public List<LDPrimitive> getPrimitives() {
		return mainModel.getPrimitives();
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
	
	
	


	/////////////////////
	//
	//  STEP functions
	//
	/////////////////////


	
	public int nextStep() {
		
		int s = mainModel.nextStep();
		if (getCurrentAction() != null) {
			getCurrentAction().doStepChanged(s);
		}
		return s;
	}
	
	
	public int prevStep() {
		int s = mainModel.prevStep();
		if (getCurrentAction() != null) {
			getCurrentAction().doStepChanged(s);
		}
		return s;		
	}
	
	
	public int goFirstStep() {
		
		int s = mainModel.goFirstStep(); 
		if (getCurrentAction() != null) {
			getCurrentAction().doStepChanged(s);
		}
		return s;		
	}
	
	
	public int goLastStep() {
		
		int s = mainModel.goLastStep(); 
		if (getCurrentAction() != null) {
			getCurrentAction().doStepChanged(s);
		}
		return s;		
	}
	
	
	public int getNumSteps() {
		return mainModel.getNumSteps();
	}



	public Collection<LDPrimitive> getPartsInStep(int n) {
		return mainModel.getPartsInStep(n);
	}
	

	public void moveToCurrStep(LDPrimitive p) {
		mainModel.moveToCurrStep(p);
	}


	public void moveToStep(LDPrimitive p, int s) {
		mainModel.moveToStep(p, s);
	}
	

	public int getCurrStep() {
		return mainModel.getCurrStep();
	}



	/////////////////////
	//
	//  Editing functions
	//
	/////////////////////

	
	
	
	public void startAction(String pluginName, Object...params) {
		
		if (plugins.containsKey(pluginName)) {
			if (getCurrentAction() != null) {
				getCurrentAction().reset();
			}
			setCurrentAction(plugins.get(pluginName));
			getCurrentAction().start(params);
		}
		else 
			throw new IllegalArgumentException("[LDEditor.startPluginAction] Unknown plugin: "+pluginName);
	}
	

	
	
	public synchronized void resetCurrentAction() {
		
		if (currentPlugin != null) {
			currentPlugin.reset();
			// needed to avoid an NPE when last conn point is on deleted part.
			connHandler.resetTarget();
			currentPlugin = null;
		}
		if (listener != null) {
			listener.modifiedNotification(undo.isModified());
			listener.undoAvailableNotification(undo.isUndoAvailable());
			listener.redoAvailableNotification(undo.isRedoAvailable());
		}
	}
	
	

	/**
	 * called by active plugin to notify end of work to editor
	 */
	protected synchronized void pluginEndAction() {
		if (currentPlugin != null) {
			currentPlugin = null;
		}
		if (listener != null) {
			listener.modifiedNotification(undo.isModified());
			listener.undoAvailableNotification(undo.isUndoAvailable());
			listener.redoAvailableNotification(undo.isRedoAvailable());
		}
	}
	
	
	
	private synchronized void setCurrentAction(LDEditorPlugin action) {
		
		currentPlugin = action;
	}
	
	
	
	
	private synchronized LDEditorPlugin getCurrentAction() {
		
		return currentPlugin;
	}
	
	
	
	
	public void currentColorChanged(int color) {
		
		currentColor = color;
		if (getCurrentAction() != null) {
			getCurrentAction().doColorchanged(currentColor);
		}
	}
	
	
	
	
	private void matrixChanged() {

		if (getCurrentAction() != null) {
			getCurrentAction().doMatrixChanged();
		}
		display.update();
	}
	
	
	
	protected void addUnsavedPart(String s) {
		
		if (s != null && s != "") {
			unsavedParts.add(s);
		}
	}
	
	
	
	public static void savedPart(String s) {
		
		if (s != null && s != "") {
			unsavedParts.remove(s);
		}
	}
	

	
	/**
	 * Start a cut or copy action
	 * @param cutMode if true do a "cut"
	 */
	public void doCutCopy(boolean cutMode) {
		
		if (getSelected().size() == 0)
			return;
		LDrawPart savedPart = LDrawPart.newInternalUsePart(savedPartName);
		List<LDPrimitive> tempPart = new ArrayList<LDPrimitive>();
		for (int i: getSelected()) {
			// copy part to temp block
			tempPart.add(getPart(i).getClone());
		}
		// computes moved part "center of gravity"
		float x=0, y=0, z=0;
		for (LDPrimitive p: tempPart) {
			x += p.getTransformation().getX();
			y += p.getTransformation().getY();
			z += p.getTransformation().getZ();
		}
		// median point
		x /= tempPart.size();
		y /= tempPart.size();
		z /= tempPart.size();
		// new origin
		Matrix3D origin = new Matrix3D().moveTo(-x,-y,-z);
		for (LDPrimitive p: tempPart) {
			p = p.transform(origin);
			savedPart.addPart(p);
		}
		unselectAll();
		// now if "cut" deleted parts and prepare undo
		if (cutMode) {
			undo.startUndoRecord();
			for (LDPrimitive p: tempPart) {
				undo.recordDelete(delPart(getPart(p.getId())));
			}
			undo.endUndoRecord();
		}
		listener.pasteAvailable(true);
	}
	
	
	
	public void doPaste() {

		if (LDrawPart.existsInternalUsePart(savedPartName)) {
			dh.resetPointerMatrix();
			startAction(ADDPLUGIN, savedPartName,currentColor, true);
		}
	}
	
	
	
	
	public int explodeSelected() {
		
		if (getSelected().size() == 0)
			return 0;
		int expanded = 0;
		display.disableAutoRedraw();
		undo.startUndoRecord();
		for (int id : getSelected()) {
			//LDRenderedPart rp = LDRenderedPart.getByGlobalId(id);
			LDPrimitive pp = getPart(id);
			// TODO: test avoiding "explosion" of parts
			if (pp.getType() != LDrawCommand.REFERENCE) {
				continue;
			}
			LDrawPartType pt = LDrawPart.getPart(pp.getLdrawId()).getPartType();
			if (pt == LDrawPartType.GEOM_PRIMITIVE
					|| pt == LDrawPartType.OFFICIAL
					|| pt == LDrawPartType.PRIMITIVE
					|| pt == LDrawPartType.PRIMITIVE48
					|| pt == LDrawPartType.PRIMITIVE8
					|| pt == LDrawPartType.SHORTCUT
					|| pt == LDrawPartType.UNOFF_PRIM
					|| pt == LDrawPartType.UNOFF8
					|| pt == LDrawPartType.UNOFF48
					|| pt == LDrawPartType.UNOFF_SHORTCUT
					|| pt == LDrawPartType.UNOFFICIAL
					) {
				continue;
			}			
			delPart(pp);
			undo.recordDelete(pp);
			expandSubModel(pp);
			expanded++;
		}
		unselectAll();
		undo.endUndoRecord();
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
				// ignore non-reference elements
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
			undo.recordAdd(np);
		}
	}
	
	
	public void saveSelectedAsBlock() {
		if (getSelected().size() > 0) {
			// generate a name that do not collides with other parts
			String blockPartName = "blk-" + System.currentTimeMillis() + ".ldr";
			if (LDrawPart.existsCustomPart(blockPartName)) {
				blockPartName = "blk-" + (System.currentTimeMillis()+12) + ".ldr";			
			}
			LDrawPart blockPart = LDrawPart.newCustomPart(blockPartName);
			blockPart.setDescription("Saved block");
			List<LDPrimitive> tempPart = new ArrayList<LDPrimitive>();
			for (int i: getSelected()) {
				// copy moving part to "moving block"
				tempPart.add(getPart(i).getCopy());
			}
			// computes moved part "center of gravity"
			float x=0, y=0, z=0;
			for (LDPrimitive p: tempPart) {
				x += p.getTransformation().getX();
				y += p.getTransformation().getY();
				z += p.getTransformation().getZ();
			}
			// median point
			x /= tempPart.size();
			y /= tempPart.size();
			z /= tempPart.size();
			for (LDPrimitive p: tempPart) {
				p.moveTo(-x,-y,-z);
				blockPart.addPart(p);
			}
		}
	}
	
	



	
	/////////////////////
	//
	//  Selection and hiding
	//
	/////////////////////

	
	private void select(int partId) {
		
		selectedParts.add(partId);
		display.getPart(partId).select();
		listener.cutCopyAvailable(true);
	}
	
	
	private void unSelect(int partId) {
		
		selectedParts.remove(partId);
		display.getPart(partId).unSelect();
		if (selectedParts.size() == 0) {
			listener.cutCopyAvailable(false);
		}
	}
	
	

	public Set<Integer> getSelected() {
		return selectedParts;
	}
	
	
	
	public int unselectAll() {
		
		int s = selectedParts.size();
		if (s > 0) {
			for (int id : selectedParts) {
				LDRenderedPart pt = display.getPart(id);
				if (pt != null)
					pt.unSelect();
			}
			selectedParts.clear();
			display.update();
		}
		listener.cutCopyAvailable(false);
		return s;
	}
	
	
	
	
	
	public void clearSelected() {
		
		listener.cutCopyAvailable(false);
		selectedParts.clear();
	}
	

	
	public void hideSelected() {
		
		if (selectedParts.size() > 0) {
			for (int id : selectedParts) {
				display.getPart(id).hide();
				hiddenParts.add(id);
			}
			unselectAll();
			display.update();
		}
	}
	
	
	
	/*
	 * (non-Javadoc)
	 * @see bricksnspace.ldrawlib.PartQueryable#isHidden(int)
	 */
	@Override
	public boolean isHidden(int id) {
		return hiddenParts.contains(id);
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
		
//		releaseMovingPart();
		if (getSelected().size() == 1) {
			int id = getSelected().iterator().next();
			LDPrimitive p = getPart(id);
			unselectAll();
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
		
//		releaseMovingPart();
		if (getSelected().size() == 1) {
			int id = getSelected().iterator().next();
			LDPrimitive p = getPart(id);
			unselectAll();
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
		
		if (!undo.isUndoAvailable()) 
			return;
		List<UndoableAction<LDPrimitive>> actionList = undo.peekNextUndoActions();
		display.disableAutoRedraw();
		// do first deletion, next adds to avoid problem with modify
		for (UndoableAction<LDPrimitive> a : actionList) {
			if (a.getOp() == UndoableOperation.ADD) {
				delPart(a.getObject());
			}
		}
		for (UndoableAction<LDPrimitive> a : actionList) {
			if (a.getOp() == UndoableOperation.DEL) {
				addPart(a.getObject());
			}
		}
		undo.undo();
		display.enableAutoRedraw();
		display.update();
	}
	

	
	public void redoLastEdit() {
		
		if (!undo.isRedoAvailable()) 
			return;
		List<UndoableAction<LDPrimitive>> actionList = undo.peekNextRedoActions();
		display.disableAutoRedraw();
		for (UndoableAction<LDPrimitive> a : actionList) {
			if (a.getOp() == UndoableOperation.DEL) {
				delPart(a.getObject());
			}
		}
		for (UndoableAction<LDPrimitive> a : actionList) {
			if (a.getOp() == UndoableOperation.ADD) {
				addPart(a.getObject());
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

	
	
	/**
	 * WARNING! May be a LOOOOONG task
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
		Logger.getGlobal().log(Level.WARNING, "[LDEditor] Unable to complete rendering for model "+mainModel.getLdrawId(), e);
	}


	

	/////////////////////
	//
	//  Selection and moving callbacks from display 
	//
	/////////////////////

	
	
	public Point3D getCursor() {
		
		return prevCursor;
	}

	
	@Override
	public void picked(int partId, Point3D eyeNear, Point3D eyeFar,
			PickMode mode) {

		//System.out.println(partId); // DBg
		display.disableAutoRedraw();
		if (getCurrentAction() != null && mode != PickMode.CENTER_TO) {
			boolean res = getCurrentAction().doClick(partId, eyeNear, eyeFar, mode);
			if (! res) {
				// plugin is done working
				resetCurrentAction();
			}
		}
		if (getCurrentAction() == null || getCurrentAction().needSelection() || mode == PickMode.CENTER_TO) {
			if (mode == PickMode.NONE) {
				if (getSelected().size() > 0) {
					unselectAll();
				}
			}
			if (partId != 0 && getPart(partId) != null) {
				switch (mode) {
				case ADD:
					if (!getSelected().contains(partId)) {
						select(partId);
					}
					break;
				case TOGGLE:
					if (!getSelected().contains(partId)) {
						select(partId);
					}
					else {
						unSelect(partId);
					}
					break;
				case NONE:
					select(partId);
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
		display.enableAutoRedraw();
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
			if (getCurrentAction() != null) {
				getCurrentAction().doMove(partId, eyeNear, eyeFar);
			}
			display.enableAutoRedraw();
			dh.movePointer(prevCursor);
		}
	}

	
	
	@Override
	public void endDragSelectionWindow() {
		
		getCurrentAction().doWindowSelected(selectedParts);
	}
	

	
	@Override
	public void startDragParts(int partId) {
		
		/*
		 * avoid multiple drag start
		 * mouse drag events are notified continuously during "drag" 
		 */
		if (getCurrentAction() == plugins.get(DRAGPLUGIN)) 
			return;
		startAction(DRAGPLUGIN, partId);
	}
	

	
	
	
	@Override
	public void keyTyped(KeyEvent e) {

		if (e.getKeyChar() == 27) {
			// ESC -> release moving part
			resetCurrentAction();
		}
		else if (e.getKeyChar() == 0x7f) {
			// delete -> remove selected parts
			startAction(DELPLUGIN);
			resetCurrentAction();
		}
	}



	@Override
	public void keyPressed(KeyEvent e) { 
		
		if (e.getKeyCode() == KeyEvent.VK_CUT
				|| (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_X)) {
			doCutCopy(true);
		}
		else if (e.getKeyCode() == KeyEvent.VK_COPY
				|| (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_C)) {
			doCutCopy(false);
		}
		else if (e.getKeyCode() == KeyEvent.VK_PASTE
				|| (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_V)) {
			doPaste();
		}
		else if (e.getKeyCode() == KeyEvent.VK_UNDO
				|| (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Z)) {
			undoLastEdit();
			listener.modifiedNotification(undo.isModified());
			listener.undoAvailableNotification(undo.isUndoAvailable());
			listener.redoAvailableNotification(undo.isRedoAvailable());
		}
		else if (e.getKeyCode() == KeyEvent.VK_AGAIN
				|| (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Y)) {
			redoLastEdit();
			listener.modifiedNotification(undo.isModified());
			listener.undoAvailableNotification(undo.isUndoAvailable());
			listener.redoAvailableNotification(undo.isRedoAvailable());
		}
		else {
			if (getCurrentAction() != null) {
				display.disableAutoRedraw();
				if (getCurrentAction().doKeyPress(e)) {
					display.update();
				}
				display.enableAutoRedraw();
			}
		}
	}

	

	@Override
	public void keyReleased(KeyEvent e) { }





}
