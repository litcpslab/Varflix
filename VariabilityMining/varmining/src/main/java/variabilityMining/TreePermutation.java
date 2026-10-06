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

    public Feature getFeatureByName(String name) {
        for (Feature feature : features) {
            if (feature.getName().equals(name)) {
                return feature;
            }
        }
        return null;
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

    public boolean removeFeatureByName(String name) {
        Feature feature = getFeatureByName(name);
        if(feature == null) return false;
        return removeFeature(feature);
    }

    public boolean removeFeature(Feature feature) {
        return removeFeatureAndReplace(feature) && features.remove(feature);
    }

    private Feature replaceGroupParentWithDummy(Feature feature) {
        Feature newFeature;
        if (feature.isAlternativeParent()) {
            newFeature = new Feature("ALT_" + features.stream().filter(f -> f.getName().startsWith("ALT_")).count() + 1);
            newFeature.setAlternativeParent(true);
        } else if (feature.isOrParent()) {
            newFeature = new Feature("OR_" + features.stream().filter(f -> f.getName().startsWith("OR_")).count() + 1);
            newFeature.setAlternativeParent(true);
        } else return null;
        newFeature.setParent(feature.getParent());
        newFeature.setChildren(feature.getChildren());
        for (Feature child : feature.getChildren()) {
            child.setParent(newFeature);
        }
        for (Group group : groups) {
            if (group.getFeatures().contains(feature)) {
                group.getFeatures().add(newFeature);
                group.getFeatures().remove(feature);
            }
            if (group.getParent().equals(feature)) {
                group.setParent(newFeature);
            }
        }
        return newFeature;
    }

    private boolean addFeatureAsChildOfParent(Feature feature, Feature newParent) {
        if (newParent == null || feature == null) return false;
        boolean hasMutexToAllSiblings = getCtcs().stream()
                .filter(ctc -> ctc instanceof MutualExclusion)
                .filter(ctc -> ctc.getFeature1().equals(feature) || ctc.getFeature2().equals(feature))
                .map(ctc -> ctc.getFeature1().equals(feature) ? ctc.getFeature2() : ctc.getFeature1())
                .allMatch(sibling -> newParent.getChildren().contains(sibling));


        if(newParent.isAlternativeParent() && hasMutexToAllSiblings) {
            groups.stream().filter(group -> group instanceof AlternativeGroup && group.getParent().equals(newParent)).findFirst().ifPresent(group -> group.getFeatures().add(feature));
            // remove all sibling mutexes
            List<SimpleConstraint> siblingMutexes = new ArrayList<>();
            ctcs.stream().filter(ctc -> ctc instanceof MutualExclusion)
                    .filter(ctc -> (ctc.getFeature1().equals(feature) || ctc.getFeature2().equals(feature))
                            && (ctc.getFeature1() != feature && newParent.getChildren().contains(ctc.getFeature1()) || ctc.getFeature1() != feature && newParent.getChildren().contains(ctc.getFeature2())))
                    .forEach(ctc -> siblingMutexes.add(ctc));
            ctcs.removeAll(siblingMutexes);

        } else if(newParent.isOrParent() && feature.isOptional()) {
            groups.stream().filter(group -> group instanceof OrRelation && group.getParent().equals(newParent)).findFirst().ifPresent(group -> group.getFeatures().add(feature));

        } else if(newParent.getChildren().size() == 1 && feature.isOptional()) {
            // new or group
            List<Feature> orGroupFeatures = new ArrayList<>(newParent.getChildren());
            orGroupFeatures.add(feature);
            groups.add(new OrRelation(orGroupFeatures, newParent));

        } else if(newParent != root && !newParent.getChildren().isEmpty()) return false;

        // add as child
        feature.setParent(newParent);
        newParent.addChild(feature);

        // remove implication to new ancestors
        List<Feature> newAncestors = getFeatureAncestors(newParent);
        List<SimpleConstraint> newAncestorImplications = ctcs.stream().filter(ctc -> ctc instanceof Implication)
                .filter(ctc -> ctc.getFeature1().equals(feature) && newAncestors.contains(ctc.getFeature2()))
                .toList();
        ctcs.removeAll(newAncestorImplications);
        return true;
    }

    private List<Feature> getFeatureAncestors(Feature feature) {
        List<Feature> ancestors = new ArrayList<>();
        while (feature.getParent() != null) {
            feature = feature.getParent();
            ancestors.add(feature);
        }
        return ancestors;
    }

    private List<Feature> getFeatureDescendants(Feature feature) {
        List<Feature> descendants = new ArrayList<>();
        getFeatureDescendants(descendants, feature);
        return descendants;
    }

    private void getFeatureDescendants(List<Feature> descendants, Feature feature) {
        for(Feature child: feature.getChildren()) {
            descendants.add(child);
            getFeatureDescendants(descendants, child);
        }
    }

    private Feature findNewParentForOptionalFeature(Feature feature) {
        List<Feature> parentCandidates = getCtcs().stream()
                .filter(ctc -> ctc instanceof Implication && ctc.getFeature1().equals(feature))
                .map(ctc -> ctc.getFeature2())
                .toList();
        List<Feature> mutexFeatures = getCtcs().stream()
                .filter(ctc -> ctc instanceof MutualExclusion && (ctc.getFeature1().equals(feature) || ctc.getFeature2().equals(feature)))
                .map(ctc -> ctc.getFeature1().equals(feature) ? ctc.getFeature2() : ctc.getFeature1())
                .toList();
        List<Feature> featureDescendants = getFeatureDescendants(feature);
        List<Feature> mutexDescendantFeatures = new ArrayList<>();
        for(Feature descendant: featureDescendants) {
            mutexDescendantFeatures.addAll(getCtcs().stream()
                    .filter(ctc -> ctc instanceof MutualExclusion && (ctc.getFeature1().equals(descendant) || ctc.getFeature2().equals(descendant)))
                    .map(ctc -> ctc.getFeature1().equals(descendant) ? ctc.getFeature2() : ctc.getFeature1())
                    .toList());
        }
        for(Feature candidate: parentCandidates) {
            List<Feature> ancestors = getFeatureAncestors(candidate);
            boolean hasMutexAncestor = false;
            for(Feature ancestor: ancestors) {
                if(mutexFeatures.contains(ancestor) || mutexDescendantFeatures.contains(ancestor)) {
                    hasMutexAncestor = true;
                    break;
                }
            }
            if(hasMutexAncestor) continue;

            Feature current = candidate;
            while(mutexFeatures.contains(current) || current.isAlternativeParent()) {
                if(current.getChildren().isEmpty() || current.isOrParent() || current.isOptional()) {
                    return current;
                } else if(current.isMandatory()) {
                    current = current.getChildren().get(0);
                }
            }
        }
        return root;
    }

    private boolean removeFeatureAndReplace(Feature oldFeature) {
        Feature parent = oldFeature.getParent();
        List<Feature> children = oldFeature.getChildren();
        Group groupAsParent = groups.stream().filter(group -> group.getParent().equals(oldFeature)).findFirst().orElse(null);
        Group groupAsChild = groups.stream().filter(group -> group.getFeatures().contains(oldFeature)).findFirst().orElse(null);

        if ((parent.isAlternativeParent() && oldFeature.isOrParent())
                || oldFeature.isAlternativeParent() && parent.isOrParent()) {
            replaceGroupParentWithDummy(oldFeature);

        } else if (parent.isAlternativeParent() && !children.isEmpty() && children.get(0).isOptional()) {
            Feature orphanOptionalParent = findNewParentForOptionalFeature(children.get(0));
            boolean addedToNewParent = addFeatureAsChildOfParent(children.get(0), orphanOptionalParent);
            if (addedToNewParent) {
                ctcs.add(new Implication(children.get(0), parent));
            }
        } else {
            // children can be added to parent children
            if (children.isEmpty()) {
                parent.getChildren().remove(oldFeature);
                oldFeature.setParent(null);
            } else {
                if (oldFeature.isOptional() && !(oldFeature.isAlternativeParent() || oldFeature.isOrParent()))
                    children.get(0).setMandatory(false);
                children.addAll(parent.getChildren());
                children.remove(oldFeature);
                if (groupAsChild != null && groupAsParent != null) {
                    groupAsChild.getFeatures().addAll(groupAsParent.getFeatures());
                    groups.remove(groupAsParent);
                }
                if (children.size() > 1 && children.stream().allMatch(Feature::isOptional) && !(parent.isAlternativeParent() || parent.isOrParent())) {
                    groups.add(new OrRelation(children, parent));
                    parent.setOrParent(true);
                }
            }
        }
        parent.getChildren().remove(oldFeature);
        oldFeature.setParent(null);
        oldFeature.setChildren(new ArrayList<>());
        return true;
    }
}
