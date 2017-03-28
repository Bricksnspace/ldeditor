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
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import bricksnspace.j3dgeom.JSimpleGeom;
import bricksnspace.j3dgeom.Matrix3D;
import bricksnspace.j3dgeom.Point3D;
import bricksnspace.ldraw3d.DrawHelpers;
import bricksnspace.ldraw3d.LDRenderedPart;
import bricksnspace.ldraw3d.LDrawGLDisplay;
import bricksnspace.ldraw3d.PickMode;
import bricksnspace.ldraw3d.DrawHelpers.PointerMode;
import bricksnspace.ldrawlib.ConnectionHandler;
import bricksnspace.ldrawlib.ConnectionPoint;
import bricksnspace.ldrawlib.LDPrimitive;
import bricksnspace.simpleundo.Undo;


/**
 * Rotate part or blocks around a connection point
 * 
 * @author Mario Pascucci
 *
 */
public class RotatePartModePlugin implements LDEditorPlugin, ActionListener {
	
	
	private LDEditor editor = null;
	DrawHelpers dh = null;
	ConnectionHandler connHandler = null;
	LDrawGLDisplay display = null;
	Undo<LDPrimitive> undo = null;
	LDPrimitive currentPart = null;
	LDRenderedPart currPartRendered = null;
	
	Container toolPanel = null;
	Component[] savedComponents = new Component[0];
	ConnectionPoint rotatePoint = null;
	private JTextField angleEntry;
	private JLabel angleLabel;
	boolean startRotation = false;
	boolean inRotation = false; 

	
	
	public RotatePartModePlugin(LDEditor me, DrawHelpers dhelp, ConnectionHandler ch, 
			Undo<LDPrimitive>u, LDrawGLDisplay gld) {
		if (me == null || dhelp == null || ch == null || gld == null || u == null)
			throw new IllegalArgumentException("[RotatePartModePlugin] All parameters must be not null.");
		editor = me;
		dh = dhelp;
		connHandler = ch;
		display = gld;
		undo = u;
		// options control for rotation tool
		angleLabel = new JLabel("Angle (degree):",SwingConstants.RIGHT);
		angleEntry = new JTextField("0.0");
		angleEntry.setColumns(8);
		angleEntry.addActionListener(this);
	}
	
	
	
	@Override
	public void start(Object... params) {
		
		// parameters check
		if (params.length != 1)
			throw new IllegalArgumentException("[RotatePartModePlugin.start] Missing parameter needed.");
		if (!(params[0] instanceof Container))
			throw new IllegalArgumentException("[RotatePartModePlugin.start] Param[0] must be a Container.");
		toolPanel = (Container) params[0];
		dh.setPointerMode(PointerMode.STUD,Color.ORANGE);
		startRotation = true;
	}
	
	
	
	@Override
	public void reset() {
		
		if (startRotation) {
			dh.removeRotGadgets();
			startRotation = false;
		}
		else if (inRotation) {
			dh.removeRotGadgets();
			startRotation = false;
			inRotation = false;
			display.delRenderedPart(rotatePoint.getPartId());
			LDPrimitive p = editor.getPart(rotatePoint.getPartId());
			display.addRenderedPart(LDRenderedPart.newRenderedPart(p));
			rotatePoint = null;
			toolPanel.removeAll();
			for (Component c:savedComponents)
				toolPanel.add(c);
			//toolPanel.invalidate();
			toolPanel.getParent().validate();
		}
		//duplicate = false;
		dh.resetPointer();
		currentPart = null;
		currPartRendered = null;
		inRotation = false;
		startRotation = false;
		display.enableHover();
	}
	
	
	
