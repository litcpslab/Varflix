package variabilityMining;

import java.util.*;
import java.util.stream.Collectors;

import UVL.UVLGenerator;
import constraints.*;

public class PermutationGenerator {

    public static int PERMUTATION_LIMIT = 10;
    private int altCount = 0;
    int orCount = 0;

    public List<TreePermutation> generatePermutations(Feature base, List<Feature> originalFeatures, List<Constraint> constraints) {
        List<TreePermutation> results = new ArrayList<>();

        List<Feature> cleanFeatures = new ArrayList<>(originalFeatures);
        cleanFeatures.removeIf(f -> f.getName().startsWith("OR") || f.getName().startsWith("ALT"));
        cleanFeatures.forEach(f -> {
            f.setChildren(new ArrayList<>());
            f.setParent(null);
            f.setAlternativeParent(false);
            f.setOrParent(false);
        });

        Feature root = base == null ? new Feature("Base") : base;
        root.setMandatory(true);
        if (!cleanFeatures.contains(root)) cleanFeatures.add(root);

        List<Group> groupConstraints = constraints.stream()
                .filter(c -> c instanceof Group)
                .map(c -> (Group) c).collect(Collectors.toList());
        List<SimpleConstraint> simpleConstraints = constraints.stream()
                .filter(c -> c instanceof SimpleConstraint)
                .map(c -> (SimpleConstraint) c).collect(Collectors.toList());

        TreePermutation baseState = new TreePermutation(root, cleanFeatures, new ArrayList<>());
        applyDeterministicRules(baseState, simpleConstraints, groupConstraints);

        List<Feature> orphans = baseState.getFeatures().stream()
                .filter(f -> f.getParent() == null && !f.equals(baseState.getRoot()))
                .collect(Collectors.toList());

        branchAndPermute(baseState, orphans, 0, simpleConstraints, groupConstraints, results);

        return results;
    }

    private void applyDeterministicRules(TreePermutation state, List<SimpleConstraint> simpleConstraints, List<Group> groups) {
        List<SimpleConstraint> baseEquivs = simpleConstraints.stream()
                .filter(c -> c.getType().equals("Equivalence"))
                .filter(c -> c.getFeature1().getName().equals(state.getRoot().getName()) ||
                        c.getFeature2().getName().equals(state.getRoot().getName()))
                .toList();

        for (SimpleConstraint c : baseEquivs) {
            String otherName = c.getFeature1().getName().equals(state.getRoot().getName()) ?
                    c.getFeature2().getName() : c.getFeature1().getName();

            // Fetch the new feature from the current state
            Feature other = getFeatureByName(state.getFeatures(), otherName);
            if (other != null) {
                other.setParent(state.getRoot());
                other.setMandatory(true);
                state.getRoot().addChild(other);
            }
        }

        buildGroups(state.getRoot(), groups, state.getFeatures());
    }

    private void buildGroups(Feature base, List<Group> groups, List<Feature> features) {
        orCount = 0;
        altCount = 0;

        Map<Feature, List<Group>> groupMap = new TreeMap<>();

        // Group by parent, but ensure we use the new parent from the features list
        for (Group group : groups) {
            Feature freshParent = getFeatureByName(features, group.getParent().getName());
            if (freshParent != null) {
                groupMap.computeIfAbsent(freshParent, k -> new ArrayList<>()).add(group);
            }
        }

        for (Feature parent : groupMap.keySet()) {
            List<Group> childGroups = groupMap.get(parent);

            for (Group group : childGroups) {
                group.getFeatures().sort((f1, f2) -> f1.getName().compareTo(f2.getName()));
            }
            childGroups.sort((g1, g2) -> g1.getFeatures().get(0).getName().compareTo(g2.getFeatures().get(0).getName()));

            if (childGroups.size() > 1) {
                for (Group group : childGroups) {
                    boolean isAlt = group.getType().equals("Alternative");
                    String parentName = isAlt ? "ALT" + (++altCount) : "OR" + (++orCount);

                    Feature dummyParent = getFeatureByName(features, parentName);
                    if (dummyParent == null) {
                        dummyParent = new Feature(parentName, parent);
                        features.add(dummyParent);
                    } else {
                        dummyParent.setParent(parent);
                    }

                    parent.addChild(dummyParent);
                    dummyParent.setMandatory(true);
                    dummyParent.setAlternativeParent(isAlt);
                    dummyParent.setOrParent(!isAlt);

                    // Fetch the new children by name
                    List<Feature> groupChildren = new ArrayList<>();
                    for (Feature f : group.getFeatures()) {
                        Feature freshChild = getFeatureByName(features, f.getName());
                        if (freshChild != null) {
                            freshChild.setParent(dummyParent);
                            groupChildren.add(freshChild);
                        }
                    }
                    groupChildren.sort((c1, c2) -> c1.getName().compareTo(c2.getName()));
                    dummyParent.setChildren(groupChildren);
                }
            } else {
                Group group = childGroups.get(0);
                if (group.getType().equals("Alternative")) parent.setAlternativeParent(true);
                if (group.getType().equals("Or Group")) parent.setOrParent(true);

                List<Feature> groupChildren = new ArrayList<>();
                for (Feature f : group.getFeatures()) {
                    Feature freshChild = getFeatureByName(features, f.getName());
                    if (freshChild != null) {
                        freshChild.setParent(parent);
                        groupChildren.add(freshChild);
                    }
                }
                groupChildren.sort((c1, c2) -> c1.getName().compareTo(c2.getName()));
                parent.addChildren(groupChildren);
            }
        }
    }

