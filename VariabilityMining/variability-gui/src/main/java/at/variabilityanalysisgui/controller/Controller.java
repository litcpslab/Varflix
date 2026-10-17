/***
 
    This Source Code Form is subject to the terms of the Mozilla
    Public License, v. 2.0. If a copy of the MPL was not distributed
    with this file, You can obtain one at
    https://mozilla.org/MPL/2.0/.*
    Contributors:
    Michael Schmidhammer
    
    Modifications: 
    Copyright (c) 2025 Johannes Kepler University Linz
  	LIT Cyber-Physical Systems Lab
 	Contributors:
 	Alexander Stummer - Added scene switching & adapted to integrate with Varflix backend
    Kejda Domi- Added the visualization section
    Sophie Öttl - Change Tracking
**/

package at.variabilityanalysisgui.controller;


import at.variabilityanalysisgui.visualization.TreeGraph;
import constraints.Constraint;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.application.Platform;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import guiModel.Element;
import guiModel.ExtractionType;
import guiModel.Group;
import javafx.scene.Node;
import variabilityMining.Feature;
import variabilityMining.TreePermutation;
import variabilityMining.VarflixAPI;


public class Controller {

    public static final String OCCURRENCE_HEADER_JAVA = "Occurrence:";
    public static final String OCCURRENCE_HEADER_IEC = "Variants:";
    public static final String ELEMENTS_HEADER = "Elements:";

    @FXML private FeatureViewController featureViewController;
    @FXML private ConstraintsViewController constraintsViewController;

    @FXML private TabPane visualizationTabPane;
    @FXML private TabPane varflixTabPane;
    @FXML private Tab constraintsTab;

    @FXML private MenuItem loadItem;
    @FXML private MenuItem saveItem;


    public static final int MAX_VISUALIZATION_TABS = 5;

    private List<Group> originalGroups;
    private ExtractionType artifactType = ExtractionType.UNKNOWN;
    private final VarflixAPI model = new VarflixAPI();
    private final List<TreeGraph> featureGraphs = new ArrayList<>();

    public Map<ExtractionType, String> seperatorMap = Map.of(ExtractionType.JAVA, "/", ExtractionType.IEC61499, ";");

    @FXML
    public void initialize(){

    }

