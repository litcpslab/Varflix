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
import java.util.ArrayList;
import java.util.List;

import UVL.UVLGenerator;
import constraints.Constraint;
import guiModel.Group;
import iec61499Mining.DeltaModelGenerator;
import iec61499Mining.IEC61499VariabilityExtractor;
import mappers.DataMapper;
import varflixModel.IVariabilityGroup;
import varflixModel.IEC61499.IEC61499Variability;
import varflixModel.IEC61499.IEC61499Variant;
import varflixModel.IEC61499.JSON1499VariabilityGroup;

public class VarflixAPI {
	
	private IVariabilityExtractor<IEC61499Variant, IEC61499Variability> extractor;
	
	private DataMapper mapper;
	
	private FormalConceptAnalysis analysis;

    private List<TreePermutation> permutations;
	
	private VariabilityModelGenerator generator = new VariabilityModelGenerator();

    private PermutationGenerator permutationGenerator = new PermutationGenerator();

	public List<Group> computeInitialGroups(String variantPath, String extractionPath){

		extractor = new IEC61499VariabilityExtractor();

		List<IVariabilityGroup<IEC61499Variant, IEC61499Variability>> initialGroups = extractor.performAutomaticMining(variantPath, extractionPath);

		mapper = new DataMapper();
		
		DeltaModelGenerator.generateDeltas(initialGroups.stream().map(e -> (JSON1499VariabilityGroup) e).toList(), "output/deltas/");
		
		return mapper.mapVariabilityGroup(initialGroups);
	}
	
	public void computePCM(List<Group> editedGroups) {
		
		List<IVariabilityGroup<IEC61499Variant, IEC61499Variability>> updatedGroups = mapper.mapGUIGroupTo1499VariabilityGroup(editedGroups, extractor.getVariants(), extractor.getElements(), ((element) -> new IEC61499Variability(element.getId())), ((variant) -> new IEC61499Variant(variant)));
		
		DeltaModelGenerator.generateDeltas(updatedGroups.stream().map(e -> (JSON1499VariabilityGroup) e).toList(), "output/deltas/");
		
		extractor.buildPCM(extractor.getVariants(), updatedGroups);	
	}
	
	public List<Constraint> performFCA(){
		analysis = new FormalConceptAnalysis(new File("pcm.csv"));
		analysis.buildConceptLattice();
		return analysis.getGroupPermutations().get(0).getAllConstraints();
	}

	public List<Feature> getFeatures() {
		return analysis.getGroupPermutations().get(0).getFeatures();
	}
	
	public Feature getBaseFeature() {
		return analysis.getGroupPermutations().get(0).getRoot();
	}

    public List<TreePermutation> getPermutations() {
        return permutations;
    }
	
//	public List<Constraint> generateModel(Feature base, List<Feature> features, List<Constraint> constraints) {
//		List<Constraint> updatedConstraints = generator.generateVariabilityModel(base, features, constraints);
//		DeltaModelGenerator.generateDeltaConfigFile(features, constraints, "output/deltas/");
//        return updatedConstraints;
//	}

    public void generateModel() {
        List<TreePermutation> validTrees = analysis.getGroupPermutations().stream()
                        .flatMap(t -> permutationGenerator.generatePermutations(t.getRoot(), t.getFeatures(), t.getAllConstraints()).stream())
                                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        permutationGenerator.dumpPermutationsToFile(validTrees, "debug_permutations.txt");
        List<Constraint> result = new ArrayList<>(validTrees.get(0).getModelConstraints());
        UVLGenerator.createUVLModel(validTrees.get(0).getRoot(), validTrees.get(0).getModelCtcs(), "output/model.uvl", false);

        DeltaModelGenerator.generateDeltaConfigFile(validTrees.get(0).getFeatures(), result, "output/deltas/");

        permutations = validTrees;
    }
}
