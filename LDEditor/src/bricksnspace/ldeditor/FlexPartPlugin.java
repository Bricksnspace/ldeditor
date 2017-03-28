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


import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.JButton;

import bricksnspace.j3dgeom.JSimpleGeom;
import bricksnspace.j3dgeom.Matrix3D;
import bricksnspace.j3dgeom.Point3D;
import bricksnspace.ldraw3d.DrawHelpers;
import bricksnspace.ldraw3d.Gadget3D;
import bricksnspace.ldraw3d.LDRenderedPart;
import bricksnspace.ldraw3d.LDrawGLDisplay;
import bricksnspace.ldraw3d.PickMode;
import bricksnspace.ldrawlib.ConnectionHandler;
import bricksnspace.ldrawlib.ConnectionPoint;
import bricksnspace.ldrawlib.LDFlexPart;
import bricksnspace.ldrawlib.LDPrimitive;
import bricksnspace.ldrawlib.LDrawColor;
import bricksnspace.ldrawlib.LDrawPart;
import bricksnspace.simpleundo.Undo;


/**
 * Flexible parts editing
 * 
 * @author Mario Pascucci
 *
 */
public class FlexPartPlugin implements LDEditorPlugin, ActionListener {
	
	
	LDEditor editor = null;
	DrawHelpers dh = null;
	ConnectionHandler connHandler = null;
	LDrawGLDisplay display = null;
	Undo<LDPrimitive> undo = null;
	
	// main model name for sub-part name
	String mainModel;
	
	// counter for sub-part instance
	private static int count = 0;

	// current part
	LDFlexPart flexPart;
	String partId;
	int colorIndex;
	LDPrimitive currentPart = null;
	LDPrimitive headPart = null;
	LDPrimitive tailPart = null;
	LDRenderedPart currPartRendered = null;
	
	// gadgets to display bezier
	private Gadget3D currentBezier = null;
	
	// selected point marker and constraints
	List<ConnectionPoint> constraintPoints = new ArrayList<ConnectionPoint>();
	ConnectionPoint selectedPoint = ConnectionPoint.getDummy(new Point3D(0,0,0), new Point3D(10,0,0));
	
	// curve start an end points
	ConnectionPoint startPoint = null;
	ConnectionPoint endPoint = null;
	
	// internal status
	private boolean movingHead = false;
	private boolean movingTail = false;
	private boolean addConstraint = false;
	
	// user interaction
	Container toolPanel = null;
	Component[] savedComponents = new Component[0];
	JButton delConstraint;
	JButton endEdit;

	
	
	
	public FlexPartPlugin(LDEditor me, DrawHelpers dhelp, ConnectionHandler ch, 
			Undo<LDPrimitive>u, LDrawGLDisplay gld) {
		if (me == null || dhelp == null || ch == null || gld == null || u == null)
			throw new IllegalArgumentException("[RotatePartModePlugin] All parameters must be not null.");
		editor = me;
		dh = dhelp;
		connHandler = ch;
		display = gld;
		undo = u;
		delConstraint = new JButton("Remove last point");
		delConstraint.addActionListener(this);
		endEdit = new JButton("Accept");
		endEdit.addActionListener(this);
	}
	
	
	/**
	 * Requires four parameter: 
	 *  - tool panel container
	 *  - LDraw part id
	 *  - LDraw color index
	 *  - main model name string
	 */
	@Override
	public void start(Object... params) {
		
		// parameters check
		if (params.length != 4)
			throw new IllegalArgumentException("[FlexPartPlugin.start] Wrong parameter number, must be 4.");
		if (!(params[0] instanceof Container))
			throw new IllegalArgumentException("[FlexPartPlugin.start] Param[0] must be a Container.");
		if (!(params[1] instanceof String))
			throw new IllegalArgumentException("[FlexPartPlugin.start] Param[1] must be String.");
		if (!(params[2] instanceof Integer))
			throw new IllegalArgumentException("[FlexPartPlugin.start] Param[2] must be Integer");
		if (!(params[3] instanceof String))
			throw new IllegalArgumentException("[FlexPartPlugin.start] Param[3] must be String.");
		String partId = (String) params[1];
		colorIndex = (Integer) params[2];
		toolPanel = (Container) params[0];
		mainModel = (String) params[3];
		// now gets head for flex part
		flexPart = LDFlexPart.getFlexPart(partId);
		headPart = LDPrimitive.newGlobalPart(flexPart.getStart(),colorIndex,dh.getCurrentMatrix());
		currentPart = headPart;
		currPartRendered = LDRenderedPart.newRenderedPart(currentPart);
		display.disableHover();
		endEdit.setEnabled(false);
		delConstraint.setEnabled(false);
		movingHead = true;
		savedComponents = toolPanel.getComponents();
		toolPanel.removeAll();
		toolPanel.add(delConstraint);
		toolPanel.add(endEdit);
		toolPanel.getParent().validate();
		constraintPoints.clear();
		display.addRenderedPart(currPartRendered.fastMove(editor.getCursor()));
	}
	
	
	
