/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla
 * Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Johannes Kepler University Linz
 * LIT Cyber-Physical Systems Lab
 * Contributors:
 *  Sophie Öttl - Change Tracking
 ********************************************************************************/

package at.variabilityanalysisgui.changeTracking;

import at.variabilityanalysisgui.controller.ConstraintsViewController;
import at.variabilityanalysisgui.controller.Controller;
import at.variabilityanalysisgui.controller.FeatureViewController;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.Stack;

public class ChangeTracker<T, U> {

    private final T controller;
    private final U viewController;
    private final Stack<ChangeModel<T, U>> undoStack = new Stack<>();
    private final Stack<ChangeModel<T, U>> redoStack = new Stack<>();

    private final SimpleBooleanProperty canUndo = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty canRedo = new SimpleBooleanProperty(false);

    public ChangeTracker(T controller, U viewController) {
        this.controller = controller;
        this.viewController = viewController;
    }

    public void undo() {
        if (!undoStack.isEmpty()) {
            ChangeModel<T, U> change = undoStack.pop();
            change.undo(controller, viewController);
            redrawVisualization(controller);
            redoStack.push(change);
            updateProperties();
        }
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            ChangeModel<T, U> change = redoStack.pop();
            change.redo(controller, viewController);
            redrawVisualization(controller);
            undoStack.push(change);
            updateProperties();
        }
    }

    public void addUndo(ChangeModel<T, U> undo) {
        undoStack.push(undo);
        redoStack.clear();
        updateProperties();
    }

    private void updateProperties() {
        canUndo.set(!undoStack.isEmpty());
        canRedo.set(!redoStack.isEmpty());
    }

    public SimpleBooleanProperty canUndoProperty() {
        return canUndo;
    }

    public SimpleBooleanProperty canRedoProperty() {
        return canRedo;
    }

    public void redrawVisualization(T controller) {
        if (controller instanceof FeatureViewController) {
            ((FeatureViewController) controller).redrawVisualization();
        } else {
            ConstraintsViewController constraintsController = (ConstraintsViewController) controller;
            if (constraintsController.getMainController() != null) {
                constraintsController.getMainController().redrawVisualization();
            } else {
                constraintsController.updateConstraintModel();
            }
        }
    }

    public void clearStack() {
        undoStack.clear();
        redoStack.clear();
        updateProperties();
    }
}
