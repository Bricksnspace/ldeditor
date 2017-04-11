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

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;

import bricksnspace.j3dgeom.Point3D;
import bricksnspace.ldraw3d.DrawHelpers;
import bricksnspace.ldraw3d.LDrawGLDisplay;
import bricksnspace.ldraw3d.PickMode;
import bricksnspace.ldrawlib.ConnectionHandler;
import bricksnspace.ldrawlib.LDPrimitive;
import bricksnspace.ldrawlib.LDrawPart;
import bricksnspace.simpleundo.Undo;

public class BuildStepPlugin implements LDEditorPlugin, ActionListener {

	// TODO: insert/split step
	// TODO: merge/delete step
	
	
	private LDEditor editor;
	//private DrawHelpers dh;
	//private Undo<LDPrimitive> undo;
	private LDrawGLDisplay display;
	
	// plugin status
	//private boolean stepEditing = false;
	private int currentStep = 0;
	
	// tool container and saving
	private Container toolPanel = null;
	private Component[] savedComponents = new Component[0];
	
	// editing controls
	private JButton moveToNext;
	private JButton moveHere;
	private JButton moveToPrev;
	private JButton autoStep;
	
	
	

	public BuildStepPlugin(LDEditor me, DrawHelpers dhelp, ConnectionHandler ch, 
			Undo<LDPrimitive>u, LDrawGLDisplay gld) {
		if (me == null || dhelp == null || ch == null || gld == null || u == null)
			throw new IllegalArgumentException("[LDStepEditorPlugin] All parameters must be not null.");
		editor = me;
//		dh = dhelp;
//		undo = u;
		display = gld;
		// controls for step editing
		String imgFolder;
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		if (d.width > 1600 && d.height > 1250) {
			imgFolder = "imghires/";
		}
		else {
			imgFolder = "images/";
		}		

		moveToNext = new JButton(new ImageIcon(this.getClass().getResource(imgFolder+"move-step-next.png")));
		moveToNext.setToolTipText("Move to next step");
		moveToNext.addActionListener(this);
		moveHere = new JButton(new ImageIcon(this.getClass().getResource(imgFolder+"move-step-here.png")));
		moveHere.setToolTipText("Move to current step");
		moveHere.addActionListener(this);
		moveToPrev = new JButton(new ImageIcon(this.getClass().getResource(imgFolder+"move-step-prev.png")));
		moveToPrev.setToolTipText("Move to previous step");
		moveToPrev.addActionListener(this);
		autoStep = new JButton(new ImageIcon(this.getClass().getResource(imgFolder+"auto-step.png")));
		autoStep.setToolTipText("Auto-generate steps");
		autoStep.addActionListener(this);
	}
	
	
	@Override
	public void start(Object... params) {

		// parameters check
		if (params.length != 1)
			throw new IllegalArgumentException("[LDStepEditorPlugin.start] Missing parameter needed.");
		if (!(params[0] instanceof Container))
			throw new IllegalArgumentException("[LDStepEditorPlugin.start] Param[0] must be a Container.");
		toolPanel = (Container) params[0];
		//stepEditing = true;
		currentStep = editor.getCurrStep();
		highLightStep();
		display.update();
		savedComponents = toolPanel.getComponents();
		toolPanel.removeAll();
		toolPanel.add(autoStep);
		toolPanel.add(moveToPrev);
		toolPanel.add(moveHere);
		toolPanel.add(moveToNext);
		toolPanel.getParent().validate();
	}

	
	
	@Override
	public void reset() {
		
		//stepEditing = false;
		resetHighLight();
		display.update();
		toolPanel.removeAll();
		for (Component c:savedComponents)
			toolPanel.add(c);
		toolPanel.getParent().validate();
	}

	
	@Override
	public boolean doClick(int partId, Point3D eyeNear, Point3D eyeFar,
			PickMode mode) {

		return true;
	}

	@Override
	public void doMove(int partId, Point3D eyeNear, Point3D eyeFar) {
		// TODO Stub di metodo generato automaticamente
		
	}

	@Override
	public void doWindowSelected(Set<Integer> selected) {
		// TODO Stub di metodo generato automaticamente
		
	}

	@Override
	public boolean doKeyPress(KeyEvent e) {
		// TODO Stub di metodo generato automaticamente
		return false;
	}