	@Override
	public void reset() {
		
		if (movingHead || movingTail || addConstraint) {
			movingHead = false;
			movingTail = false;
			if (tailPart != null)
				display.delRenderedPart(tailPart.getId());
			if (headPart != null)
				display.delRenderedPart(headPart.getId());
			display.removeGadget(DrawHelpers.FLEXPOINT);
			display.removeGadget(DrawHelpers.BEZIER);
		}
		addConstraint = false;
		toolPanel.removeAll();
		for (Component c: savedComponents)
			toolPanel.add(c);
		toolPanel.getParent().validate();
		dh.resetPointer();
		currentPart = null;
		currPartRendered = null;
		display.enableHover();
	}
	
	
	
	private static synchronized int getInstanceCount() {
		
		return ++count;
	}

	
	
	private void renderMiddle() {
		
		String n = flexPart.getName().toLowerCase();
		if (n.endsWith(".dat")) {
			n = n.substring(0, n.length()-4);
		}
		String name;
		// to avoid generated name collision
		do {
			if (mainModel.length() == 0) {
				name = n+"-"+getInstanceCount()+".ldr";
			} 
			else if (mainModel.lastIndexOf(".") > 0) {
				name = mainModel.substring(0, mainModel.lastIndexOf(".")) + " - " +n+"-"+getInstanceCount()+".ldr";
			}
			else {
				name = mainModel  + " - " +n+"-"+getInstanceCount()+".ldr";
			}
		}
		while (LDrawPart.existsCustomPart(name));
		
		LDrawPart flex = LDrawPart.newCustomPart(name);
		flex.setDescription("JBrickBuilder generated flex part");
		flex.addPart(LDPrimitive.newMetaUnk("!LDRAW_ORG Unofficial_Part"));
		Point3D headOffset = headPart.getTransformation().getOffsetPoint();
		Point3D tailOffset = headOffset.vector(tailPart.getTransformation().getOffsetPoint());
		headPart = headPart.setTransform(headPart.getTransformation().getOnlyRotation());
		tailPart = tailPart.setTransform(tailPart.getTransformation().getOnlyRotation().moveTo(tailOffset));
		List<ConnectionPoint> cpp = constraintPoints;
		constraintPoints = new ArrayList<ConnectionPoint>();
		for (ConnectionPoint cp: cpp) {
			constraintPoints.add(ConnectionPoint.getDummy(headOffset.vector(cp.getP1()), 
					headOffset.vector(cp.getP2())));
		}
		cpp = null;
		flex.addPart(headPart.setColorIndex(LDrawColor.CURRENT));
		flex.addPart(tailPart.setColorIndex(LDrawColor.CURRENT));
		if (flexPart.isContinue()) {
			// if flexpart is continue:
			// join two or more segments to reduce part size
			// compute vector and stretch values
			// place place parts on bezier
			int resolution;
			// if maxLength == 0 part doesn't have limit length
			if (flexPart.getMaxLength() > 0) {
				resolution = (int) (flexPart.getMaxLength()) * 2 + 1;
			}
			else {
				resolution = (int) (headPart.getTransformation().getOffsetPoint()
						.vector(tailPart.getTransformation().getOffsetPoint()).modulo() + 1);
			}
			float[] p = DrawHelpers.getBezier(
					flexPart.getStartVector().transform(headPart.getTransformation()),
					flexPart.getEndVector().transform(tailPart.getTransformation()),
					constraintPoints,
					flexPart.getHowRigid(),resolution);
			ConnectionPoint midv = flexPart.getMidVector().getClone(0);
			// minimum segment length 
			float length = midv.getP1().vector(midv.getP2()).modulo();
			// reduces segment number
			int index = 3;
			List<Point3D> bz = new ArrayList<Point3D>();
			bz.add(new Point3D(p[0],p[1],p[2]));
			while (index < p.length-3) {
				Point3D ps = new Point3D(p[index],p[index+1],p[index+2]);
				Point3D pe = new Point3D(p[index+3],p[index+4],p[index+5]);
				// if angle < 0.81 degree, consider two consecutive segment as one
				if (JSimpleGeom.dotProdAngle(bz.get(bz.size()-1), ps, ps, pe) > 0.9999) {
					index += 3;
					continue;
				}
				bz.add(ps);
				index += 3;
			}
			bz.add(new Point3D(p[index],p[index+1],p[index+2]));
			Point3D p1 = new Point3D(0, 0, 0);
			Point3D p2 = new Point3D(0, 0, 0);
			Matrix3D m = new Matrix3D();
			for (int i=0; i<bz.size()-1;i++) {
				p1 = bz.get(i);
				p2 = bz.get(i+1);
				float scale = (p1.vector(p2).modulo() + flexPart.getOverlapLen()) / length;
				Point3D scaleVector = flexPart.getMidVector().getP1().vector(flexPart.getMidVector().getP2())
						.normalize().scale(scale);
				m = m.transform(JSimpleGeom.alignMatrix(
								midv.getP1(),midv.getP2(),p1,p2));
				Matrix3D mv = Matrix3D.getScale(scaleVector.x==0?1:scaleVector.x,
						scaleVector.y==0?1:scaleVector.y,
						scaleVector.z==0?1:scaleVector.z)
						.transform(m).moveTo(p1);
				midv.setP1(p1);
				midv.setP2(p2);
				flex.addPart(LDPrimitive.newGlobalPart(flexPart.getMid(),LDrawColor.CURRENT,mv));
			}
		}
		else {
			float length = JSimpleGeom.modulo(flexPart.getMidVector().getP1(),flexPart.getMidVector().getP2());
			int resolution;
			// if maxLength == 0 part doesn't have limit length
			if (flexPart.getMaxLength() > 0) {
				resolution = (int) (flexPart.getMaxLength() / (length-flexPart.getOverlapLen())  * 5 + 1);
			}
			else {
				resolution = (int) (headPart.getTransformation().getOffsetPoint()
						.vector(tailPart.getTransformation().getOffsetPoint()).modulo() /
						(length-flexPart.getOverlapLen()) * 5 + 1);
			}
			float[] p = DrawHelpers.getBezier(
					flexPart.getStartVector().transform(headPart.getTransformation()),
					flexPart.getEndVector().transform(tailPart.getTransformation()),
					constraintPoints,
					flexPart.getHowRigid(),resolution);
			// reduces points for Bézier
			int index = 0;
			List<Point3D> bz = new ArrayList<Point3D>();
			Point3D ps = new Point3D(p[0],p[1],p[2]);
			float initStep = JSimpleGeom.modulo(flexPart.getMidVector().getP1(), Point3D.ORIGIN);
			if (initStep > 0) { 
				for (;;) {
					index += 3;
					Point3D pe = new Point3D(p[index],p[index+1],p[index+2]);
					float l = JSimpleGeom.modulo(pe,ps);
					// consider a segment as length as mid segment part
					if (l < initStep) {
						continue;
					}
					ps = ps.vector(pe).normalize().scale(initStep-flexPart.getOverlapLen()).translate(ps);
					break;
				}
				bz.add(ps);
			}
			else {
				bz.add(new Point3D(p[0],p[1],p[2]));
			}
			while (index < p.length) {
				ps = new Point3D(p[index],p[index+1],p[index+2]);
				//Point3D pe = new Point3D(p[index+3],p[index+4],p[index+5]);
				float l = JSimpleGeom.modulo(bz.get(bz.size()-1),ps);
				// consider a segment as length as mid segment part
				if (l < length-flexPart.getOverlapLen()) {
					index += 3;
					continue;
				}
				ps = bz.get(bz.size()-1).vector(ps).normalize().scale(length-flexPart.getOverlapLen())
						.translate(bz.get(bz.size()-1));
				bz.add(ps);
				//System.out.println(ps);
				index += 3;
			}
			index -= 3;
			bz.add(new Point3D(p[index],p[index+1],p[index+2]));
			//System.out.println(p.length+" r:"+bz.size());
			ConnectionPoint midv = flexPart.getMidVector().getClone(0);
			Point3D p1 = new Point3D(0, 0, 0);
			Point3D p2 = new Point3D(0, 0, 0);
			Matrix3D m = new Matrix3D();
			for (int i=0; i<bz.size()-1;i++) {
				p1 = bz.get(i);
				p2 = bz.get(i+1);
				m = m.transform(JSimpleGeom.alignMatrix(
						midv.getP1(),midv.getP2(),p1,p2));
				Matrix3D mv = m.moveTo(p1);
				midv.setP1(p1);
				midv.setP2(p2);
				flex.addPart(LDPrimitive.newGlobalPart(flexPart.getMid(),LDrawColor.CURRENT,mv));
			}
		}
		LDPrimitive p = LDPrimitive.newGlobalPart(name, colorIndex, new Matrix3D().moveTo(headOffset));
		undo.startUndoRecord();
		editor.addPart(p);
		undo.recordAdd(p);
		undo.endUndoRecord();
		editor.addUnsavedPart(p.getLdrawId());
		display.disableAutoRedraw();
		display.delRenderedPart(tailPart.getId());
		display.delRenderedPart(headPart.getId());
		display.enableAutoRedraw();
		display.addRenderedPart(LDRenderedPart.newRenderedPart(p));
	}
	
	
	