    public void init(String variantPath, String extractionPath) {
        originalGroups = model.computeInitialGroups();
        model.computePCM(originalGroups);
        model.performFCA();
        model.generateModel();
        Feature currentBase = model.getPermutations().get(0).getRoot();

        artifactType = ExtractionType.IEC61499;		//TODO Change dynamically based on artifacts used
        featureViewController.setMainController(this);
        featureViewController.init();
        constraintsViewController.init();
        constraintsViewController.setMainController(this);

        visualizationTabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            Integer permutation = permutationIndexOf(newTab);
            if (permutation != null) {
                constraintsViewController.showPermutation(permutation);
            }
        });

        constraintsTab.setOnSelectionChanged(event -> {
            if(constraintsTab.isSelected()) {
                constraintsViewController.setPermutationIndex(getSelectedPermutationIndex());
                constraintsViewController.setModel(model);
                featureViewController.resetSelection();
                constraintsViewController.isGroupView(true);
                loadItem.setOnAction(e -> constraintsViewController.handleLoadAction());
                saveItem.setOnAction(e -> constraintsViewController.handleSaveAction());
            } else {
                constraintsViewController.resetConstraintsViewButtons();
                constraintsViewController.resetSelection();
                loadItem.setOnAction(e -> handleLoadAction());
                saveItem.setOnAction(e -> handleSaveAction());
            }
        });

        Feature currentBase = computeFeatureModel();

        Platform.runLater(this::updateVisualizationTabs);

    }

    public void updateVisualizationTabs() {
        visualizationTabPane.getTabs().clear();
        featureGraphs.clear();

        List<TreePermutation> permutations = model.getPermutations();
        if (permutations == null || permutations.isEmpty()) return;

        int tabCount = Math.min(permutations.size(), MAX_VISUALIZATION_TABS);
        TreeViewController treeViewController = featureViewController.getTreeViewController();

        for (int i = 0; i < tabCount; i++) {
            TreeGraph graph = new TreeGraph(permutations.get(i).getRoot());
            featureGraphs.add(graph);

            ScrollPane scrollPane = new ScrollPane();
            scrollPane.setContent((Node) graph.getViewer());
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);

            Tab tab = new Tab("Model " + (i + 1));
            tab.setUserData(i);
            tab.setClosable(true);
            tab.setContent(scrollPane);
            visualizationTabPane.getTabs().add(tab);

            graph.setSelectionListener(featureId -> treeViewController.selectAndScrollTo(featureId));
        }

        treeViewController.setOnListSelectionChanged(featureId -> {
            for (TreeGraph graph : featureGraphs) {
                graph.highlightNode(featureId);
            }
        });
    }

    public void redrawVisualization(){
        List<TreePermutation> permutations = model.getPermutations();
        if (permutations != null && !featureGraphs.isEmpty()) {
            for (int i = 0; i < featureGraphs.size() && i < permutations.size(); i++) {
                featureGraphs.get(i).updateGraph(permutations.get(i).getRoot());
            }
        }

        constraintsViewController.updateConstraintModel();
    }

    public int getSelectedPermutationIndex() {
        Integer index = permutationIndexOf(visualizationTabPane.getSelectionModel().getSelectedItem());
        if(index == null) {
            return 0;
        }
        return index;
    }

    private Integer permutationIndexOf(Tab tab) {
        if (tab != null && tab.getUserData() instanceof Integer index) {
            return index;
        }
        return null;
    }

    @FXML
    private void handleLoadAction() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Difference Report File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        fileChooser.setInitialDirectory(new File("./"));
        File selectedFile = fileChooser.showOpenDialog(featureViewController.getWindow());

        featureViewController.loadFile(selectedFile);
        redrawVisualization();
    }

    @FXML
    private void handleSaveAction() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Differences");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        fileChooser.setInitialFileName("saved_differences.txt");
        fileChooser.setInitialDirectory(new File("./"));
        File file = fileChooser.showSaveDialog(featureViewController.getWindow());

        if (file == null) {
            System.out.println("Saving cancelled by user.");
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (Group group : originalGroups) {
                if (group.getElements().isEmpty()) {
                    continue;
                }
                // Group Header
                String groupName = group.getName().get();
                writer.write(groupName + ":");
                writer.newLine();

                // Occurrences
                if (group.getOccurrences() != null && !group.getOccurrences().isEmpty()) {
                    if (group.getElements().size() > 1 && this.artifactType == ExtractionType.JAVA) {
                        writer.write(OCCURRENCE_HEADER_JAVA);
                    } else {
                        writer.write(OCCURRENCE_HEADER_IEC);
                    }
                    writer.newLine();
                    for (String occurrence : group.getOccurrences()) {
                        writer.write(occurrence);
                        writer.newLine();
                    }
                }

                // Elements
                writer.write(ELEMENTS_HEADER);
                writer.newLine();
                for (Element element : group.getElements()) {
                	if(artifactType == ExtractionType.JAVA) {
                        writer.write("(" + element.getLocation() + ")");
                        writer.newLine();

                        writer.write(element.getDescription());
                        writer.newLine();

                    } else if(artifactType == ExtractionType.IEC61499) {
                        writer.write(element.getDescription());
                        writer.newLine();
                    }
                }
                writer.newLine();
            }


        } catch (IOException e) {
            featureViewController.showErrorDialog("Error Saving File", "Could not save the file:\n" + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @FXML
    private void handleExit() {
        Platform.exit();
    }

    public static Group findGroupContainingElement(List<Group> groups, Element element) {
        if (groups == null) return null;
        for (Group group : groups) {
            if (group.getElements().contains(element)) {
                return group;
            }
        }
        return null;
    }

    public ExtractionType getParserType() {
        return artifactType;
    }
    
    public void setOriginalGroups(List<Group> groups) {
    	this.originalGroups = groups;
    }

    public List<Group> getOriginalGroups() {
        return originalGroups;
    }

    public ExtractionType getArtifactType() {
		return artifactType;
	}
    
    public void setArtifactType(ExtractionType artifactType) {
		this.artifactType = artifactType;
	}
    public void changeScene(ActionEvent event) throws IOException {
    	model.computePCM(originalGroups.stream().filter(group -> !group.getElements().isEmpty()).toList());

    	ConstraintsViewController constraintController = SceneManager.getConstraintsLoader().getController();
    	constraintController.setModel(model);
        constraintController.setController(this);

    	Scene scene = ((Node)event.getSource()).getScene();
    	scene.setRoot((Parent)SceneManager.getConstraintScene());
    }

    public Group findGroupById(int groupId) {
        return getOriginalGroups().stream()
                .filter(g -> g.getId() == groupId)
                .findFirst()
                .orElse(null);
    }

    public FeatureViewController getFeatureViewController() {
		return featureViewController;
	}

    public TreeGraph getFeatureGraph() {
        if(featureGraphs.isEmpty()) {
            return null;
        }
        return featureGraphs.get(0);
    }

    public List<Constraint> getConstraints() {
        return model.performFCA();
    }

    public List<TreePermutation> getPermutations() {
        return model.getPermutations();
    }

    public List<Feature> getAllTreeFeaturesByName(String name) {
        return model.getPermutations().stream()
                .map(permutation -> permutation.getFeatureByName(name))
                .filter(Objects::nonNull).toList();
    }
}