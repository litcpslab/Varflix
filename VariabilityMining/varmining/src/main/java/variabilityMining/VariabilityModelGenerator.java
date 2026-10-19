/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla
 * Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2025 Johannes Kepler University Linz
 * LIT Cyber-Physical Systems Lab
 * Contributors:
 *  Alexander Stummer - Initial Implementation
********************************************************************************/

package variabilityMining;

import java.util.*;
import java.util.stream.Collectors;

import UVL.UVLGenerator;
import constraints.AlternativeGroup;
import constraints.Constraint;
import constraints.Group;
import constraints.Implication;
import constraints.SimpleConstraint;

public class VariabilityModelGenerator {
	
	private Feature root;
	
	private List<Feature> features;
	
	private List<SimpleConstraint> coveredConstraints;
	
	private int altCount = 0;

	public VariabilityModelGenerator() {
		this.coveredConstraints = new ArrayList<>();
	}
	
	
	/*
	 * Method to generate a variability model given the base/root feature, a list of all features and a list of all the constraints.
	 * The result is written to a uvl file
	 */
	public List<Constraint> generateVariabilityModel(Feature base, List<Feature> features, List<Constraint> constraints, boolean enableOtherRelations) {
		
		this.features = features;
        List<Feature> removedFeatures = features.stream().filter(feature -> (feature.getName().startsWith("OR") || feature.getName().startsWith("ALT"))).toList();
		this.features.removeAll(removedFeatures);
		features.forEach(feature -> {
			feature.setChildren(new ArrayList<>());
			feature.setParent(null);
            feature.setAlternativeParent(false);
            feature.setOrParent(false);
		});
		
		root = base == null? new Feature("Base"): base;
		root.setMandatory(true);
		
		
		List<Group> groupConstraints = new ArrayList<>();
		
		for(Constraint groupConstraint: constraints) {
			if(groupConstraint instanceof Group) {
				groupConstraints.add((Group)groupConstraint);
			}
		}
			
		this.coveredConstraints = new ArrayList<>();
		
		List<SimpleConstraint> baseEquivalences = constraints.stream()
				.filter(c -> c.getType().equals("Equivalence")).map(c -> (SimpleConstraint) c).filter(c -> (c.getFeature1().equals(base) || c.getFeature2().equals(base))).toList();
			
		generateMandatoryFeatures(base, baseEquivalences);
			
		coveredConstraints.addAll(baseEquivalences);
			
		buildGroups(base, groupConstraints);	
		
		List<Feature> uncoveredFeatures = features.stream().filter(f -> f.getParent() == null && !f.equals(root)).toList();
		
		List<SimpleConstraint> relevantConstraints = constraints.stream().filter(c -> c instanceof SimpleConstraint).map(c -> (SimpleConstraint) c)
				.filter(f -> uncoveredFeatures.contains(f.getFeature1()) || uncoveredFeatures.contains(f.getFeature2())).toList();
		
		if(enableOtherRelations) {
			
			List<Constraint> addedConstraints = buildOtherRelations(uncoveredFeatures, relevantConstraints, constraints);
			
			constraints.addAll(addedConstraints);
		} else {
			for(Feature feature: uncoveredFeatures) {
				findParentChildRelations(relevantConstraints, feature);
			}
		}
		
		positionUncoveredFeatures(uncoveredFeatures.stream().filter(f -> f.getParent() == null).toList());
			
		List<SimpleConstraint> ctcs = constraints.stream().filter(c -> !coveredConstraints.contains(c) && c instanceof SimpleConstraint).map(c -> (SimpleConstraint) c).toList();
			
		UVLGenerator.createUVLModel(root, ctcs);
		
		return constraints.stream().filter(c -> (!(c instanceof Implication && ((Implication)c).getFeature2().equals(root)))).collect(Collectors.toList());

	}

	private void positionUncoveredFeatures(List<Feature> uncoveredFeatures) {
		for(Feature feature: uncoveredFeatures) {
			root.addChild(feature);
			feature.setParent(root);
		}
	}
	

	/*
	 * Check the remaining constraints and determine which are CTCs and which can directly be integrated in the variability model
	 */
	private List<Constraint> buildOtherRelations(List<Feature> uncoveredFeatures, List<SimpleConstraint> relevantConstraints, List<Constraint> constraints) {
		
		List<Constraint> addedConstraints = new ArrayList<>();
		List<SimpleConstraint> relevantEquivalences = relevantConstraints.stream().filter(c -> c.getType().equals("Equivalence")).toList();
		
		if(relevantConstraints.isEmpty()) {
			root.addChildren(uncoveredFeatures);
			for(Feature feature: uncoveredFeatures) {
				feature.setParent(root);
			}
		}
		
		featureLoop:
		for(Feature feature: uncoveredFeatures) {
			for(SimpleConstraint equivalence: relevantEquivalences) {
				if(equivalence.getFeature2().equals(feature)) {
					Feature other = equivalence.getFeature1();
					feature.setMandatory(true);
					feature.setParent(other);
					other.addChild(feature);
					coveredConstraints.add(equivalence);
					continue featureLoop;
				}
			}
			
			List<SimpleConstraint> relevantMutex = relevantConstraints.stream().filter(c -> c.getType().equals("Mutual Exclusion"))
					.filter(c -> c.getFeature1().getName().equals(feature.getName()) || c.getFeature2().getName().equals(feature.getName())).toList();		
			for(SimpleConstraint mutex: relevantMutex) {
				Feature other;
				if(mutex.getFeature1().getName().equals(feature.getName())) {
					other = features.stream().filter(f -> f.getName().equals(mutex.getFeature2().getName())).findFirst().get();
				} else {
					other = features.stream().filter(f -> f.getName().equals(mutex.getFeature1().getName())).findFirst().get();
				}

				if(feature.getParent() == null && other.getParent() == null) {
					altCount++;
					Feature altParent = new Feature("ALT" + altCount, root);
					altParent.setAlternativeParent(true);
					altParent.setAbstract(true);
					root.addChild(altParent);
					features.add(altParent);
					altParent.addChild(feature);
					feature.setParent(altParent);
					altParent.addChild(other);
					other.setParent(altParent);
					coveredConstraints.add(mutex);
					constraints.remove(mutex);
					addedConstraints.add(new AlternativeGroup(altParent.getChildren(), root));
					continue featureLoop;

				}
				
			}
			
			findParentChildRelations(relevantConstraints, feature);
		}
		return addedConstraints;
	}


