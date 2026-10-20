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

    public boolean removeConstraint(Constraint constraint) {
        if (constraint instanceof SimpleConstraint sc) {
            return ctcs.remove(sc);
        }
        if (constraint instanceof Group g) {
            return removeGroup(g);
        }
        return false;
    }

    public boolean addSimpleConstraint(SimpleConstraint constraint) {
        if (constraint == null) return false;
        Feature f1 = constraint.getFeature1();
        Feature f2 = constraint.getFeature2();
        if (f1 == null || f2 == null) return false;
        if (f1.equals(f2)) return false;
        if (!featuresContains(f1) || !featuresContains(f2)) return false;

        for (Constraint c : getAllConstraints()) {
            if (c instanceof SimpleConstraint sc) {
                boolean samePair = (sc.getFeature1().equals(f1) && sc.getFeature2().equals(f2))
                        || (sc.getFeature1().equals(f2) && sc.getFeature2().equals(f1));
                if (samePair) {
                    if (!sc.getType().equals("Implication") || !constraint.getType().equals("Equivalence")) {
                        return false;
                    }
                }
            }
        }

        ctcs.add(constraint);
        return true;
    }

    public List<SimpleConstraint> resolveGroupConstraintsForFeature(Feature feature, Group group) {
        List<SimpleConstraint> resolved = new ArrayList<>();
        if (feature == null || group == null) {
            return resolved;
        }

        if (group instanceof AlternativeGroup) {
            for (Feature sibling : group.getFeatures()) {
                if (!sibling.equals(feature)) {
                    resolved.add(new MutualExclusion(feature, sibling));
                }
            }
        }

        Feature formerParent = group.getParent();
        if (formerParent != null && !formerParent.equals(root)) {
            resolved.add(new Implication(feature, formerParent));
        }

        return resolved;
    }

    public boolean removeGroup(Group group) {
        return removeGroup(group, null, true);
    }

    public boolean keepGroupConstraints(Group group, List<SimpleConstraint> dissolved) {
        return removeGroup(group, dissolved, false);
    }

    public boolean removeFeatureFromGroup(Feature feature, Group group) {
        return removeFeatureFromGroup(feature, group, false);
    }

    public boolean removeFeatureFromGroupAndReposition(Feature feature, Group group) {
        return removeFeatureFromGroup(feature, group, true);
    }

    private boolean removeFeatureFromGroup(Feature feature, Group group, boolean reposition) {
        if (feature == null || group == null) return false;
        if (!groups.contains(group)) return false;
        if (!containsByName(group.getFeatures(), feature)) return false;

        Feature groupParentBeforeRemoval = group.getParent();
        List<Feature> remainingMembers = new ArrayList<>(group.getFeatures());
        remainingMembers.removeIf(f -> f.equals(feature));

        group.getFeatures().removeIf(f -> f.equals(feature));
        if (feature.getParent() != null && feature.getParent().getChildren() != null) {
            feature.getParent().getChildren().removeIf(f -> f.equals(feature));
        }
        feature.setParent(null);

        if (group.getFeatures().size() <= 1) {
            groups.remove(group);
            if (group.getFeatures().size() == 1) {
                Feature survivor = group.getFeatures().get(0);
                survivor.setMandatory(true);
                Feature groupParent = survivor.getParent();
                if (groupParent != null) {
                    if (group instanceof AlternativeGroup) {
                        groupParent.setAlternativeParent(false);
                    } else {
                        groupParent.setOrParent(false);
                    }
                    if (isSyntheticFeature(groupParent)) {
                        Feature grandParent = groupParent.getParent();
                        if (grandParent != null) {
                            groupParent.getChildren().removeIf(f -> f.equals(survivor));
                            link(survivor, grandParent);
                        }
                    }
                }
            }
        }

        if (reposition) {
            Feature fallback = findPlacementFallback(groupParentBeforeRemoval);
            List<Feature> avoidedParents = new ArrayList<>(remainingMembers);
            if (groupParentBeforeRemoval != null) {
                avoidedParents.add(groupParentBeforeRemoval);
            }
            placeFeature(feature, avoidedParents, false, fallback);
            if (feature.getParent() == null) {
                placeNextToGroup(feature, groupParentBeforeRemoval);
            }
        }

        pruneSyntheticFeature();
        normalizeGroups();
        recomputeCtcs();
        return true;
    }

    private Feature findPlacementFallback(Feature groupParent) {
        Feature fallback = unwrapSyntheticFeature(groupParent);
        if (fallback == null) {
            return root;
        }
        Feature candidate = fallback;
        while (candidate.getParent() != null && findGroupWithParent(candidate) != null) {
            candidate = candidate.getParent();
        }
        return candidate;
    }

    private void placeNextToGroup(Feature feature, Feature groupParent) {
        Feature parent;
        if (groupParent == null) {
            parent = root;
        } else {
            parent = groupParent;
        }
        Group groupAtParent = findGroupWithParent(parent);

        if (groupAtParent != null) {
            boolean isAlt = groupAtParent instanceof AlternativeGroup;
            Feature dummy = createGroupDummy(parent, isAlt);
            features.add(dummy);
            link(dummy, parent);

            for (Feature member : new ArrayList<>(groupAtParent.getFeatures())) {
                link(member, dummy);
            }
            groupAtParent.setParent(dummy);
            parent.setAlternativeParent(false);
            parent.setOrParent(false);
        }

        link(feature, parent);
    }

    public boolean addFeatureToGroup(Feature feature, Group group) {
        if (feature == null || group == null) return false;
        if (!featuresContains(feature)) return false;
        if (!groups.contains(group)) return false;
        if (containsByName(group.getFeatures(), feature)) return false;

        Feature groupParent = group.getParent();
        Feature syntheticParent = null;

        //check synthetic parent in between
        if (!group.getFeatures().isEmpty() && !group.getFeatures().get(0).getParent().equals(groupParent)) {
            syntheticParent = group.getFeatures().get(0).getParent();
        }
        Feature oldParent = feature.getParent();
        if (oldParent != null && oldParent.getChildren().contains(feature)) {
            oldParent.getChildren().removeIf(f -> f.equals(feature));
        }
        feature.setParent(null);

        Feature attachTo = groupParent;
        if(syntheticParent != null){
            attachTo = syntheticParent;
        }

        if (attachTo != null && featuresContains(attachTo)) {
            link(feature, attachTo);
        } else if (root != null) {
            link(feature, root);
        }

        group.getFeatures().add(feature);
        pruneSyntheticFeature();
        normalizeGroups();
        recomputeCtcs();
        return true;
    }

    private boolean removeGroup(Group group, List<SimpleConstraint> dissolved, boolean reParent) {
        if (group == null || !groups.contains(group)) return false;

        Feature groupParent = group.getFeatures().get(0).getParent();
        List<Feature> members = new ArrayList<>(group.getFeatures());
        boolean isAlt = group instanceof AlternativeGroup;

        groups.remove(group);

        if (reParent && isAlt && members.size() > 1) {
            addMemberMutexes(members);
        }

        if (dissolved != null) {
            for (SimpleConstraint constraint : dissolved) {
                if (constraint.getFeature1().equals(constraint.getFeature2())) continue;
                if (!ctcs.contains(constraint)) {
                    ctcs.add(constraint);
                }
            }
        }

        Feature anchor = unwrapSyntheticFeature(groupParent);

        if (anchor != null && findGroupWithParent(anchor) == null) {
            if (isAlt) anchor.setAlternativeParent(false);
            else anchor.setOrParent(false);
        }
        if (groupParent != null && !groupParent.equals(anchor) && findGroupWithParent(groupParent) == null) {
            if (isAlt) groupParent.setAlternativeParent(false);
            else groupParent.setOrParent(false);
        }

        if (reParent && members.size() > 1) {
            Feature fallback;
            if(anchor == null) {
                fallback = root;
            } else {
                fallback = anchor;
            }
            for (Feature member : members) {
                placeFeature(member, members, false, fallback);
            }
        }

        pruneSyntheticFeature();
        normalizeGroups();

        if (dissolved == null) {
            recomputeCtcs();
        } else {
            ctcs.removeIf(c -> !featuresContains(c.getFeature1()) || !featuresContains(c.getFeature2()));
        }

        return true;
    }

    private void addMemberMutexes(List<Feature> members) {
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                Feature first = members.get(i);
                Feature second = members.get(j);
                if (first.equals(second)) continue;
                if (getFeatureAncestors(first).contains(second) || getFeatureAncestors(second).contains(first)) {
                    continue;
                }
                MutualExclusion mutex = new MutualExclusion(first, second);
                if (!ctcs.contains(mutex)) {
                    ctcs.add(mutex);
                }
            }
        }
    }

    private void pruneSyntheticFeature() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Feature feature : new ArrayList<>(features)) {
                if (!isSyntheticFeature(feature)) continue;

                Group group;
                if (feature.getParent() == root) {
                    group = findGroupWithParent(root);
                    if (group != null && !feature.getChildren().isEmpty()) continue;
                } else {
                    group = findGroupWithParent(feature);
                    if (group != null) continue;
                }

                Feature parent = feature.getParent();
                if (parent == null) continue;

                List<Feature> children = new ArrayList<>(feature.getChildren());
                for (Feature child : children) {
                    link(child, parent);
                }
                if (parent.getChildren() != null) {
                    parent.getChildren().removeIf(f -> f.equals(feature));
                }
                features.removeIf(f -> f.equals(feature));
                feature.setAlternativeParent(false);
                feature.setOrParent(false);
                feature.setParent(null);

                if (findGroupWithParent(parent) == null) {
                    parent.setAlternativeParent(false);
                    parent.setOrParent(false);
                }
                changed = true;
            }
        }
    }

    private Feature unwrapSyntheticFeature(Feature feature) {
        Feature current = feature;
        while (current != null && isSyntheticFeature(current) && current.getParent() != null) {
            current = current.getParent();
        }
        return current;
    }

    private boolean isSyntheticFeature(Feature feature) {
        if (feature == null || feature.getName() == null) return false;
        return feature.getName().startsWith("ALT") || feature.getName().startsWith("OR");
    }

    // Feature removal
    public boolean removeFeatureByName(String name) {
        Feature feature = getFeatureByName(name);
        if (feature == null) return false;
        return removeFeature(feature);
    }

    public boolean removeFeature(Feature feature) {
        if (feature == null || feature.equals(root)) return false;
        Feature parent = feature.getParent();
        if (parent == null) return false;

        List<Feature> children = new ArrayList<>(feature.getChildren());

        Group groupAsChild = findGroupContaining(feature);
        Group groupAsParent = findGroupWithParent(feature);

        replaceSimpleConstraints(feature, children);

        if (groupAsChild != null && (feature.isAlternativeParent() || feature.isOrParent())) {
            // feature is group parent and group children
            handleGroupMemberRemoval(feature, parent, children, groupAsChild);
        } else if (groupAsParent != null) {
            // feature is group children
            Feature newParent = findParentForGroup(groupAsParent, parent);
            attachGroup(groupAsParent, newParent);
        } else if (groupAsChild != null) {
            // feature is group parent
            groupAsChild.getFeatures().removeIf(f -> f.equals(feature));
            List<Feature> siblings = new ArrayList<>(groupAsChild.getFeatures());
            boolean siblingsAllowed = groupAsChild instanceof OrRelation;
            for (Feature child : children) {
                placeFeature(child, siblings, siblingsAllowed, parent);
            }
        } else {
            // feature without group and ctc
            for (Feature child : children) {
                placeFeature(child, children, false, parent);
            }
        }

        // detach feature from tree
        parent.getChildren().removeIf(f -> f.equals(feature));
        feature.setParent(null);
        feature.setChildren(new ArrayList<>());
        features.removeIf(f -> f.equals(feature));

        normalizeGroups();

        recomputeCtcs();

        return true;
    }

    private void handleGroupMemberRemoval(Feature feature, Feature parent, List<Feature> children, Group groupAsChild) {
        boolean isAltGroup = groupAsChild instanceof AlternativeGroup;
        boolean headsAlt = feature.isAlternativeParent();
        boolean headsOr = feature.isOrParent();
        Group ownGroup = findGroupWithParent(feature);

        if ((headsAlt && isAltGroup) || (headsOr && !isAltGroup)) {
            // merge same group types to parent
            for (Feature child : children) {
                link(child, parent);
                if (!groupAsChild.getFeatures().contains(child)) {
                    groupAsChild.getFeatures().add(child);
                }
            }
            if (ownGroup != null) {
                groups.remove(ownGroup);
            }
        } else {
            // substitute with dummy
            Feature dummy = createGroupDummy(parent, headsAlt);
            features.add(dummy);
            link(dummy, parent);
            replaceGroupMember(groupAsChild, feature, dummy);

            if (ownGroup != null) {
                groups.remove(ownGroup);
            }
            if(headsAlt) {
                new AlternativeGroup(new ArrayList<>(children), dummy);
            } else {
                new OrRelation(new ArrayList<>(children), dummy);
            }

            for (Feature child : children) {
                link(child, dummy);
            }
        }

    }

    private Feature findParentForGroup(Group group, Feature fallback) {
        List<Feature> members = new ArrayList<>(group.getFeatures());
        for (Feature member : members) {
            for (Feature candidate : findParentCandidates(member)) {
                if (members.contains(candidate) || candidate.equals(root)) continue;
                boolean cycle = false;
                for (Feature m : members) {
                    if (getFeatureAncestors(candidate).contains(m)) {
                        cycle = true;
                        break;
                    }
                }
                if (!cycle) {
                    return candidate;
                }
            }
        }
        if (fallback != null && !members.contains(fallback)) {
            return fallback;
        }
        return root;
    }

    private void attachGroup(Group group, Feature newParent) {
        if (newParent == null) newParent = root;
        boolean isAlt = group instanceof AlternativeGroup;
        List<Feature> members = new ArrayList<>(group.getFeatures());

        Group existing = findGroupWithParent(newParent);
        if (existing != null && sameGroupType(existing, group)) {
            for (Feature member : members) {
                link(member, newParent);
                if (!existing.getFeatures().contains(member)) {
                    existing.getFeatures().add(member);
                }
            }
            groups.remove(group);
            return;
        }

        boolean hasChildren = newParent.getChildren() != null && !newParent.getChildren().isEmpty();
        boolean isGroupParent = newParent.isAlternativeParent() || newParent.isOrParent();

        if (!hasChildren && !isGroupParent) {
            // The new parent becomes the group parent directly
            group.setParent(newParent);
            if (isAlt) newParent.setAlternativeParent(true);
            else newParent.setOrParent(true);
            for (Feature member : members) {
                link(member, newParent);
            }
        } else {
            //nest the group under an artificial group node
            Feature dummy = createGroupDummy(newParent, isAlt);
            features.add(dummy);
            link(dummy, newParent);
            group.setParent(dummy);
            for (Feature member : members) {
                link(member, dummy);
            }
        }
    }

    // find new parent
    private void placeFeature(Feature child, List<Feature> siblings, boolean siblingsAllowed, Feature fallback) {
        for (Feature candidate : findParentCandidates(child)) {
            if (candidate.equals(child)) continue;
            if (containsByName(siblings, candidate) && !siblingsAllowed) continue;
            if (attachFeature(child, candidate) != null) return;
        }
        if (fallback != null && !fallback.equals(child) && attachFeature(child, fallback) != null) return;
        if (!root.equals(child)) {
            attachFeature(child, root);
        }
    }

    private Feature attachFeature(Feature feature, Feature newParent) {
        if (feature == null || newParent == null || feature.equals(newParent)) return null;
        if (getFeatureAncestors(newParent).contains(feature)) return null;

        if (newParent.isAlternativeParent()) {
            List<Feature> members = membersOf(newParent);
            if (!members.isEmpty() && !hasMutexToAll(feature, members)) {
                return null;
            }
            Group group = findGroupWithParent(newParent);
            if (group != null && !group.getFeatures().contains(feature)) {
                group.getFeatures().add(feature);
            }
            removeMutexesTo(feature, members);
            link(feature, newParent);
            return newParent;
        }

        if (newParent.isOrParent()) {
            if (!feature.isOptional()) return null;
            Group group = findGroupWithParent(newParent);
            if (group != null && !group.getFeatures().contains(feature)) {
                group.getFeatures().add(feature);
            }
            link(feature, newParent);
            return newParent;
        }

        // Plain parent
        if (newParent.equals(root)) {
            link(feature, newParent);
            return newParent;
        }

        if (newParent.isOptional() && newParent.getChildren() != null && newParent.getChildren().size() == 1
                && feature.isOptional() && !newParent.getChildren().contains(feature)) {
            Feature existing = newParent.getChildren().get(0);
            if (existing.isOptional()) {
                List<Feature> orMembers = new ArrayList<>(newParent.getChildren());
                orMembers.add(feature);
                groups.add(new OrRelation(orMembers, newParent));
                newParent.setOrParent(true);
                link(feature, newParent);
                return newParent;
            }
        }



        link(feature, newParent);
        return newParent;
    }

    private List<Feature> findParentCandidates(Feature feature) {
        List<Feature> candidates = new ArrayList<>();
        for (SimpleConstraint ctc : ctcs) {
            if (ctc instanceof Implication && ctc.getFeature1().equals(feature)) {
                Feature candidate = ctc.getFeature2();
                if (candidate != null && !candidates.contains(candidate)) {
                    candidates.add(candidate);
                }
            }
        }

        List<Feature> reduced = new ArrayList<>();
        for (Feature c1 : candidates) {
            boolean ancestorOfOther = false;
            for (Feature c2 : candidates) {
                if (!c1.equals(c2) && getFeatureAncestors(c2).contains(c1)) {
                    ancestorOfOther = true;
                    break;
                }
            }
            if (!ancestorOfOther) {
                reduced.add(c1);
            }
        }
        return reduced;
    }

    // reference children of simple constraints of deleted feature
    private void replaceSimpleConstraints(Feature removed, List<Feature> children) {
        List<SimpleConstraint> kept = new ArrayList<>();
        List<SimpleConstraint> additions = new ArrayList<>();

        for (SimpleConstraint ctc : ctcs) {
            boolean firstRemoved = ctc.getFeature1().equals(removed);
            boolean secondRemoved = ctc.getFeature2().equals(removed);

            if (!firstRemoved && !secondRemoved) {
                kept.add(ctc);
                continue;
            }

            // link single constraints to all its children
            for (Feature child : children) {
                if (ctc instanceof Equivalence) {
                    if (firstRemoved) {
                        addReplacement(additions, new Equivalence(child, ctc.getFeature2()));
                    } else {
                        addReplacement(additions, new Equivalence(ctc.getFeature1(), child));
                    }
                } else if (ctc instanceof MutualExclusion) {
                    if(firstRemoved) {
                        addReplacement(additions, new MutualExclusion(child, ctc.getFeature2()));
                    } else {
                        addReplacement(additions, new MutualExclusion(child, ctc.getFeature1()));
                    }
                } else if (ctc instanceof Implication) {
                    if (firstRemoved) {
                        addReplacement(additions, new Implication(child, ctc.getFeature2()));
                    } else {
                        addReplacement(additions, new Implication(ctc.getFeature1(), child));
                    }
                } else {
                    if(firstRemoved) {
                        addReplacement(additions, new SimpleConstraint(child, ctc.getFeature2(), ctc.getType()));
                    } else {
                        addReplacement(additions, new SimpleConstraint(ctc.getFeature1(), child, ctc.getType()));
                    }
                }
            }
        }

        this.ctcs = kept;
        for (SimpleConstraint addition : additions) {
            if (!ctcs.contains(addition)) {
                ctcs.add(addition);
            }
        }
    }

    private void addReplacement(List<SimpleConstraint> replacements, SimpleConstraint constraint) {
        if (constraint.getFeature1().equals(constraint.getFeature2())) return;
        if (!replacements.contains(constraint)) {
            replacements.add(constraint);
        }
    }

    private void recomputeCtcs() {
        this.ctcs.removeIf(ctc -> !featuresContains(ctc.getFeature1()) || !featuresContains(ctc.getFeature2()));
        this.ctcs = PermutationGenerator.computeCTCs(this, this.ctcs);
    }

    private void normalizeGroups() {
        List<Group> collapsed = new ArrayList<>();
        for (Group group : groups) {
            group.getFeatures().removeIf(f -> !featuresContains(f));
            if (group.getFeatures().size() <= 1) {
                collapsed.add(group);
            }
        }

        for (Group group : collapsed) {
            groups.remove(group);
            if (group.getFeatures().size() == 1) {
                Feature survivor = group.getFeatures().get(0);
                survivor.setMandatory(true);
                Feature groupParent = survivor.getParent();
                if (groupParent != null) {
                    if (group instanceof AlternativeGroup) {
                        groupParent.setAlternativeParent(false);
                    } else {
                        groupParent.setOrParent(false);
                    }
                    if(groupParent.getName().startsWith("ALT") || groupParent.getName().startsWith("OR")) {
                        Feature grandParent = groupParent.getParent();
                        survivor.setParent(grandParent);
                        grandParent.getChildren().replaceAll(f -> f.equals(groupParent) ? survivor : f);
                        group.getFeatures().replaceAll(f -> f.equals(survivor) ? survivor : f);
                        for (SimpleConstraint ctc : ctcs) {
                            if(ctc.getFeature1().equals(groupParent)) {
                                ctc.setFeature1(survivor);
                            } else if(ctc.getFeature2().equals(groupParent)) {
                                ctc.setFeature2(survivor);
                            }
                        }
                    }
                }
            }
        }
    }

    private Group findGroupContaining(Feature feature) {
        return groups.stream()
                .filter(group -> containsByName(group.getFeatures(), feature))
                .findFirst()
                .orElse(null);
    }

    private Group findGroupWithParent(Feature feature) {
        return groups.stream()
                .filter(group -> feature.equals(group.getParent()))
                .findFirst()
                .orElse(null);
    }

    private boolean sameGroupType(Group first, Group second) {
        return (first instanceof AlternativeGroup) == (second instanceof AlternativeGroup);
    }

    private List<Feature> membersOf(Feature parent) {
        Group group = findGroupWithParent(parent);
        if (group != null) {
            return new ArrayList<>(group.getFeatures());
        }
        return parent.getChildren() == null ? new ArrayList<>() : new ArrayList<>(parent.getChildren());
    }

    private void replaceGroupMember(Group group, Feature oldFeature, Feature newFeature) {
        List<Feature> members = group.getFeatures();
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).equals(oldFeature)) {
                members.set(i, newFeature);
            }
        }
    }

    private Feature createGroupDummy(Feature parent, boolean isAlt) {
        String prefix = isAlt ? "ALT" : "OR";
        int index = 1;
        String name;
        do {
            name = prefix + index++;
        } while (getFeatureByName(name) != null);

        Feature dummy = new Feature(name);
        dummy.setMandatory(true);
        dummy.setAlternativeParent(isAlt);
        dummy.setOrParent(!isAlt);
        dummy.setChildren(new ArrayList<>());
        return dummy;
    }

    private void link(Feature feature, Feature parent) {
        Feature oldParent = feature.getParent();
        if (oldParent != null && oldParent.getChildren() != null && !oldParent.equals(parent)) {
            oldParent.getChildren().removeIf(f -> f.equals(feature));
        }
        feature.setParent(parent);
        if (parent.getChildren() == null) {
            parent.setChildren(new ArrayList<>());
        }
        if (!parent.getChildren().contains(feature)) {
            parent.addChild(feature);
        }
    }

    private boolean hasMutexToAll(Feature feature, List<Feature> members) {
        for (Feature member : members) {
            boolean mutex = ctcs.stream().anyMatch(ctc -> ctc instanceof MutualExclusion
                    && ((ctc.getFeature1().equals(feature) && ctc.getFeature2().equals(member))
                    || (ctc.getFeature2().equals(feature) && ctc.getFeature1().equals(member))));
            if (!mutex) return false;
        }
        return true;
    }

    private void removeMutexesTo(Feature feature, List<Feature> members) {
        ctcs.removeIf(ctc -> ctc instanceof MutualExclusion
                && ((ctc.getFeature1().equals(feature) && containsByName(members, ctc.getFeature2()))
                || (ctc.getFeature2().equals(feature) && containsByName(members, ctc.getFeature1()))));
    }

    private List<Feature> getFeatureAncestors(Feature feature) {
        List<Feature> ancestors = new ArrayList<>();
        Feature current = feature.getParent();
        while (current != null) {
            ancestors.add(current);
            current = current.getParent();
        }
        return ancestors;
    }

    private boolean featuresContains(Feature feature) {
        return containsByName(features, feature);
    }

    private boolean containsByName(List<Feature> list, Feature feature) {
        if (list == null) return false;
        return list.stream().anyMatch(f -> f.equals(feature));
    }
}
