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

import java.util.ArrayList;
import java.util.List;

import at.variabilityanalysisgui.controller.Controller;
import at.variabilityanalysisgui.controller.FeatureViewController;
import at.variabilityanalysisgui.controller.TreeViewController;
import guiModel.Group;
import variabilityMining.TreePermutation;

public class DeleteGroup implements ChangeModel<FeatureViewController, TreeViewController> {

    private final Group group;
    private final int groupIndex;
    private final Controller mainController;
    private final String featureName;
    private final List<TreePermutation> beforeSnapshots;
    private List<TreePermutation> afterSnapshots;

    public DeleteGroup(Group group, int groupIndex, Controller mainController) {
        this.group = new Group(group);
        this.groupIndex = groupIndex;
        this.mainController = mainController;
        this.featureName = group.getName() != null ? group.getName().get() : null;
        this.beforeSnapshots = snapshotPermutations();
        this.afterSnapshots = null;
    }

    @Override
    public void undo(FeatureViewController controller, TreeViewController treeViewController) {
        if (groupIndex >= 0 && groupIndex <= controller.getOriginalGroups().size()) {
            controller.getOriginalGroups().add(groupIndex, group);
        } else {
            controller.getOriginalGroups().add(group);
        }
        treeViewController.populateTreeView(controller.getOriginalGroups(), null);
        restorePermutations(beforeSnapshots);
    }

    @Override
    public void redo(FeatureViewController controller, TreeViewController treeViewController) {
        controller.getOriginalGroups().remove(group);
        treeViewController.populateTreeView(controller.getOriginalGroups(), null);
        if (afterSnapshots != null) {
            restorePermutations(afterSnapshots);
            return;
        }
        if (featureName != null && mainController != null && mainController.getPermutations() != null) {
            for (TreePermutation permutation : mainController.getPermutations()) {
                permutation.removeFeatureByName(featureName);
            }
        }
        afterSnapshots = snapshotPermutations();
    }

    private List<TreePermutation> snapshotPermutations() {
        List<TreePermutation> snapshots = new ArrayList<>();
        if (mainController != null && mainController.getPermutations() != null) {
            for (TreePermutation permutation : mainController.getPermutations()) {
                snapshots.add(permutation.deepClone());
            }
        }
        return snapshots;
    }

    private void restorePermutations(List<TreePermutation> snapshots) {
        List<TreePermutation> permutations = mainController != null ? mainController.getPermutations() : null;
        if (permutations == null || snapshots == null) {
            return;
        }
        for (int i = 0; i < snapshots.size() && i < permutations.size(); i++) {
            permutations.set(i, snapshots.get(i).deepClone());
        }
    }
}
