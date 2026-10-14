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
import javafx.fxml.FXML;
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
import variabilityMining.VarflixAPI;


public class Controller {

    public static final String OCCURRENCE_HEADER_JAVA = "Occurrence:";
    public static final String OCCURRENCE_HEADER_IEC = "Variants:";
    public static final String ELEMENTS_HEADER = "Elements:";
   
    @FXML private FeatureViewController featureViewController;
    @FXML private ConstraintsViewController constraintsViewController;

    @FXML private ScrollPane visualizationWindow;
    @FXML private TabPane varflixTabPane;
    @FXML private Tab constraintsTab;

    @FXML private MenuItem loadItem;
    @FXML private MenuItem saveItem;

    private List<Group> originalGroups;
    private ExtractionType artifactType = ExtractionType.UNKNOWN;
    private final VarflixAPI model = new VarflixAPI();
    private TreeGraph featureGraph;
    private Feature currentBase;

    public Map<ExtractionType, String> seperatorMap = Map.of(ExtractionType.JAVA, "/", ExtractionType.IEC61499, ";");

    @FXML
    public void initialize(){

    }

    public void init(String variantPath, String extractionPath) {
        originalGroups = model.computeInitialGroups(variantPath, extractionPath);
        artifactType = ExtractionType.IEC61499;		//TODO Change dynamically based on artifacts used
        featureViewController.setMainController(this);
        featureViewController.init();
        constraintsViewController.init();
        constraintsViewController.setMainController(this);
        
        constraintsTab.setOnSelectionChanged(event -> {
        	if(constraintsTab.isSelected()) {
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

        computeFeatureModel();

        Platform.runLater(()->{
        	featureGraph = new TreeGraph(currentBase);

            visualizationWindow.setContent((Node)featureGraph.getViewer());
            visualizationWindow.setFitToWidth(true);
            visualizationWindow.setFitToHeight(true);

            TreeViewController treeViewController = featureViewController.getTreeViewController();
            featureGraph.setSelectionListener(treeViewController::selectAndScrollTo);
            treeViewController.setOnListSelectionChanged(featureId -> featureGraph.highlightNode(featureId));
        });
        
    }

    private void computeFeatureModel() {
        model.computePCM(originalGroups);
        List<Constraint> constraints = model.performFCA();
        List<Feature> features = model.getFeatures();
        currentBase = model.getBaseFeature();
        model.generateModel(currentBase, features, new ArrayList<>(constraints), true);
    }

    public void redrawVisualization(){
        computeFeatureModel();

        featureGraph.updateGraph(currentBase);
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
    	return featureGraph;
    }
}