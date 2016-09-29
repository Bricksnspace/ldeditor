/*
	Copyright 2015 Mario Pascucci <mpascucci@gmail.com>
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
import java.util.Set;

import bricksnspace.j3dgeom.Point3D;
import bricksnspace.ldraw3d.ConnectionHandler;
import bricksnspace.ldraw3d.DrawHelpers;
import bricksnspace.ldraw3d.LDRenderedPart;
import bricksnspace.ldraw3d.LDrawGLDisplay;
import bricksnspace.ldraw3d.PickMode;
import bricksnspace.ldraw3d.DrawHelpers.PointerMode;
import bricksnspace.ldrawlib.ConnectionPoint;
import bricksnspace.ldrawlib.LDPrimitive;
import bricksnspace.simpleundo.Undo;


/**
 * Add a part from library or "registered" custom part or blocks
 * 
 * @author Mario Pascucci
 *
 */
public class DuplicatePartModePlugin implements LDEditorPlugin {
	
	
	LDEditor editor = null;
	DrawHelpers dh = null;
	ConnectionHandler connHandler = null;
	LDrawGLDisplay display = null;
	Undo<LDPrimitive> undo = null;
	LDPrimitive currentPart = null;
	LDRenderedPart currPartRendered = null;
	boolean inSelect = false;
	boolean movingPart = false; 

	
	
	public DuplicatePartModePlugin(LDEditor me, DrawHelpers dhelp, ConnectionHandler ch, 
			Undo<LDPrimitive>u, LDrawGLDisplay gld) {
		if (me == null || dhelp == null || ch == null || gld == null || u == null)
			throw new IllegalArgumentException("[DuplicatePartModePlugin] All parameters must be not null.");
		editor = me;
		dh = dhelp;
		connHandler = ch;
		display = gld;
		undo = u;
	}
	
	
	
	@Override
	public void start(Object... params) {
		
		// parameters check
		if (params.length != 0)
			throw new IllegalArgumentException("[DuplicatePartModePlugin.start] No parameter needed.");
		dh.setPointerMode(PointerMode.STUD,Color.RED);
		dh.resetPointerMatrix();
		inSelect = true;
	}
	
	
	
	@Override
	public void reset() {
		
		if (movingPart) {
			display.delRenderedPart(currentPart.getId());
			if (connHandler.getTarget() != null) {
				display.getPart(connHandler.getTarget().getPartId()).unConnect();
			}
		}
		currentPart = null;
		currPartRendered = null;
		movingPart = false;
		inSelect = false;
		dh.resetPointer();
		display.enableHover();
	}
	
	
	
	@Override
	public boolean doClick(int partId, Point3D snapFar, Point3D snapNear,
			PickMode mode) {
		
		if (inSelect && mode == PickMode.NONE && partId != 0) {
			LDPrimitive p = editor.getPart(partId);
			dh.setPointerMatrix(p.getTransformation());
			currentPart = LDPrimitive.newGlobalPart(p.getLdrawId(),p.getColorIndex(),
					dh.getCurrentMatrix());
			currPartRendered = LDRenderedPart.newRenderedPart(currentPart);
			display.disableHover();
			inSelect = false;
			movingPart = true;
			display.addRenderedPart(currPartRendered.fastMove(editor.getCursor()));
			dh.resetPointer();
		}
		else if (movingPart && mode == PickMode.NONE) {
			float[] pos = dh.getTargetPoint(snapFar,snapNear);
			if (pos[0] < -0.99) // line is parallel, no intersection 
				return movingPart;
			Point3D cursor = null;
			if (LDEditor.isSnapping()) {
				float snap = dh.getSnap();
				 cursor = new Point3D(
						Math.round(pos[1]/snap)*snap,
						Math.round(pos[2]/snap)*snap,
						Math.round(pos[3]/snap)*snap);
			}
			else {
				cursor = new Point3D(pos, 1);
			}
			Point3D prevCursor = cursor;
			if (LDEditor.isAutoconnect()) {
				if (connHandler.isLocked()) {
					display.getPart(connHandler.getTarget().getPartId()).unConnect();;
				}
				currentPart = currentPart.moveTo(connHandler.getLastConn());
			}
			else {
				currentPart = currentPart.moveTo(prevCursor);
			}
			undo.startUndoRecord();
			editor.addPart(currentPart);
			undo.recordAdd(currentPart);
			undo.endUndoRecord();
			display.enableHover();
			if (LDEditor.isRepeatBrick()) {
				movingPart = false;
				start();
			}
			else {
				movingPart = false;
			}
		}
		return movingPart || inSelect;
	}
	
	
	
	@Override
	public void doMove(int partId, Point3D eyeNear, Point3D eyeFar) {
		
		if (inSelect) {
			return;
		}
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
	
	
	
	@Override
	public boolean doKeyPress(KeyEvent e) {

		if (movingPart) {
//			if (e.getKeyCode() == KeyEvent.VK_KP_LEFT || e.getKeyCode() == KeyEvent.VK_LEFT) {
//				currentPart = currentPart.setTransform(
//						currentPart.getTransformation().rotateY(LDEditor.getRotateStep()));
//				//dh.rotPointerY(LDEditor.getRotateStep());
//			}
//			else if (e.getKeyCode() == KeyEvent.VK_KP_RIGHT || e.getKeyCode() == KeyEvent.VK_RIGHT) {
//				currentPart = currentPart.setTransform(
//						currentPart.getTransformation().rotateY(-LDEditor.getRotateStep()));
//				//dh.rotPointerY(-LDEditor.getRotateStep());
//			}
//			else if (e.getKeyCode() == KeyEvent.VK_KP_UP || e.getKeyCode() == KeyEvent.VK_UP) {
//				currentPart = currentPart.setTransform(
//						currentPart.getTransformation().rotateX(LDEditor.getRotateStep()));
//				//dh.rotPointerX(LDEditor.getRotateStep());
//			}
//			else if (e.getKeyCode() == KeyEvent.VK_KP_DOWN || e.getKeyCode() == KeyEvent.VK_DOWN) {
//				currentPart = currentPart.setTransform(
//						currentPart.getTransformation().rotateX(-LDEditor.getRotateStep()));
//				//dh.rotPointerX(-LDEditor.getRotateStep());
//			}
//			else return false;
//			//currentPart = currentPart.setTransform(dh.getCurrentMatrix());
//			currPartRendered = LDRenderedPart.newRenderedPart(currentPart);
//			display.addRenderedPart(currPartRendered.fastMove(
//					LDEditor.isAutoconnect()?connHandler.getLastConn():editor.getCursor()));
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
	public void doColorchanged(int colorIndex) { }



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
				
	}



	@Override
	public boolean needSelection() {

		return false;
	}

	
	
	
}