	@Override
	public boolean doClick(int partId, Point3D eyeNear, Point3D eyeFar,
			PickMode mode) {
		
		if (startRotation && rotatePoint != null && mode == PickMode.NONE) {
			// select rotation axis
			inRotation = true;
			startRotation = false;
			display.addGadget(dh.getRotationHandle(eyeNear, eyeFar));
			display.disableHover();
			savedComponents = toolPanel.getComponents();
			toolPanel.removeAll();
			toolPanel.add(angleLabel);
			toolPanel.add(angleEntry);
//			toolPanel.invalidate();
//			toolPanel.validate();
			toolPanel.getParent().validate();
		}
		else if (inRotation && mode == PickMode.NONE) {
			// end rotation
			inRotation = false;
			startRotation = false;
			dh.removeRotGadgets();
			display.delRenderedPart(rotatePoint.getPartId());
			LDPrimitive p = editor.getPart(rotatePoint.getPartId());
			undo.startUndoRecord();
			p = p.transform(dh.getRotMatrix(rotatePoint));
			undo.recordDelete(editor.addPart(p));
			undo.recordAdd(p);
			undo.endUndoRecord();
			toolPanel.remove(angleLabel);
			toolPanel.remove(angleEntry);
			for (Component c: savedComponents)
				toolPanel.add(c);
//			toolPanel.invalidate();
			toolPanel.getParent().validate();
			//toolPanel.validate();
			dh.resetPointer();
			display.enableHover();
			//modified = true;
		}
		return inRotation || startRotation;
	}
	
	
	
	@Override
	public void doMove(int partId, Point3D eyeNear, Point3D eyeFar) {
		
		if (startRotation && partId != 0) {
			rotatePoint = connHandler.getPartNearConn(editor.getPart(partId), eyeFar, eyeNear);
			if (rotatePoint != null) {
				display.addGadget(dh.getRotationWheel(rotatePoint));
			}
		}
		else if (inRotation) {
			display.addGadget(dh.getRotationHandle(eyeFar, eyeNear));
			display.delRenderedPart(rotatePoint.getPartId());
			LDPrimitive p = editor.getPart(rotatePoint.getPartId()).getClone();
			p = p.transform(dh.getRotMatrix(rotatePoint));
			display.addRenderedPart(LDRenderedPart.newRenderedPart(p));
			angleEntry.setText(String.format(Locale.US,	"%.1f",dh.getRotAngle()*180/Math.PI));
		}
	}
	
	
	
	@Override
	public boolean doKeyPress(KeyEvent e) {

		if (inRotation) {
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
			display.addRenderedPart(currPartRendered.fastMove(editor.getCursor()));
			return true;
		}
		return false;
	}



	@Override
	public void doWindowSelected(Set<Integer> selected) { /* do nothing */ }



	@Override
	public void doColorchanged(int colorIndex) { /* do nothing */ }



	@Override
	public void actionPerformed(ActionEvent e) {
		
		if (e.getSource() == angleEntry) {
			if (inRotation) {
				String n = angleEntry.getText();
				try {
					float a = Float.parseFloat(n);
					inRotation = false;
					startRotation = false;
					dh.removeRotGadgets();
					display.delRenderedPart(rotatePoint.getPartId());
					LDPrimitive p = editor.getPart(rotatePoint.getPartId());
					undo.startUndoRecord();
					p = p.transform(
							new Matrix3D(-rotatePoint.getP1().x, -rotatePoint.getP1().y, -rotatePoint.getP1().z)
							.transform(JSimpleGeom.axisRotMatrix(rotatePoint.getP1(), rotatePoint.getP2(), a))
							.moveTo(rotatePoint.getP1()));
					undo.recordAdd(p);
					undo.recordDelete(editor.addPart(p));
					undo.endUndoRecord();
					//gldisplay.addRenderedPart(LDRenderedPart.newRenderedPart(p));
					toolPanel.remove(angleLabel);
					toolPanel.remove(angleEntry);
					for (Component c: savedComponents)
						toolPanel.add(c);
					//toolPanel.invalidate();
					toolPanel.getParent().validate();
					dh.resetPointer();
					editor.pluginEndAction();
				}
				catch (NumberFormatException ex) {
					angleEntry.setBorder(BorderFactory.createLineBorder(Color.RED));
				}
			}
		}
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
