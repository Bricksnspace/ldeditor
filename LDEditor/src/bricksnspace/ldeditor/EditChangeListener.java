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

import bricksnspace.ldrawlib.ConnectionPoint;
import bricksnspace.ldrawlib.LDPrimitive;

/**
 * Callbacks to notify editor state changes
 * 
 * @author Mario Pascucci
 *
 */
public interface EditChangeListener {

	/**
	 * Notify a change in Undo function
	 * @param available true if available
	 */
	void undoAvailableNotification(boolean available);
	
	
	/**
	 * Notify a change in Redo function
	 * @param available true if available
	 */	
	void redoAvailableNotification(boolean available);
	
	/**
	 * Notify a change in document
	 * @param modified true if document was modified
	 */
	void modifiedNotification(boolean modified);
	
	
	/**
	 * Notify last part clicked by user
	 * @param p part clicked, null if all parts are deselected
	 */
	void selectedPartChanged(LDPrimitive p);
	
	
	/**
	 * Notify last connection selected by proximity
	 * @param p connection selected, null if none
	 */
	void selectedConnChanged(ConnectionPoint cp);
	
	
	/**
	 * Notify if cut or copy function is available
	 * @param available true if available
	 */
	void cutCopyAvailable(boolean available);
	
	
	
	/**
	 * Notify if paste function is available, i.e. clipboard contains anything
	 * @param available true if available
	 */
	void pasteAvailable(boolean available);
}