	@Override
	public boolean doClick(int partId, Point3D eyeNear, Point3D eyeFar,
			PickMode mode) {
		
		Point3D prevCursor = null;
		if ((movingHead || movingTail || addConstraint) && mode == PickMode.NONE) {
			float[] pos = dh.getTargetPoint(eyeNear,eyeFar);
			if (pos[0] < -0.99) // line is parallel, no intersection 
				return true;
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
			prevCursor = cursor;
		}
		if ((movingHead || movingTail) && mode == PickMode.NONE) {
			if (LDEditor.isAutoconnect()) {
				if (connHandler.isLocked()) {
					display.getPart(connHandler.getTarget().getPartId()).unConnect();
				}
				currentPart = currentPart.moveTo(connHandler.getLastConn());
			}
			else {
				currentPart = currentPart.moveTo(prevCursor);
			}
			if (movingHead) {
				movingHead = false;
				movingTail = true;
				headPart = currentPart;
				tailPart = LDPrimitive.newGlobalPart(flexPart.getEnd(),colorIndex,dh.getCurrentMatrix());
				currentPart = tailPart;
				currPartRendered = LDRenderedPart.newRenderedPart(currentPart);
				display.removeGadget(DrawHelpers.FLEXPOINT);
				display.addRenderedPart(currPartRendered);
			}
			else if (movingTail) {
				movingTail = false;
				addConstraint = true;
				tailPart = currentPart;
				currentBezier = DrawHelpers.getBezierGadget(
						flexPart.getStartVector().transform(headPart.getTransformation()),
						flexPart.getEndVector().transform(tailPart.getTransformation()),
						constraintPoints,
						flexPart.getHowRigid(), flexPart.getMaxLength());
				display.addGadget(currentBezier);
				//display.removeGadget(currentBezier.getId());
				currPartRendered = LDRenderedPart.newRenderedPart(currentPart);
				display.removeGadget(DrawHelpers.FLEXPOINT);
				display.addRenderedPart(currPartRendered);
				//renderMiddle();
				endEdit.setEnabled(true);
			}
		}
		else if (addConstraint && mode == PickMode.NONE) {
			ConnectionPoint conn = connHandler.getNearestConnection(eyeNear, eyeFar);
			if (conn != null) {
				constraintPoints.add(selectedPoint.transform(dh.getCurrentMatrix()).fastMove(conn.getP1()));
			}
			else {
				constraintPoints.add(selectedPoint.transform(dh.getCurrentMatrix()).fastMove(prevCursor));
			}
			currentBezier = DrawHelpers.getBezierGadget(
					flexPart.getStartVector().transform(headPart.getTransformation()),
					flexPart.getEndVector().transform(tailPart.getTransformation()),
					constraintPoints,
					flexPart.getHowRigid(), flexPart.getMaxLength());
			display.addGadget(currentBezier);
			if (constraintPoints.size() > 0)
				delConstraint.setEnabled(true);
		}
		return true;
	}
	
	
	
