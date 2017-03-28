/*
	Copyright 2015-2017 Mario Pascucci <mpascucci@gmail.com>
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import bricksnspace.j3dgeom.Matrix3D;
import bricksnspace.j3dgeom.Point3D;
import bricksnspace.ldraw3d.DrawHelpers;
import bricksnspace.ldraw3d.LDRenderedPart;
import bricksnspace.ldraw3d.LDrawGLDisplay;
import bricksnspace.ldraw3d.PickMode;
import bricksnspace.ldrawlib.ConnectionHandler;
import bricksnspace.ldrawlib.ConnectionPoint;
import bricksnspace.ldrawlib.LDPrimitive;
import bricksnspace.ldrawlib.LDrawColor;
import bricksnspace.ldrawlib.LDrawCommand;
import bricksnspace.ldrawlib.LDrawPart;
import bricksnspace.simpleundo.Undo;


/**
 * Add a part from library or "registered" custom part or blocks
 * 
 * @author Mario Pascucci
 *
 */
public class DragPartModePlugin implements LDEditorPlugin {
	
	
	LDEditor editor = null;
	DrawHelpers dh = null;
	ConnectionHandler connHandler = null;
	LDrawGLDisplay display = null;
	Undo<LDPrimitive> undo = null;
	LDPrimitive currentPart = null;
	List<LDPrimitive> tempPart;
	LDRenderedPart currPartRendered = null;
	boolean movingPart = false; 
	private static final String movingPartName = "__internal_dragging__";
	private int partId;

	
	
	public DragPartModePlugin(LDEditor me, DrawHelpers dhelp, ConnectionHandler ch, 
			Undo<LDPrimitive>u, LDrawGLDisplay gld) {
		if (me == null || dhelp == null || ch == null || gld == null || u == null)
			throw new IllegalArgumentException("[AddPartModePlugin] All parameters must be not null.");
		editor = me;
		dh = dhelp;
		connHandler = ch;
		display = gld;
		undo = u;
	}
	
	
	
