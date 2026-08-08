package variabilityMining;

import constraints.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TreePermutation {
    private Feature root;
    private List<Feature> features;
    private List<SimpleConstraint> ctcs;
    private List<Group> groups;

    public TreePermutation(Feature root,  List<Feature> features) {

    }

    public TreePermutation(Feature root, List<Feature> features, List<SimpleConstraint> ctcs) {
        this.root = root;
        this.features = features;
        this.ctcs = ctcs;
        this.groups = new ArrayList<>();
    }

    public TreePermutation deepClone() {
        Map<Feature, Feature> oldToNewMap = new HashMap<>();

        for (Feature oldF : this.features) {
            Feature newF = new Feature(oldF.getName());
            newF.setMandatory(oldF.isMandatory());
            newF.setAlternativeParent(oldF.isAlternativeParent());
            newF.setOrParent(oldF.isOrParent());
            oldToNewMap.put(oldF, newF);
        }

        for (Feature oldF : this.features) {
            Feature newF = oldToNewMap.get(oldF);

            if (oldF.getParent() != null) {
                newF.setParent(oldToNewMap.get(oldF.getParent()));
            }

            List<Feature> newChildren = new ArrayList<>();
            if (oldF.getChildren() != null) {
                for (Feature oldChild : oldF.getChildren()) {
                    newChildren.add(oldToNewMap.get(oldChild));
                }
            }
            newF.setChildren(newChildren);
        }

        Feature clonedRoot = oldToNewMap.get(this.root);
        List<Feature> clonedFeatures = new ArrayList<>(oldToNewMap.values());

        List<SimpleConstraint> clonedCtcs = new ArrayList<>();
        for (SimpleConstraint ctc : this.ctcs) {
            Feature clonedF1 = oldToNewMap.get(ctc.getFeature1());
            Feature clonedF2 = oldToNewMap.get(ctc.getFeature2());

            if (clonedF1 != null && clonedF2 != null) {
                if (ctc instanceof constraints.Implication) {
                    clonedCtcs.add(new constraints.Implication(clonedF1, clonedF2));
                } else if (ctc instanceof constraints.MutualExclusion) {
                    clonedCtcs.add(new constraints.MutualExclusion(clonedF1, clonedF2));
                } else if (ctc instanceof constraints.Equivalence) {
                    clonedCtcs.add(new constraints.Equivalence(clonedF1, clonedF2));
                } else {
                    clonedCtcs.add(new SimpleConstraint(clonedF1, clonedF2, ctc.getType()));
                }
            }
        }

        TreePermutation treePermutation = new TreePermutation(clonedRoot, clonedFeatures, clonedCtcs);

        List<Group> newGroups = new ArrayList<>();
        for(Group group : this.groups) {
            List<Feature> clonedGroupFeatures = new ArrayList<>();
            for (Feature oldGroupFeature : group.getFeatures()) {
                clonedGroupFeatures.add(oldToNewMap.get(oldGroupFeature));
            }
            Feature clonedGroupParent = oldToNewMap.get(group.getParent());

            if(group instanceof AlternativeGroup) {
                newGroups.add(new AlternativeGroup(clonedGroupFeatures, clonedGroupParent));
            } else if(group instanceof OrRelation) {
                newGroups.add(new OrRelation(clonedGroupFeatures, clonedGroupParent));
            }
        }
        treePermutation.setGroups(newGroups);

        return treePermutation;
    }

    private List<Feature> cloneFeatureGraph(List<Feature> originalList) {
        Map<Feature, Feature> oldToNewMap = new HashMap<>();

        for (Feature oldF : originalList) {
            Feature newF = new Feature(oldF.getName());
            newF.setMandatory(oldF.isMandatory());
            newF.setAlternativeParent(oldF.isAlternativeParent());
            newF.setOrParent(oldF.isOrParent());

            oldToNewMap.put(oldF, newF);
        }

        for (Feature oldF : originalList) {
            Feature newF = oldToNewMap.get(oldF);

            if (oldF.getParent() != null) {
                Feature clonedParent = oldToNewMap.get(oldF.getParent());
                newF.setParent(clonedParent);
            }

            List<Feature> newChildren = new ArrayList<>();
            if (oldF.getChildren() != null) {
                for (Feature oldChild : oldF.getChildren()) {
                    Feature clonedChild = oldToNewMap.get(oldChild);
                    newChildren.add(clonedChild);
                }
            }
            newF.setChildren(newChildren);
        }

        return new ArrayList<>(oldToNewMap.values());
    }

    public List<SimpleConstraint> getCtcs() {
        return ctcs;
    }

    public List<Group> getGroups() {
        return groups;
    }

    public Feature getRoot() {
        return root;
    }

    public void setRoot(Feature root) {
        this.root = root;
    }

    public List<Feature> getFeatures() {
        return features;
    }

    public void setFeatures(List<Feature> features) {
        this.features = features;
    }

    public void setCtcs(List<SimpleConstraint> ctcs) {
        this.ctcs = ctcs;
    }

    public void setGroups(List<Group> groups) {
        this.groups = groups;
    }

    public List<Constraint> getAllConstraints() {
        List<Constraint> constraints = new ArrayList<>(ctcs);
        constraints.addAll(groups);
        return constraints;
    }
}
