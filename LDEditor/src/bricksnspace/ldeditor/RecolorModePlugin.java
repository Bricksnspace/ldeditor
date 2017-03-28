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
import bricksnspace.ldrawlib.LDrawColor;
import bricksnspace.simpleundo.Undo;

/**
 * Recolor parts plugin
 * 
 * @author Mario Pascucci
 *
 */
public class RecolorModePlugin implements LDEditorPlugin {

	
	
	
	private LDEditor editor;
	private DrawHelpers dh;
	private Undo<LDPrimitive> undo;
	private boolean inRecolor = false;
	private int newColorId = LDrawColor.INVALID_COLOR;

	
	
	public RecolorModePlugin(LDEditor me, DrawHelpers dhelp, ConnectionHandler ch, 
			Undo<LDPrimitive>u, LDrawGLDisplay gld) {
		if (me == null || dhelp == null || ch == null || gld == null || u == null)
			throw new IllegalArgumentException("[RecolorModePlugin] All parameters must be not null.");
		editor = me;
		dh = dhelp;
		undo = u;
	}
	
	
	
	
	
	@Override
	public void start(Object... params) {
		
		// parameters check
		if (params.length != 1)
			throw new IllegalArgumentException("[RecolorModePlugin.start] Wrong parameter number, must be 1.");
		if (!(params[0] instanceof Integer))
			throw new IllegalArgumentException("[RecolorModePlugin.start] Param[0] must be Integer.");
		newColorId = (Integer) params[0];
		// two modes: if parts are already selected, recolor and enter recolor mode
		if (editor.getSelected().size() > 0) {
			undo.startUndoRecord();
			for (int id : editor.getSelected()) {
				LDPrimitive pp = editor.getPart(id).setColorIndex(newColorId);
				undo.recordDelete(editor.addPart(pp));
				undo.recordAdd(pp);
			}
			undo.endUndoRecord();
			editor.unselectAll();
		}
		dh.setPointerMode(PointerMode.CLIP,0x280ff80);
		inRecolor  = true;
	}

	
	
	@Override
	public void reset() {

		dh.resetPointer();
		newColorId = LDrawColor.INVALID_COLOR;
		inRecolor = false;
	}

	
	
	@Override
	public boolean doClick(int partId, Point3D eyenear, Point3D eyeFar, PickMode mode) {
		
		if (partId != 0 && mode == PickMode.NONE) {
			undo.startUndoRecord();
			LDPrimitive pp = editor.getPart(partId).setColorIndex(newColorId);
			undo.recordDelete(editor.addPart(pp));
			undo.recordAdd(pp);
			undo.endUndoRecord();
		}
		return inRecolor;
	}

	@Override
	public void doMove(int partId, Point3D eyeNear, Point3D eyeFar) {
		/* do nothing */
	}

	
	@Override
	public boolean doKeyPress(KeyEvent e) {
		return false;
	}





	@Override
	public void doWindowSelected(Set<Integer> selected) {
		
		if (selected.size() > 0) {
			undo.startUndoRecord();
			for (int id : selected) {
				LDPrimitive pp = editor.getPart(id).setColorIndex(newColorId);
				undo.recordDelete(editor.addPart(pp));
				undo.recordAdd(pp);
			}
			undo.endUndoRecord();
			editor.unselectAll();
		}
	}





	@Override
	public void doColorchanged(int colorIndex) {
		
		newColorId = colorIndex;
	}





	@Override
	public boolean doMatrixChanged() {
		
		return false;
	}





	@Override
	public void doStepChanged(int step) {
		/* do nothing */
	}





	@Override
	public boolean needSelection() {

		return false;
	}





	@Override
	public void doDragParts(int partId) {
		/* do nothing */
	}

}