    private void branchAndPermute(TreePermutation currentState, List<Feature> orphans, int orphanIndex,
                                  List<SimpleConstraint> allSimpleConstraints, List<Group> groupConstraints, List<TreePermutation> results) {

        if (results.size() >= PERMUTATION_LIMIT) {
            return;
        }


        if (orphanIndex >= orphans.size()) {
            currentState.setCtcs(computeCTCs(currentState, clonedSimpleConstraints(allSimpleConstraints, currentState.getFeatures())));
            currentState.setGroups(cloneGroups(groupConstraints, currentState.getFeatures()));
            results.add(currentState);
            return;
        }

        Feature currentOrphan = orphans.get(orphanIndex);

        List<Feature> candidateParents = findCandidateParents(currentOrphan, currentState.getFeatures(), allSimpleConstraints);

        if (candidateParents.isEmpty()) {
            candidateParents.add(currentState.getRoot());
        }

        for (Feature candidateParent : candidateParents) {
            TreePermutation nextState = currentState.deepClone();

            Feature clonedOrphan = getFeatureByName(nextState.getFeatures(), currentOrphan.getName());
            Feature clonedParent = getFeatureByName(nextState.getFeatures(), candidateParent.getName());

            clonedParent.addChild(clonedOrphan);
            clonedOrphan.setParent(clonedParent);

            branchAndPermute(nextState, orphans, orphanIndex + 1, allSimpleConstraints, groupConstraints, results);
        }
    }

    private List<Feature> findCandidateParents(Feature orphan, List<Feature> currentFeatures, List<SimpleConstraint> constraints) {
        List<Feature> candidates = new ArrayList<>();

        List<SimpleConstraint> implications = constraints.stream()
                .filter(c -> c.getType().equals("Implication"))
                .filter(c -> c.getFeature1().getName().equals(orphan.getName()))
                .toList();

        for (SimpleConstraint impl : implications) {
            Feature potentialParent = getFeatureByName(currentFeatures, impl.getFeature2().getName());
            if (potentialParent != null && !potentialParent.isAlternativeParent() && !potentialParent.isOrParent()) {
                candidates.add(potentialParent);
            }
        }

        return candidates;
    }

    private List<Group> cloneGroups(List<Group> template, List<Feature> features) {
        List<Group> result = new ArrayList<>();
        for (Group group : template) {
            List<Feature> members = new ArrayList<>();
            for (Feature member : group.getFeatures()) {
                Feature fresh = getFeatureByName(features, member.getName());
                if (fresh != null) members.add(fresh);
            }
            if (group.getParent() == null) continue;
            Feature parent = getFeatureByName(features, group.getParent().getName());
            if (parent == null) continue;
            if (group instanceof AlternativeGroup) {
                result.add(new AlternativeGroup(members, parent));
            } else if (group instanceof OrRelation) {
                result.add(new OrRelation(members, parent));
            }
        }
        return result;
    }

    private List<SimpleConstraint> clonedSimpleConstraints(List<SimpleConstraint> template, List<Feature> features) {
        List<SimpleConstraint> result = new ArrayList<>();
        for (SimpleConstraint constraint : template) {
            Feature f1 = getFeatureByName(features, constraint.getFeature1().getName());
            Feature f2 = getFeatureByName(features, constraint.getFeature2().getName());
            if(constraint instanceof Equivalence) {
                result.add(new Equivalence(f1, f2));
            } else if(constraint instanceof Implication) {
                result.add(new Implication(f1, f2));
            } else if(constraint instanceof MutualExclusion) {
                result.add(new MutualExclusion(f1, f2));
            } else {
                result.add(new SimpleConstraint(f1, f2, constraint.getType()));
            }
        }
        return result;
    }

    public static List<SimpleConstraint> computeCTCs(TreePermutation state, List<SimpleConstraint> allConstraints) {
        List<SimpleConstraint> ctcs = new ArrayList<>();
        for (SimpleConstraint c : allConstraints) {
            if (c.getType().equals("Implication")) {
                Feature f1 = getFeatureByName(state.getFeatures(), c.getFeature1().getName());
                Feature f2 = getFeatureByName(state.getFeatures(), c.getFeature2().getName());
//                if (f1.getParent() == null || !f1.getParent().equals(f2)) {
                if (f1.getParent() == null || (!f1.getParent().equals(f2) && !isAncestor(f1, f2))) {
                    ctcs.add(c);
                }
            } else {
                ctcs.add(c);
            }
        }
        return ctcs;
    }

    private static boolean isAncestor(Feature child, Feature potentialAncestor) {
        Feature current = child.getParent();
        while(current != null) {
            if (current.equals(potentialAncestor)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static Feature getFeatureByName(List<Feature> features, String name) {
        return features.stream().filter(f -> f.getName().equals(name)).findFirst().orElse(null);
    }

    // debug methods:
    public void dumpPermutationsToFile(List<TreePermutation> permutations, String filePath) {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(new java.io.File(filePath)))) {

            writer.println("\n===========================================\n");

            for (int i = 0; i < permutations.size(); i++) {
                TreePermutation perm = permutations.get(i);
                writer.println("MODEL " + (i + 1) + "\n");

                writer.println("UVL:");
                UVLGenerator.writeUVLModel(perm.getRoot(), perm.getCtcs(), writer);

                writer.println("\n===========================================\n");
            }
            System.out.println("Successfully wrote " + permutations.size() + " permutations to: " + filePath);
        } catch (java.io.IOException e) {
            System.err.println("Failed to write permutations to file: " + e.getMessage());
        }
    }
}