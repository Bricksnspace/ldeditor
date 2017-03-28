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

import java.awt.event.KeyEvent;
import java.util.Set;

import bricksnspace.j3dgeom.Point3D;
import bricksnspace.ldraw3d.DrawHelpers;
import bricksnspace.ldraw3d.LDrawGLDisplay;
import bricksnspace.ldraw3d.PickMode;
import bricksnspace.ldraw3d.DrawHelpers.PointerMode;
import bricksnspace.ldrawlib.ConnectionHandler;
import bricksnspace.ldrawlib.LDPrimitive;
import bricksnspace.simpleundo.Undo;

/**
 * Delete parts plugin
 * 
 * @author Mario Pascucci
 *
 */
public class DeleteModePlugin implements LDEditorPlugin {

	
	
	
	private LDEditor editor;
	private DrawHelpers dh;
	private Undo<LDPrimitive> undo;
	private boolean inDelete = false;

	
	
	public DeleteModePlugin(LDEditor me, DrawHelpers dhelp, ConnectionHandler ch, 
			Undo<LDPrimitive>u, LDrawGLDisplay gld) {
		if (me == null || dhelp == null || ch == null || gld == null || u == null)
			throw new IllegalArgumentException("[DeleteModePlugin] All parameters must be not null.");
		editor = me;
		dh = dhelp;
		undo = u;
	}
	
	
	
	
	
	@Override
	public void start(Object... params) {
		
		// two modes: if parts are already selected, delete and enter delete mode
		if (editor.getSelected().size() > 0) {
			undo.startUndoRecord();
			for (int id : editor.getSelected()) {
				undo.recordDelete(editor.delPart(editor.getPart(id)));
			}
			undo.endUndoRecord();
			editor.clearSelected();
		}
		dh.setPointerMode(PointerMode.STUD,0x2ff8080);
		inDelete  = true;
	}

	
	
	@Override
	public void reset() {

		dh.resetPointer();
		inDelete = false;
	}

	
	
	@Override
	public boolean doClick(int partId, Point3D eyenear, Point3D eyeFar, PickMode mode) {
		
		if (partId != 0 && mode == PickMode.NONE) {
			undo.startUndoRecord();
			undo.recordDelete(editor.delPart(editor.getPart(partId)));
			undo.endUndoRecord();
		}
		return inDelete;
	}

	@Override
	public void doMove(int partId, Point3D eyeNear, Point3D eyeFar) {
		// do nothing
	}

	@Override
	public boolean doKeyPress(KeyEvent e) {
		// do nothing
		return false;
	}





	@Override
	public void doWindowSelected(Set<Integer> selected) {
		
		if (selected.size() > 0) {
			undo.startUndoRecord();
			for (int id : selected) {
				undo.recordDelete(editor.delPart(editor.getPart(id)));
			}
			undo.endUndoRecord();
			editor.clearSelected();
		}
	}





	@Override
	public void doColorchanged(int colorIndex) { /* do nothing */ }





	@Override
	public boolean doMatrixChanged() {
		
		// do nothing
		return false;
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