	@Override
	public boolean doMatrixChanged() {
		// TODO Stub di metodo generato automaticamente
		return false;
	}

	@Override
	public void doColorchanged(int colorIndex) {
		// TODO Stub di metodo generato automaticamente
		
	}


	@Override
	public void doStepChanged(int step) {
		
		resetHighLight();
		currentStep = step;
		highLightStep();
		display.update();
	}

	
	
	/**
	 * highlight parts in current step and dims parts in all remaining steps
	 */
	private void highLightStep() {

		for (LDPrimitive p: editor.getPartsInStep(currentStep)) {
			if (!p.isDrawable())
				continue;
			//System.out.println(p);
			display.getPart(p.getId()).highLight();
		}
		for (int i=currentStep+1;i<=editor.getNumSteps();i++) {
			for (LDPrimitive p: editor.getPartsInStep(i)) {
				if (!p.isDrawable())
					continue;
				display.getPart(p.getId()).dimOn();
			}
		}

	}
	
	
	
	/**
	 * remove highlight from current step 
	 */
	private void resetHighLight() {

		for (LDPrimitive p: editor.getPartsInStep(currentStep)) {
			if (!p.isDrawable())
				continue;
			display.getPart(p.getId()).highLightOff();
		}
		for (int i=currentStep+1;i<=editor.getNumSteps();i++) {
			for (LDPrimitive p: editor.getPartsInStep(i)) {
				if (!p.isDrawable())
					continue;
				display.getPart(p.getId()).dimOff();
			}
		}
	}


	@Override
	public void actionPerformed(ActionEvent e) {

		if (e.getSource() == autoStep) {
			int res = JOptionPane.showConfirmDialog(autoStep, "This will reorganize building steps.\nIt'll deletes every defined STEP.\nAre you sure?",
					"Confirm auto-STEP", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (res == JOptionPane.YES_OPTION) {
				editor.unselectAll();
				resetHighLight();
				int partPerStep = 3;
				boolean submodelInStep = true;
				List<LDPrimitive> parts;
				if (editor.getNumSteps() > 0) {
					// part has step, so get them in order
					parts = new ArrayList<LDPrimitive>();
					// copy parts in new list in same order that are in defined steps
					for (int i=1;i<=editor.getNumSteps();i++) {
						Collection<LDPrimitive> stepList = editor.getPartsInStep(i);
						for (LDPrimitive p:stepList) {
							parts.add(p);
						}
					}
				}
				else {
					// no step defined, go on
					parts = editor.getPrimitives();
				}
				// here you have parts filled with model parts
				// here we go
				editor.goFirstStep();
				int partcount = 1;
				for (LDPrimitive p:parts) {
					editor.moveToCurrStep(p);
					if (!p.isDrawable()) {
						// if "part" is a META or a comment, ignore it in part count
						continue;
					}
					if (submodelInStep && LDrawPart.existsCustomPart(p.getLdrawId())) {
						// it is a submodel
						// put it in a separate step alone
						partcount = 1;
						editor.nextStep();
						continue;
					}
					partcount++;
					if (partcount > partPerStep) {
						editor.nextStep();
						partcount = 1;
					}
				}
				editor.goFirstStep();
			}
			return;
		}
		if (editor.getSelected().size() == 0)
			return;
		if (e.getSource() == moveToNext) {
			resetHighLight();
			for (int id: editor.getSelected()) {
				LDPrimitive p = editor.getPart(id);
				editor.moveToStep(p,editor.getCurrStep()+1);
			}			
			highLightStep();
			display.update();			
		}
		else if (e.getSource() == moveHere) {
			resetHighLight();
			for (int id: editor.getSelected()) {
				LDPrimitive p = editor.getPart(id);
				editor.moveToCurrStep(p);
			}
			highLightStep();
			display.update();
		}
		else if (e.getSource() == moveToPrev) {
			resetHighLight();
			for (int id: editor.getSelected()) {
				LDPrimitive p = editor.getPart(id);
				editor.moveToStep(p,editor.getCurrStep()-1);
			}			
			highLightStep();
			display.update();
		}
	}


	@Override
	public boolean needSelection() {

		return true;
	}


	@Override
	public void doDragParts(int partId) {
		// do nothing
	}
	
	
}
