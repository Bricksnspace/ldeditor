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
import bricksnspace.ldraw3d.PickMode;

public interface LDEditorPlugin {

	/**
	 * Start plugin action
	 * <p>
	 * Parameters are passed "as is", it is up to plugin code to verify parameter consistency
	 * @param params plugin parameters
	 * @throws IllegalArgumentException if parameters are invalid
	 */
	public abstract void start(Object... params);

	
	
	/**
	 * Reset plugin to inactive status
	 * <p>
	 * Is up to plugin to delete gadgets and temporary parts in display 
	 */
	public abstract void reset();
	
	
	/**
	 * returns true if main editor must handle selection, false if selection is up to plugin
	 * @return true if need selection and picking handle
	 */
	public boolean needSelection();
	
	
	/**
	 * Called when user "click" on 3D editor window
	 * @param partId global id of clicked part
	 * @param eyeNear ray vector point near user eye
	 * @param eyeFar ray vector point near model
	 * @param mode pick mode
	 * @return false if operation is ended 
	 */
	public abstract boolean doClick(int partId, Point3D eyeNear, Point3D eyeFar, PickMode mode);

	
	
	/**
	 * Called when user "move" mouse pointer over GL window
	 * @param partId part under cursor
	 * @param eyeNear ray vector point near user eye
	 * @param eyeFar ray vector point near model
	 */
	public abstract void doMove(int partId, Point3D eyeNear, Point3D eyeFar);

	
	
	
	/**
	 * Called when user release mouse button 1 after dragging a selection window
	 * @param selected set of items IDs
	 */
	public abstract void doWindowSelected(Set<Integer> selected);
	
	
	
	
	/**
	 * Called when user press a key with focus on GL window
	 * @param e event for pressed key
	 * @return true if need display update
	 */
	public abstract boolean doKeyPress(KeyEvent e);
	
	
	
	
	/**
	 * used to notify when grid or cursor matrix changed
	 * @return true if display needs refresh
	 */
	public abstract boolean doMatrixChanged();
	
	
	
	
	/**
	 * called when user change current color
	 * @param colorIndex
	 */
	public abstract void doColorchanged(int colorIndex);
	

	
	/**
	 * called when user changes current step
	 * @param step new current step
	 */
	public abstract void doStepChanged(int step);
	
}