	/* (non Javadoc)
	 * @see bricksnspace.ldraw3d.LDEditorPlugin#start(java.lang.Object)
	 */
	@Override
	public void start(Object... params) {
		
		// parameters check
		if (params.length != 1)
			throw new IllegalArgumentException("[DragPartModePlugin.start] Wrong parameter number, must be 1.");
		if (!(params[0] instanceof Integer))
			throw new IllegalArgumentException("[DragPartModePlugin.start] Param[0] must be Integer.");
		partId = (int) params[0];
		//System.out.println("drag start "+partId);
		if (partId == 0 && editor.getSelected().size() == 0) {
			movingPart = false;
			return;
		}
		// if user drag a part not in selection set 
		LDrawPart savedPart = LDrawPart.newInternalUsePart(movingPartName);
		tempPart = new ArrayList<LDPrimitive>();
		if (editor.getSelected().size() == 0 || !editor.getSelected().contains(partId)) {
			editor.unselectAll();
			tempPart.add(editor.getPart(partId).getClone());
		}
		for (int i: editor.getSelected()) {
			// copy part to temp block
			tempPart.add(editor.getPart(i).getClone());
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
		editor.unselectAll();
		// remove parts
		for (LDPrimitive p: tempPart) {
			editor.delPart(editor.getPart(p.getId()));
		}
		dh.resetPointerMatrix();
		currentPart = LDPrimitive.newGlobalPart(movingPartName,LDrawColor.CURRENT,dh.getCurrentMatrix());
		currPartRendered = LDRenderedPart.newRenderedPart(currentPart);
		display.disableHover();
		movingPart = true;
		display.addRenderedPart(currPartRendered.fastMove(editor.getCursor()));
	}
	
	
	
	/* (non Javadoc)
	 * @see bricksnspace.ldraw3d.LDEditorPlugin#reset()
	 */
	@Override
	public void reset() {
		
		if (currentPart != null) {
			display.delRenderedPart(currentPart.getId());
			for (LDPrimitive p: tempPart) {
				editor.addPart(p);
			}
		}
		if (connHandler.getTarget() != null) {
			display.getPart(connHandler.getTarget().getPartId()).unConnect();
		}
		currentPart = null;
		currPartRendered = null;
		display.enableHover();
		movingPart = false;
	}
	
	
	
	/* (non Javadoc)
	 * @see bricksnspace.ldraw3d.LDEditorPlugin#doClick(int, bricksnspace.j3dgeom.Point3D, bricksnspace.j3dgeom.Point3D, bricksnspace.ldraw3d.PickMode)
	 */
	@Override
	public boolean doClick(int partId, Point3D eyeNear, Point3D eyeFar, PickMode mode) {
		
		if (movingPart && mode == PickMode.NONE) {
//			float[] pos = dh.getTargetPoint(eyeNear,eyeFar);
//			if (pos[0] < -0.99) // line is parallel, no intersection 
//				return movingPart;
//			Point3D cursor = null;
//			if (LDEditor.isSnapping()) {
//				float snap = dh.getSnap();
//				 cursor = new Point3D(
//						Math.round(pos[1]/snap)*snap,
//						Math.round(pos[2]/snap)*snap,
//						Math.round(pos[3]/snap)*snap);
//			}
//			else {
//				cursor = new Point3D(pos, 1);
//			}
//			Point3D prevCursor = cursor;
			if (LDEditor.isAutoconnect()) {
				if (connHandler.isLocked()) {
					display.getPart(connHandler.getTarget().getPartId()).unConnect();
				}
				currentPart = currentPart.moveTo(connHandler.getLastConn());
			}
			else {
				currentPart = currentPart.moveTo(editor.getCursor()); //prevCursor);
			}
			undo.startUndoRecord();
			display.delRenderedPart(currentPart.getId());
			for (LDPrimitive p:tempPart) {
				if (p.getType() != LDrawCommand.REFERENCE) {
					// ignore non-reference elements
					continue;
				}
				undo.recordDelete(p);
			}
			LDrawPart m = LDrawPart.getPart(currentPart.getLdrawId());
			Matrix3D t = currentPart.getTransformation();
			for (LDPrimitive p: m.getPrimitives()) {
				if (p.getType() != LDrawCommand.REFERENCE) {
					// ignore non-reference elements
					continue;
				}
				LDPrimitive np = LDPrimitive.newGlobalPart(p.getLdrawId(), p.getColorIndex(), p.getTransformation().transform(t));
				editor.addPart(np);
				undo.recordAdd(np);
			}
			undo.endUndoRecord();
			currentPart = null;
			currPartRendered = null;
			movingPart = false;
		}
		return movingPart;
	}
	
	
	
	/* (non Javadoc)
	 * @see bricksnspace.ldraw3d.LDEditorPlugin#doMove(int, bricksnspace.j3dgeom.Point3D, bricksnspace.j3dgeom.Point3D, bricksnspace.j3dgeom.Point3D)
	 */
	@Override
	public void doMove(int partId, Point3D eyeNear, Point3D eyeFar) {
		
		if (LDEditor.isAutoconnect()) {
			ConnectionPoint p = connHandler.getTarget();
			if (p != null) {
				LDRenderedPart ldrp = display.getPart(p.getPartId());
				if (ldrp != null)
					ldrp.unConnect();
			}
			//System.out.println(currentPartRendered.getConnections());
			if (connHandler.getConnectionPoint(currentPart,dh.getCurrentMatrix(),editor.getCursor(), eyeNear)) {
				// needs alignment
				currentPart = currentPart.setTransform(connHandler.getAlignMatrix());
				currPartRendered = LDRenderedPart.newRenderedPart(currentPart);
			}
			if (connHandler.isLocked()) {
				display.getPart(connHandler.getTarget().getPartId()).connected();
			}

			display.addRenderedPart(currPartRendered.fastMove(connHandler.getLastConn()));
		}
		else {
			display.addRenderedPart(currPartRendered.fastMove(editor.getCursor()));
		}
			
	}
	
	
	
	/* (non Javadoc)
	 * @see bricksnspace.ldraw3d.LDEditorPlugin#doKeyPress(java.awt.event.KeyEvent)
	 */
	@Override
	public boolean doKeyPress(KeyEvent e) {

		if (movingPart) {
			if (e.getKeyCode() == KeyEvent.VK_KP_LEFT || e.getKeyCode() == KeyEvent.VK_LEFT) {
				dh.rotPointerY(LDEditor.getRotateStep());
			}
			else if (e.getKeyCode() == KeyEvent.VK_KP_RIGHT || e.getKeyCode() == KeyEvent.VK_RIGHT) {
				dh.rotPointerY(-LDEditor.getRotateStep());
			}
			else if (e.getKeyCode() == KeyEvent.VK_KP_UP || e.getKeyCode() == KeyEvent.VK_UP) {
				dh.rotPointerX(LDEditor.getRotateStep());
			}
			else if (e.getKeyCode() == KeyEvent.VK_KP_DOWN || e.getKeyCode() == KeyEvent.VK_DOWN) {
				dh.rotPointerX(-LDEditor.getRotateStep());
			}
			else return false;
			currentPart = currentPart.setTransform(dh.getCurrentMatrix());
			currPartRendered = LDRenderedPart.newRenderedPart(currentPart);
			display.addRenderedPart(currPartRendered.fastMove(
					LDEditor.isAutoconnect()?connHandler.getLastConn():editor.getCursor()));
			return true;
		}
		return false;
	}



	@Override
	public void doWindowSelected(Set<Integer> selected) { }



	@Override
	public void doColorchanged(int color) { 
		
		if (movingPart) {
			currentPart = currentPart.setColorIndex(color);
			currPartRendered = LDRenderedPart.newRenderedPart(currentPart);
			display.addRenderedPart(currPartRendered.fastMove(
					LDEditor.isAutoconnect()?connHandler.getLastConn():editor.getCursor()));
		}
	}
	
	
	
	@Override
	public boolean doMatrixChanged() {
		
		if (movingPart) {
			currentPart = currentPart.setTransform(dh.getCurrentMatrix());
			currPartRendered = LDRenderedPart.newRenderedPart(currentPart);
			display.addRenderedPart(currPartRendered.fastMove(
					LDEditor.isAutoconnect()?connHandler.getLastConn():editor.getCursor()));
		}
		return movingPart;
	}



	@Override
	public void doStepChanged(int step) {

		// do nothing
	}



	@Override
	public boolean needSelection() {
		
		return false;
	}



	@Override
	public void doDragParts(int partId) {
		
		// do nothing
	}

	

	
	
}
