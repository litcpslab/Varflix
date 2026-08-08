package variabilityMining;

import constraints.*;
import fr.lirmm.fca4j.core.IBinaryContext;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GroupPermutationGenerator {
    public static int PERMUTATION_LIMIT = 1000;
    private final Feature base;
    private final List<Constraint> constraints;
    private final List<Feature> features;

    private final IBinaryContext context;

    private Map<Feature, Set<Feature>> implicationMap;

    public GroupPermutationGenerator(IBinaryContext context, Feature base, List<Feature> features, List<Constraint> constraints) {
        this.context = context;
        this.base = base;
        this.features = features;
        this.constraints = constraints;
    }

    public List<TreePermutation> generateConstraintPermutations() {
        implicationMap = buildImplicationMap(constraints);
        TreePermutation baseState = new TreePermutation(base, features, constraints.stream().filter(c -> !(c instanceof Group)).map(c -> (SimpleConstraint) c).collect(Collectors.toList()));
        List<TreePermutation> permutations = new ArrayList<>();
        List<MutualExclusion> availableMutex = new ArrayList<>(filterConstraintType(baseState.getCtcs(), MutualExclusion.class).toList());

        List<AlternativeGroup> initialClusters = getAlternativeGroupClusters(base, baseState.getFeatures(), availableMutex);
        initialClusters = splitClustersByParents(initialClusters);
        initialClusters = new ArrayList<>(initialClusters.stream().distinct().toList());
        initialClusters.sort(Comparator.comparing(g -> g.getFeatures().get(0).getName()));

        buildAlternativeGroups(permutations, baseState, initialClusters);
        findOrGroups(permutations);
        filterRedundantConstraintsPermutation(permutations);

        return permutations;
    }

    private Map<Feature, Set<Feature>> buildImplicationMap(List<Constraint> constraints) {
        Map<Feature, Set<Feature>> implicationMap = new HashMap<>();
        List<Implication> implications = filterConstraintType(constraints, Implication.class).toList();
        for(Implication implication: implications) {
            if(!implicationMap.containsKey(implication.getFeature2())) {
                implicationMap.put(implication.getFeature2(), new HashSet<>());
            }
            implicationMap.get(implication.getFeature2()).add(implication.getFeature1());
        }

        return implicationMap;
    }

    private <T> Stream<T> filterConstraintType(Collection<? extends Constraint> constraints, Class<T> type) {
        return constraints.stream()
                .filter(type::isInstance)
                .map(type::cast);
    }

    private void buildAlternativeGroups(List<TreePermutation> treePermutations, TreePermutation state, List<AlternativeGroup> remainingGroups) {
        if (treePermutations.size() >= PERMUTATION_LIMIT) return;

        // no validGroups
        List<AlternativeGroup> validGroups = remainingGroups.stream()
                .filter(g -> g.getFeatures().size() >= 3)
                .toList();
        if (validGroups.isEmpty()) {
            treePermutations.add(state);
            return;
        }

        AlternativeGroup targetGroup = validGroups.get(0);
        List<AlternativeGroup> nextGroups = validGroups.subList(1, validGroups.size());

        // deep clone state and add the target group it
        TreePermutation includeState = state.deepClone();
        includeState.getGroups().add(targetGroup);

        // remove covered mutex
        List<Feature> altFeatures = targetGroup.getFeatures();
        String parentName = targetGroup.getParent() != null ? targetGroup.getParent().getName() : "";

        // remove covered simple constraints
        includeState.getCtcs().removeIf(c -> {
            if (c instanceof MutualExclusion) {
                // remove Mutex if both features are inside this ALT group
                return altFeatures.stream().anyMatch(f -> f.getName().equals(c.getFeature1().getName()))
                        && altFeatures.stream().anyMatch(f -> f.getName().equals(c.getFeature2().getName()));
            } else if (c instanceof Implication) {
                // remove implication if it is from a child in this ALT group to the Parent of this ALT group
                return altFeatures.stream().anyMatch(f -> f.getName().equals(c.getFeature1().getName()))
                        && c.getFeature2().getName().equals(parentName);
            }
            return false;
        });

        // subtract used features from future groups
        List<AlternativeGroup> mutatedNextGroups = new ArrayList<>();
        for (AlternativeGroup futureGroup : nextGroups) {
            AlternativeGroup clonedFutureGroup = new AlternativeGroup(
                    new ArrayList<>(futureGroup.getFeatures()),
                    futureGroup.getParent()
            );
            for (Feature usedFeature : targetGroup.getFeatures()) {
                clonedFutureGroup.getFeatures().removeIf(f -> f.getName().equals(usedFeature.getName()));
            }
            mutatedNextGroups.add(clonedFutureGroup);
        }

        // recurse down the include branch
        buildAlternativeGroups(treePermutations, includeState, mutatedNextGroups);
        // recurse down the skip branch
        buildAlternativeGroups(treePermutations, state.deepClone(), new ArrayList<>(nextGroups));
    }

    private List<AlternativeGroup> splitClustersByParents(List<AlternativeGroup> initialClusters) {
        List<AlternativeGroup> splitResult = new ArrayList<>();

        for (AlternativeGroup cluster : initialClusters) {
            List<Feature> remainingFeatures = new ArrayList<>(cluster.getFeatures());
            Feature originalParent = cluster.getParent();

            for (Feature potentialParent : features) {
                if (potentialParent.equals(originalParent) || !implicationMap.containsKey(potentialParent)) continue;

                // 2 features share same parent
                List<Feature> subset = remainingFeatures.stream()
                        .filter(f -> implicationMap.get(potentialParent).contains(f))
                        .toList();
                if (subset.size() >= 2) {
                    splitResult.add(new AlternativeGroup(new ArrayList<>(subset), potentialParent));
                }
            }

            // keep ALT group only if 2 or more remain
            if (remainingFeatures.size() >= 2) {
                splitResult.add(new AlternativeGroup(remainingFeatures, originalParent));
            }
        }

        return splitResult;
    }

    private List<AlternativeGroup> getAlternativeGroupClusters(Feature base, List<Feature> features, List<MutualExclusion> mutualExclusions) {
        Map<Feature, Set<Feature>> graph = new HashMap<>();
        for (Feature f : features) {
            graph.put(f, new HashSet<>());
        }

        for (MutualExclusion mutex : mutualExclusions) {
            Feature f1 = mutex.getFeature1();
            Feature f2 = mutex.getFeature2();
            graph.get(f1).add(f2);
            graph.get(f2).add(f1);
        }

        Set<Feature> R = new HashSet<>();
        Set<Feature> P = new HashSet<>(features);
        Set<Feature> X = new HashSet<>();
        List<Set<Feature>> maximalCliques = new ArrayList<>();

        bronKerbosch(R, P, X, graph, maximalCliques);

        Set<AlternativeGroup> alternatives = new LinkedHashSet<>();

        for (Set<Feature> clique : maximalCliques) {
            if (clique.size() >= 3) {

                List<Feature> reducedValues = new ArrayList<>(clique);
                reducedValues.sort(Comparator.comparing(Feature::getName));

                Feature parent = findParent(reducedValues);
                if (parent.equals(base)) {
                    if (isParentFeatureCovered(base, reducedValues) && !reducedValues.contains(base)) {
                        alternatives.add(new AlternativeGroup(reducedValues, base));
                    }
                } else {
                    if (isParentFeatureCovered(parent, reducedValues)) {
                        alternatives.add(new AlternativeGroup(reducedValues, parent));
                    }
                }
            }
        }
        return new ArrayList<>(alternatives);
    }

    private void bronKerbosch(Set<Feature> R, Set<Feature> P, Set<Feature> X,
                              Map<Feature, Set<Feature>> graph, List<Set<Feature>> cliques) {

        if (P.isEmpty() && X.isEmpty()) {
            cliques.add(new HashSet<>(R));
            return;
        }

        List<Feature> pCopy = new ArrayList<>(P);
        pCopy.sort(Comparator.comparing(Feature::getName)); // sort for consistent order

        for (Feature v : pCopy) {
            Set<Feature> nextR = new HashSet<>(R);
            nextR.add(v);

            Set<Feature> neighbors = graph.getOrDefault(v, new HashSet<>());
            Set<Feature> nextP = new HashSet<>(P);
            nextP.retainAll(neighbors);

            Set<Feature> nextX = new HashSet<>(X);
            nextX.retainAll(neighbors);

            bronKerbosch(nextR, nextP, nextX, graph, cliques);

            P.remove(v);
            X.add(v);
        }
    }

    private Feature findParent(List<Feature> reducedValues) {
        for(Feature key: features) {
            if(implicationMap.containsKey(key) && implicationMap.get(key).containsAll(reducedValues)) {
                return key;
            }
        }

        return base;
    }

    private boolean isParentFeatureCovered(Feature key, List<Feature> subFeatures) {
        List<Integer> extents;

        if(key.equals(base)) {
            extents = new ArrayList<>();
            for(int i = 0; i < context.getObjectCount(); i++) {
                extents.add(i);
            }
        } else {
            extents = context.getExtent(context.getAttributeIndex(key.getName())).toList();
        }

        Set<Integer> subExtents = new HashSet<>();

        for(Feature feature: subFeatures) {
            subExtents.addAll(context.getExtent(context.getAttributeIndex(feature.getName())).toList());
        }

        extents.removeAll(subExtents);

        return extents.isEmpty();
    }


    private void findOrGroups(List<TreePermutation> permutations) {
        for (TreePermutation permutation : permutations) {
            List<Constraint> constraints = permutation.getAllConstraints();
            findOrGroups(constraints, permutation.getFeatures());
            permutation.setCtcs(filterConstraintType(constraints, SimpleConstraint.class).toList());
            permutation.setGroups(filterConstraintType(constraints, Group.class).toList());
        }
    }

    private void findOrGroups(List<Constraint> constraints, List<Feature> features) {

        Set<Feature> usedFeatures = new HashSet<>();

        usedFeatures.addAll(filterConstraintType(constraints, AlternativeGroup.class).map(Group::getFeatures).reduce(new ArrayList<>(), (set1, set2) -> {
            set1.addAll(set2);
            return set1;
        }));

        for(Feature key: features) {
            boolean added = false;
            if(implicationMap.containsKey(key)) {

                List<Feature> matchSet = new ArrayList<>();
                matchSet.addAll(implicationMap.get(key));
                matchSet.removeAll(usedFeatures);
                matchSet.sort(Comparator.comparing(Feature::getName));
                if(matchSet.size() > 1 && isParentFeatureCovered(key, matchSet)) {
                    List<MutualExclusion> mutExs = new ArrayList<>();
                    if(checkAlternativeConnection(matchSet)) {
                        AlternativeGroup alternative = new AlternativeGroup(matchSet, key);
                        if(!constraints.contains(alternative)) {
                            constraints.add(alternative);
                        }

                        mutExs = filterConstraintType(constraints, MutualExclusion.class).filter(mutex -> (matchSet.contains(mutex.getFeature1()) && matchSet.contains(mutex.getFeature2()))).collect(Collectors.toList());
                        //TODO check for potentially dead features when adding an alternative
                    } else {
                        OrRelation orRelation = new OrRelation(matchSet, key);
                        if(!constraints.contains(orRelation)) {
                            constraints.add(orRelation);
                        }
                    }

                    usedFeatures.addAll(matchSet);
                    added = true;

                    if(added) {
                        Set<Constraint> removedConstraints = filterConstraintType(constraints, Implication.class).filter(c -> c.getFeature2().equals(key)).collect(Collectors.toSet());

                        constraints.removeAll(removedConstraints);
                        constraints.removeAll(mutExs);
                    }
                }
            }
        }
    }

    private boolean checkAlternativeConnection(List<Feature> matchSet) {
        for(Feature feature: matchSet) {
            for(Feature other: matchSet) {
                if(feature != other && !context.getExtent(context.getAttributeIndex(feature.getName())).newIntersect(context.getExtent(context.getAttributeIndex(other.getName()))).isEmpty()) {
                    return false;
                }
            }
        }

        return true;
    }

    private void filterRedundantConstraintsPermutation(List<TreePermutation> permutations) {
        for(TreePermutation permutation: permutations) {
            List<Constraint> constraints = permutation.getAllConstraints();
            filterRedundantConstraints(constraints);
            permutation.setCtcs(filterConstraintType(constraints, SimpleConstraint.class).toList());
            permutation.setGroups(filterConstraintType(constraints, Group.class).toList());
        }
    }

    public void filterRedundantConstraints(List<Constraint> constraints) {

        List<Implication> implications = filterConstraintType(constraints, Implication.class).toList();
        List<AlternativeGroup> alternatives = filterConstraintType(constraints, AlternativeGroup.class).toList();

        for(Implication implication: implications) {
            Set<MutualExclusion> redundantMutex = checkForRedundancy(constraints, implication, alternatives);

            if(!redundantMutex.isEmpty()) {
                constraints.removeAll(redundantMutex);
            }

            List<Implication> potentialImplications = new ArrayList<>();

            // finds all implications that can be derived from the current implication by substituting the right side of the implication with other features that imply it
            for(Feature key: features) {
                if(implicationMap.containsKey(key) && implicationMap.get(key).contains(implication.getFeature2())) {
                    potentialImplications.add(new Implication(implication.getFeature1(), key));
                }
            }

            for(Implication potentialImplication: potentialImplications) {
                redundantMutex = checkForRedundancy(constraints, potentialImplication, alternatives);
            }

            if(!redundantMutex.isEmpty()) {
                constraints.removeAll(redundantMutex);
            }
        }

        checkForGroupRedundancy(alternatives, constraints);
    }

    /*
     * Checks whether mutex conditions are already implicitly present through the groupings
     */
    private void checkForGroupRedundancy(List<AlternativeGroup> alternatives, List<Constraint> constraints) {

        List<Group> groups = filterConstraintType(constraints, Group.class).toList();

        List<MutualExclusion> allMutex = filterConstraintType(constraints, MutualExclusion.class).toList();
        Set<MutualExclusion> checkingMutex = new HashSet<>();
        Set<MutualExclusion> removedMutex = new HashSet<>();

        Map<Feature, Group> featureGroupMap = computeFeatureGroupMap(groups);

        for(MutualExclusion mutex: allMutex) {
            Feature left = mutex.getFeature1();
            Feature right = mutex.getFeature2();
            MutualExclusion impliedConstraint = null;
            Group group = featureGroupMap.get(left);
            if(group != null) {
                impliedConstraint = new MutualExclusion(group.getParent(), right);
                checkImpliedConstraint(alternatives, mutex, impliedConstraint, removedMutex, removedMutex);
                Group other = featureGroupMap.get(right);
                if(other != null) {
                    impliedConstraint = new MutualExclusion(impliedConstraint.getFeature1(), other.getParent());
                    //impliedConstraint.setFeature2(other.getParent());
                    checkImpliedConstraint(alternatives, mutex, impliedConstraint, removedMutex, removedMutex);
                }
            }
            group = null;
            group = featureGroupMap.get(right);
            if(group != null) {
                impliedConstraint = new MutualExclusion(left, group.getParent());
                checkImpliedConstraint(alternatives, mutex, impliedConstraint, removedMutex, removedMutex);
                Group other = featureGroupMap.get(left);
                if(other != null) {
                    impliedConstraint = new MutualExclusion(other.getParent(), impliedConstraint.getFeature2());
                    //impliedConstraint.setFeature1(other.getParent());
                    checkImpliedConstraint(alternatives, mutex, impliedConstraint, removedMutex, removedMutex);
                }
            }
        }
		/*
		for(AlternativeGroup alternative: alternatives) {

			for(Feature f: alternative.getFeatures()) {
				if(implicationMap.containsKey(f)) {
					Set<Feature> implyingFeatures = implicationMap.get(f);
					for(Feature implyingFeature: implyingFeatures) {

					}
				}
			}
		}*/

        for(MutualExclusion mutex: checkingMutex) {
            if(!removedMutex.contains(mutex)) {
                for(MutualExclusion compareMutex: allMutex) {
                    if(compareMutex.equals(mutex)) {
                        removedMutex.add(compareMutex);
                        break;
                    }
                }
            }
        }

        if(!removedMutex.isEmpty()) {
            constraints.removeAll(removedMutex);
        }
    }

    private void checkImpliedConstraint(List<AlternativeGroup> alternatives, MutualExclusion mutex, MutualExclusion impliedConstraint, Set<MutualExclusion> removedMutex, Set<MutualExclusion> checkingMutex) {
        if(impliedConstraint != null) {
            for(AlternativeGroup a: alternatives) {
                if(a.getFeatures().contains(impliedConstraint.getFeature1()) && a.getFeatures().contains(impliedConstraint.getFeature2())) {
                    removedMutex.add(mutex);
                }
            }

            if(!removedMutex.contains(mutex)) {
                checkingMutex.add(impliedConstraint);
            }
        }
    }

    // TODO: loop overrides group value, so it is always just the last group
    private Map<Feature, Group> computeFeatureGroupMap(List<Group> groups) {
        Map<Feature, Group> featureGroupMap = new HashMap<>();

        for(Group group: groups) {
            for(Feature feature: group.getFeatures()) {
                featureGroupMap.put(feature, group);
            }
        }

        return featureGroupMap;
    }

    /*
     * Checks whether mutual exclusion constraints are already covered by another one or groups by substituting features from implications
     */
    private Set<MutualExclusion> checkForRedundancy(List<Constraint> constraints, Implication implication, List<AlternativeGroup> alternatives) {
        Feature left = implication.getFeature1();

        Set<MutualExclusion> relevantMutex = filterConstraintType(constraints, MutualExclusion.class)
                .filter(m -> m.getFeature1().equals(left) || m.getFeature2().equals(left))
                .collect(Collectors.toSet());

        Set<MutualExclusion> removedMutex = new HashSet<>();

        for(MutualExclusion mutex: relevantMutex) {
            MutualExclusion impliedMutex;
            if(mutex.getFeature1().equals(left)) {
                impliedMutex = new MutualExclusion(implication.getFeature2(), mutex.getFeature2());
            } else {
                impliedMutex = new MutualExclusion(implication.getFeature2(), mutex.getFeature1());
            }

            for(AlternativeGroup a: alternatives) {
                if(a.getFeatures().contains(impliedMutex.getFeature1()) && a.getFeatures().contains(impliedMutex.getFeature2())) {
                    removedMutex.add(mutex);
                }
            }

            Set<MutualExclusion> allMutex = filterConstraintType(constraints, MutualExclusion.class).collect(Collectors.toSet());

            if(!removedMutex.contains(mutex)) {
                for(MutualExclusion compareMutex: allMutex) {
                    if(compareMutex.equals(impliedMutex)) {
                        removedMutex.add(mutex);
                        break;
                    }
                }
            }
        }

        return removedMutex;
    }
}


