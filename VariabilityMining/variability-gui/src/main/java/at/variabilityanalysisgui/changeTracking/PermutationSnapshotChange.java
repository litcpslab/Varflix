package at.variabilityanalysisgui.changeTracking;

import java.util.List;

import variabilityMining.TreePermutation;

public class PermutationSnapshotChange<T, U> implements ChangeModel<T, U> {

    private final List<TreePermutation> permutations;
    private final int index;
    private final TreePermutation before;
    private final TreePermutation after;

    public PermutationSnapshotChange(List<TreePermutation> permutations, int index, TreePermutation before, TreePermutation after) {
        this.permutations = permutations;
        this.index = index;
        this.before = before;
        this.after = after;
    }

    @Override
    public void undo(T controller, U viewController) {
        restore(before);
    }

    @Override
    public void redo(T controller, U viewController) {
        restore(after);
    }

    private void restore(TreePermutation snapshot) {
        if (permutations != null && index >= 0 && index < permutations.size() && snapshot != null) {
            permutations.set(index, snapshot.deepClone());
        }
    }
}
