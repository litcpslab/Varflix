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

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import constraints.Constraint;
import constraints.Equivalence;
import constraints.Implication;
import constraints.MutualExclusion;
import fr.lirmm.fca4j.algo.AOC_poset_Hermes;
import fr.lirmm.fca4j.algo.Lattice_AddExtent;
import fr.lirmm.fca4j.cli.io.MyCSVReader;
import fr.lirmm.fca4j.core.ConceptOrder;
import fr.lirmm.fca4j.core.IBinaryContext;
import fr.lirmm.fca4j.iset.ISet;
import fr.lirmm.fca4j.iset.std.ArrayListSetFactory;

public class FormalConceptAnalysis {

	private File source;
	
	private Map<Integer, ISet> attributeConcepts = new HashMap<>();
	
	private IBinaryContext context;
	
	private ConceptOrder order;
	
	private ConceptOrder fullOrder;

    private List<Constraint> constraints;
	private List<Feature> features;
	
	private Map<Feature, Set<Feature>> implicationMap = new HashMap<>();
	
	private Feature base;
    private List<TreePermutation> groupPermutations = new ArrayList<>();
	
	public FormalConceptAnalysis(File file) {
		this.source = file;
		this.constraints = new ArrayList<>();
		this.features = new ArrayList<>();
	}
	
	public File getSource() {
		return source;
	}
	
	public void buildConceptLattice() {

		try {
			context = MyCSVReader.read(source, ';', new ArrayListSetFactory());
			
			for(int i = 0; i < context.getAttributeCount(); i++) {
				features.add(new Feature(context.getAttributeName(i)));
			}
			
			Lattice_AddExtent lattice = new Lattice_AddExtent(context);
			lattice.run();
			
			fullOrder = lattice.getResult();
						
			AOC_poset_Hermes hermes = new AOC_poset_Hermes(context);
			hermes.run();

			order = hermes.getResult();
			
			for(Integer i : fullOrder.getConcepts()) {
				
				ISet curr = fullOrder.getConceptReducedIntent(i);
					
				if(!curr.isEmpty()) {
					attributeConcepts.put(i, curr);
				}
			}
			
			if(order.getMaximals().cardinality() > 1) {
				base = new Feature("Root");
				features.add(base);
			} else {
				base = features.stream().filter(f -> f.getName().equals(context.getAttributeName(order.getConceptIntent(order.getTop()).first()))).findAny().orElseGet(() -> null);
			}
			
			findEquivalences();
			
			findImplications();

			findMutualExclusions();

            GroupPermutationGenerator groupPermutationGenerator = new GroupPermutationGenerator(context, base, features, constraints);

            groupPermutations = groupPermutationGenerator.generateConstraintPermutations();
			
			ConstraintFileIO.writeConstraintsToFile(new File("constraints.json"), constraints, features, base);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void findMutualExclusions() {
		
		Set<Integer> concepts = order.getConcepts().stream().filter(i -> !order.getConceptReducedIntent(i).isEmpty()).collect(Collectors.toSet());
		Set<Integer> processedConcepts = new HashSet<>();
		
		for(Integer key: concepts) {
			for(Integer key2: concepts) {
				if(!processedConcepts.contains(key) && !processedConcepts.contains(key2) && key != key2) {
					ISet extent = order.getConceptExtent(key);
					ISet extent2 = order.getConceptExtent(key2);
					ISet intersect = extent.newIntersect(extent2);
					
					if(intersect.isEmpty()) {
						
						Feature currentFeature = getFeatureByName(context.getAttributeName(order.getConceptReducedIntent(key).first()));
						Feature mutexFeature = getFeatureByName(context.getAttributeName(order.getConceptReducedIntent(key2).first()));
						
						MutualExclusion mutex = new MutualExclusion(currentFeature, mutexFeature);
						
						if(!constraints.contains(mutex)) {
							constraints.add(mutex);
						}

					} 
				}
			}
			processedConcepts.add(key);
			
		}
		
	}

	private Feature getFeatureByName(String featureName) {
		
		for(Feature feature: features) {
			if(feature.getName().equals(featureName)) {
				return feature;
			}
		}
		
		return null;
	}

	private void findImplications() {
	
		Set<Integer> concepts = order.getConcepts().stream().filter(i -> !order.getConceptReducedIntent(i).isEmpty()).collect(Collectors.toSet());
		
		for(Integer key: concepts) {
			for(Integer key2: concepts) {
				if(key != key2) {
					if(order.getUpperCover(key).contains(key2)) {
						ISet curr = order.getConceptReducedIntent(key);
						ISet cover = order.getConceptReducedIntent(key2);
						
						if(!curr.isEmpty() && !cover.isEmpty()) {
							
							Feature currentFeature = getFeatureByName(context.getAttributeName(curr.first()));
							Feature impliedFeature = getFeatureByName(context.getAttributeName(cover.first()));
							
							Implication implication = new Implication(currentFeature, impliedFeature);
							
							if(!constraints.contains(implication)) {
								constraints.add(implication);
							}
						}
					}
				}
			}
		}
		
	}

	private void findEquivalences() {
		
		for(Integer key: attributeConcepts.keySet()) {
			ISet current = attributeConcepts.get(key);
			
			if(current.cardinality() > 1) {
				Iterator<Integer> currentIterator = current.iterator();
				
				Feature featureName = getFeatureByName(context.getAttributeName(currentIterator.next()));
				
				while(currentIterator.hasNext()) {
					Feature feature2 = getFeatureByName(context.getAttributeName(currentIterator.next()));
					
					Equivalence equivalence = new Equivalence(featureName, feature2);
					
					if(!constraints.contains(equivalence)) {
						constraints.add(equivalence);
					}
					
				}
			}
		}
	}

	public List<Constraint> getConstraints() {
		return constraints;
	}

	public List<Feature> getFeatures() {
		return features;
	}
	
	public Feature getBaseFeature() {
		return base;
	}

    public List<TreePermutation> getGroupPermutations() {
        return groupPermutations;
    }
}