	@Override
	public void doMove(int partId, Point3D eyeNear, Point3D eyeFar) {

		if (movingTail || movingHead) {
			if (LDEditor.isAutoconnect()) {
				ConnectionPoint p = connHandler.getTarget();
				if (p != null) {
					display.getPart(p.getPartId()).unConnect();
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
			}
			if (movingHead) {
				display.addGadget(DrawHelpers.getFlexPoint(flexPart.getStartVector()
						.transform(currentPart.getTransformation())).fastMove(
								LDEditor.isAutoconnect()?connHandler.getLastConn():editor.getCursor()));
			}
			if (movingTail) {
				currentBezier = DrawHelpers.getBezierGadget(
						flexPart.getStartVector().transform(headPart.getTransformation()),
						flexPart.getEndVector().transform(currentPart.getTransformation().moveTo(
								LDEditor.isAutoconnect()?connHandler.getLastConn():editor.getCursor())),
						constraintPoints,
						flexPart.getHowRigid(), flexPart.getMaxLength());
				display.addGadget(currentBezier);
				display.addGadget(DrawHelpers.getFlexPoint(flexPart.getEndVector()
						.transform(currentPart.getTransformation())).fastMove(
								LDEditor.isAutoconnect()?connHandler.getLastConn():editor.getCursor()));
			}
			display.addRenderedPart(currPartRendered.fastMove(
					LDEditor.isAutoconnect()?connHandler.getLastConn():editor.getCursor()));
		}
		else if (addConstraint) {
			display.removeGadget(DrawHelpers.FLEXPOINT);
			ConnectionPoint conn = connHandler.getNearestConnection(eyeNear, eyeFar);
			if (conn != null) {
				display.addGadget(DrawHelpers.getConstraintPoint(selectedPoint
						.transform(dh.getCurrentMatrix())).fastMove(conn.getP1()));
			}
			else {
				display.addGadget(DrawHelpers.getConstraintPoint(selectedPoint
						.transform(dh.getCurrentMatrix())).fastMove(editor.getCursor()));
			}
		}
	}
	
	
	
	@Override
	public boolean doKeyPress(KeyEvent e) {
		if (movingHead || movingTail || addConstraint) {
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
		}
		if (movingHead || movingTail) {
			currentPart = currentPart.setTransform(dh.getCurrentMatrix());
			currPartRendered = LDRenderedPart.newRenderedPart(currentPart);
			display.addRenderedPart(currPartRendered.fastMove(
					LDEditor.isAutoconnect()?connHandler.getLastConn():editor.getCursor()));
			if (movingHead) {
				display.addGadget(
						DrawHelpers.getFlexPoint(flexPart.getStartVector().transform(currentPart.getTransformation()))
						.fastMove(LDEditor.isAutoconnect()?connHandler.getLastConn():editor.getCursor()));
			} 
			else if (movingTail) {
				display.addGadget(
						DrawHelpers.getFlexPoint(flexPart.getEndVector().transform(currentPart.getTransformation()))
						.fastMove(LDEditor.isAutoconnect()?connHandler.getLastConn():editor.getCursor()));
			}
			return true;
		}
		else if (addConstraint) {
			display.addGadget(DrawHelpers.getConstraintPoint(selectedPoint
					.transform(dh.getCurrentMatrix())).fastMove(
							LDEditor.isAutoconnect()?connHandler.getLastConn():editor.getCursor()));
			return true;
		}
		return false;
	}



	@Override
	public void doWindowSelected(Set<Integer> selected) { /* do nothing */ }



	@Override
	public void doColorchanged(int color) {
		
		// color change allowed only before placing first part
		if (movingHead) {
			colorIndex = color; 
			currentPart = currentPart.setColorIndex(colorIndex);
			currPartRendered = LDRenderedPart.newRenderedPart(currentPart);
			display.addRenderedPart(currPartRendered.fastMove(
					LDEditor.isAutoconnect()?connHandler.getLastConn():editor.getCursor()));
		}
	}






	@Override
	public boolean doMatrixChanged() {
		return false;
	}


	@Override
	public void actionPerformed(ActionEvent e) {
		
		if (e.getSource() == delConstraint) {
			constraintPoints.remove(constraintPoints.size()-1);
			currentBezier = DrawHelpers.getBezierGadget(
					flexPart.getStartVector().transform(headPart.getTransformation()),
					flexPart.getEndVector().transform(tailPart.getTransformation()),
					constraintPoints,
					flexPart.getHowRigid(), flexPart.getMaxLength());
			display.addGadget(currentBezier);
			if (constraintPoints.size() == 0)
				delConstraint.setEnabled(false);
		}
		else if (e.getSource() == endEdit) {
			renderMiddle();
			display.removeGadget(DrawHelpers.FLEXPOINT);
			display.removeGadget(DrawHelpers.BEZIER);
			addConstraint = false;
			toolPanel.removeAll();
			for (Component c: savedComponents)
				toolPanel.add(c);
			toolPanel.getParent().validate();
			dh.resetPointer();
			currentPart = null;
			currPartRendered = null;
			display.enableHover();
			editor.pluginEndAction();
		}
		
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