	private void findParentChildRelations(List<SimpleConstraint> relevantConstraints, Feature feature) {
		List<SimpleConstraint> relevantImplications = relevantConstraints.stream().filter(c -> c.getType().equals("Implication"))
				.filter(c -> c.getFeature1().getName().equals(feature.getName()) || c.getFeature2().getName().equals(feature.getName())).toList();
		
		for(SimpleConstraint implication: relevantImplications) {
			if(implication.getFeature1().getName().equals(feature.getName())) {
				Feature other = features.stream().filter(f -> f.getName().equals(implication.getFeature2().getName())).findFirst().get();
				
				if(feature.getParent() == null && !other.isAlternativeParent() && !other.isOrParent()) {
					other.addChild(feature);
					feature.setParent(other);
					coveredConstraints.add(implication);
					return;
				}
			}
		}
	}

	/*
	 * Building all the feature groups given in the groups list and establishing their relations
	 */
	private void buildGroups(Feature base, List<Group> groups) {
		
		int orCount = 0;
		altCount = 0;
		
		groups.sort(Comparator.comparing(g -> g.getParent().getName()));
		
		List<Feature> groupChildren = new ArrayList<>();
		
		Map<Feature, List<Group>> groupMap = new HashMap<>();
		
		for(Group group: groups) {
			for(Feature feature: features) {
				if(group.getParent().getName().equals(feature.getName())) {
					if(groupMap.get(feature) == null) {
						List<Group> groupList = new ArrayList<>();
						groupList.add(group);
						groupMap.put(feature, groupList);
					} else {
						groupMap.get(feature).add(group);
					}
				}
			}
		}
		
		
		for(Feature parent: groupMap.keySet()) {
			List<Group> childGroups = groupMap.get(parent);
			
			for(Group group: childGroups) {
				group.getFeatures().sort(Comparator.comparing(Feature::getName));
			}
			
			childGroups.sort(Comparator.comparing(g -> g.getFeatures().get(0).getName()));
			
			if(childGroups.size() > 1 || parent.equals(base)) {
				for(Group group: childGroups) {
					switch (group.getType()) {
					case "Or Group":
						orCount++;
						String parentName = "OR" + orCount;
						Feature orParent = features.stream().filter(f -> f.getName().equals(parentName))
								.findFirst().orElse(new Feature(parentName, parent));
						if(!features.contains(orParent)) {
							features.add(orParent);
						} else {
							orParent.setParent(parent);
						}
						
						parent.addChild(orParent);
						orParent.setMandatory(true);
						orParent.setOrParent(true);
						orParent.setAbstract(true);
                        groupChildren = new ArrayList<>(group.getFeatures());
						groupChildren.stream().forEach(f -> f.setParent(orParent));
						groupChildren.sort((c1,c2) -> c1.getName().compareTo(c2.getName()));
						orParent.setChildren(groupChildren);
						break;
					case "Alternative":
						altCount++;
						parentName = "ALT" + altCount;
						Feature altParent = features.stream().filter(f -> f.getName().equals(parentName))
								.findFirst().orElse(new Feature(parentName, parent));
						if(!features.contains(altParent)) {
							features.add(altParent);
						} else {
							altParent.setParent(parent);
						}
						parent.addChild(altParent);
						altParent.setMandatory(true);
						altParent.setAlternativeParent(true);
						altParent.setAbstract(true);
                        groupChildren = new ArrayList<>(group.getFeatures());
						groupChildren.stream().forEach(f -> f.setParent(altParent));
						groupChildren.sort(Comparator.comparing(Feature::getName));
						altParent.setChildren(groupChildren);
						break;
					default:
						break;
				}
				}
			} else {
				Group group = childGroups.get(0);
				switch (group.getType()) {
					case "Or Group":
						parent.setOrParent(true);
						break;
					case "Alternative":
						parent.setAlternativeParent(true);
						break;
					default:
						break;
				}

                groupChildren = new ArrayList<>(group.getFeatures());
				groupChildren.stream().forEach(f -> f.setParent(parent));
				groupChildren.sort(Comparator.comparing(Feature::getName));
				parent.addChildren(groupChildren);
			}
		}
				
	}
	
		

	/*
	 * Generating all features of the variability model that are mandatory, i.e. present in every possible configuration
	 */
	private void generateMandatoryFeatures(Feature base, List<SimpleConstraint> baseEquivalences) {
		
		for(SimpleConstraint constraint: baseEquivalences) {
			Feature other = constraint.getFeature1().equals(base) ? constraint.getFeature2():constraint.getFeature1();
			Feature feature = features.stream().filter(f -> f.getName().equals(other.getName())).findFirst().get();
			
			feature.setParent(root);
			feature.setMandatory(true);
			root.addChild(feature);
		}
	}
}
