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
 	Alexander Stummer - Adapted name changing operations
    Kejda Domi- Added the visualization section
   	Sophie Öttl - Change Tracking
**/

package at.variabilityanalysisgui.controller;

import at.variabilityanalysisai.services.LlmService;
import at.variabilityanalysisgui.changeTracking.RenameGroup;
import at.variabilityanalysisgui.view.DifferenceDirectory;
import at.variabilityanalysisgui.view.FeatureTreeNode;
import constraints.Constraint;
import constraints.SimpleConstraint;
import guiModel.Difference;
import guiModel.Element;
import guiModel.Group;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import com.google.gson.Gson;
import variabilityMining.Feature;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DetailsController {

	Controller mainController;
    FeatureViewController featureController;
    private final ListView<Element> detailSubElementListView;
    private final ScrollPane detailScrollPane;
    private final HBox detailsNameHBox;
    private final Label detailDescriptionLabel;
    private final TextArea detailDescriptionTextArea;
    private final Button detailDescriptionButton;
    private final Label detailLocationLabel;
    private final TextArea detailLocationTextArea;
    private final TextField detailGroupNameTextField;
    private final Button detailChangeNameButton;
    private final Label detailOccurrenceLabel;
    private final ListView<String> detailOccurrencesListView;
    private final Label detailElementLabel;
    private final TextArea detailElementData;
    private final Label detailSubElementLabel;
    private final Label detailNamingHistoryLabel;
    private final ListView<String> detailNamingHistoryListView;
    private final Button detailChangeBackButton;
    private final HBox detailNamingHistoryHBox;
    private final Button askAiButton;
    private final LlmService llmService = new LlmService("IEC61499");

    private TreeItem<FeatureTreeNode> currentDetailItem = null;

    public DetailsController(Controller mainController,
                             FeatureViewController featureController,
                             ListView<Element> detailSubElementListView,
                             ScrollPane detailScrollPane,
                             HBox detailsNameHBox,
                             Label detailLocationLabel,
                             TextArea detailLocationTextArea,
                             TextField detailGroupNameTextField,
                             Button detailChangeNameButton,
                             Label detailDescriptionLabel,
                             TextArea detailDescriptionTextArea,
                             Button detailDescriptionButton,
                             Label detailOccurrenceLabel,
                             ListView<String> detailOccurrencesListView,
                             Label detailElementLabel,
                             TextArea detailElementData,
                             Label detailSubElementLabel,
                             Button detailCloseButton,
                             Label detailNamingHistoryLabel,
                             ListView<String> detailNamingHistoryListView,
                             Button detailChangeBackButton,
                             HBox detailNamingHistoryHBox,
                             Button askAiButton) {
        this.mainController = mainController;
    	this.featureController = featureController;
        this.detailSubElementListView = detailSubElementListView;
        this.detailScrollPane = detailScrollPane;
        this.detailsNameHBox = detailsNameHBox;
        this.detailDescriptionLabel = detailDescriptionLabel;
        this.detailDescriptionTextArea = detailDescriptionTextArea;
        this.detailDescriptionButton = detailDescriptionButton;
        this.detailLocationLabel = detailLocationLabel;
        this.detailLocationTextArea = detailLocationTextArea;
        this.detailGroupNameTextField = detailGroupNameTextField;
        this.detailChangeNameButton = detailChangeNameButton;
        this.detailOccurrenceLabel = detailOccurrenceLabel;
        this.detailOccurrencesListView = detailOccurrencesListView;
        this.detailElementLabel = detailElementLabel;
        this.detailElementData = detailElementData;
        this.detailSubElementLabel = detailSubElementLabel;

        this.detailNamingHistoryLabel = detailNamingHistoryLabel;
        this.detailNamingHistoryListView = detailNamingHistoryListView;
        this.detailChangeBackButton = detailChangeBackButton;
        this.detailNamingHistoryHBox = detailNamingHistoryHBox;
        this.askAiButton = askAiButton;

        this.askAiButton.setOnAction(this::handleAskAiAction);

        this.detailChangeNameButton.setOnAction(event -> updateGroupName(false));
        this.detailGroupNameTextField.setOnAction(event -> updateGroupName(false));
        this.detailDescriptionButton.setOnAction(event -> updateGroupDescription());
        this.detailChangeBackButton.setOnAction(event -> updateGroupName(true));
        // Configure the optional detail elements list view display
        detailSubElementListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Element item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VBox vbox = new VBox(2);
                    Label label = new Label(item.getLocation());
                    label.setStyle("-fx-font-weight: bold;");
                    vbox.getChildren().add(label);
                    if (item.getDescription() != null && !item.getDescription().isEmpty()) {
                        Label descLabel = new Label(item.getDescription());
                        descLabel.setWrapText(true);
                        vbox.getChildren().add(descLabel);
                    }
                    setGraphic(vbox);
                    setText(null);
                }
            }
        });
        detailCloseButton.setOnAction(event -> hideDetailsPane());
    }

	// Detail Pane
    public void showDetailsPane(Difference diff, TreeItem<FeatureTreeNode> item) {
        currentDetailItem = item;
        if (diff instanceof Group) {
            showDetailsPaneGroup((Group) diff);
        } else if (diff instanceof Element) {
            showDetailsPaneElement((Element) diff);
        } else if (diff instanceof DifferenceDirectory) {
            showDetailsPaneDifferenceDirectory((DifferenceDirectory) diff);
        } else {
            System.out.println("No details available for this item.");
            hideDetailsPane();
        }
    }

    private void showDetailsPaneDifferenceDirectory(DifferenceDirectory dir) {

        setNodeVisibility(true, detailLocationLabel, detailLocationTextArea, detailSubElementLabel, detailSubElementListView, detailScrollPane);
        setNodeVisibility(false, detailsNameHBox, detailOccurrenceLabel, detailOccurrencesListView, detailElementLabel, detailElementData);

        detailElementData.setEditable(false);
        detailLocationTextArea.setText(dir.getPath());
        detailSubElementListView.setItems(currentDetailItem.getChildren().stream().map(i -> (Element) i.getValue().getData()).collect(Collectors.toCollection(FXCollections::observableArrayList)));

    }

    private void showDetailsPaneGroup(Group group) {
        detailGroupNameTextField.setText(group.getName().get());
        //group.getName().bind(detailGroupNameTextField.textProperty());
        detailSubElementListView.setItems(FXCollections.observableArrayList(group.getElements()));
        detailOccurrencesListView.setItems(FXCollections.observableArrayList(group.getOccurrences()));
        detailNamingHistoryListView.setItems(group.getPreviousNames());

        setNodeVisibility(true, detailOccurrenceLabel, detailOccurrencesListView, detailSubElementLabel, detailSubElementListView, detailScrollPane, detailsNameHBox, detailNamingHistoryLabel, detailNamingHistoryListView, detailChangeBackButton, detailNamingHistoryHBox, detailChangeNameButton);
        setNodeVisibility(false, detailLocationLabel, detailLocationTextArea, detailElementLabel, detailElementData);

        detailGroupNameTextField.setEditable(true);
        detailElementData.setEditable(false);
    }

    private void showDetailsPaneElement(Element element) {
        detailGroupNameTextField.setText(element.getName().get());
        element.getName().bind(detailGroupNameTextField.textProperty());
        detailElementData.setText(element.getDescription());
        detailSubElementListView.setItems(FXCollections.observableArrayList(element));
        detailLocationTextArea.setText(element.getLocation());

        setNodeVisibility(false, detailOccurrenceLabel, detailOccurrencesListView, detailNamingHistoryLabel, detailNamingHistoryListView, detailChangeBackButton, detailNamingHistoryHBox, detailChangeNameButton);
        setNodeVisibility(true, detailsNameHBox, detailLocationLabel, detailLocationTextArea, detailElementLabel, detailElementData, detailScrollPane);
        setNodeVisibility(currentDetailItem.getValue().isDirectory(), detailSubElementLabel, detailSubElementListView);

        detailGroupNameTextField.setEditable(false);
        detailLocationTextArea.setEditable(false);
        detailElementData.setEditable(false);

    }

    private void setNodeVisibility(boolean visible, Node... node) {
        for (Node n : node) {
            if (n != null) {
                n.setVisible(visible);
                n.setManaged(visible);
            }
        }
    }

    public void hideDetailsPane() {
        detailScrollPane.setVisible(false);
        detailScrollPane.setManaged(false);
        currentDetailItem = null;
        detailOccurrencesListView.setItems(FXCollections.emptyObservableList());
        detailSubElementListView.setItems(FXCollections.emptyObservableList());
    }
    
    private void updateGroupName(boolean changeBack) {
    	FeatureTreeNode currentTreeNode = currentDetailItem.getValue();
        String candidateName = detailGroupNameTextField.getText();
        if (changeBack) {
            candidateName = detailNamingHistoryListView.getSelectionModel().getSelectedItem();
            if (candidateName == null) {
                return;
            }
        }
		Group group = (Group) currentDetailItem.getValue().getData();
        if(isValidName(candidateName)) {
            String oldName = group.getName().get();
            group.addPreviousName(oldName);
            featureController.getChangeTracker().addUndo(new RenameGroup(this, group, oldName, candidateName));
			group.getName().setValue(candidateName);
			currentDetailItem.setValue(null);
			currentDetailItem.setValue(currentTreeNode);
            setModelFeatureName(oldName, candidateName);
            setDetailGroupNameTextField(candidateName);
            mainController.redrawVisualization();
        }
   	}

    public void updateGroupDescription()  {
        Group group = (Group) currentDetailItem.getValue().getData();
        String newDescription = detailDescriptionTextArea.getText();
        group.setDescription(newDescription);
    }

    public void setModelFeatureName(String oldName, String newName) {
        for(Feature feature : mainController.getAllTreeFeaturesByName(oldName)) {
            feature.setName(newName);
        }
    }

    public void setDetailGroupNameTextField(String name) {
        detailGroupNameTextField.setText(name);
    }

    private boolean isValidName(String candidateName) {
    	Alert errorAlert = new Alert(AlertType.ERROR, "", ButtonType.OK);
		errorAlert.setHeaderText("Invalid Name Error");

		if(candidateName.isEmpty()) {
			errorAlert.setContentText("The proposed name is empty!");
		} else if(candidateName.contains(" ")) {
			errorAlert.setContentText("The proposed name contains a whitespace. This is not allowed!");
		} else if(Character.isDigit(candidateName.charAt(0))) {
			errorAlert.setContentText("The proposed name starts with a number. This is not allowed!");
		} else if(mainController.getOriginalGroups().stream().anyMatch(group -> group.getName().getValue().equals(candidateName))) {
			errorAlert.setContentText("The proposed name already exists. Duplicate names are not allowed!");
		}

		if(!errorAlert.getContentText().isEmpty()) {
			errorAlert.showAndWait();
			return false;
		}
		
		return true;
	}

    public TreeItem<FeatureTreeNode> getCurrentDetailItem() {
		return currentDetailItem;
	}

    protected void handleAskAiAction(ActionEvent event) {
        if (currentDetailItem != null && currentDetailItem.getValue() != null &&
                currentDetailItem.getValue().getData() instanceof Group group) {
            String occurences = String.join("\n", group.getOccurrences());
            String elements = group.getElements().stream()
                    .map(element -> element.getName().getValue())
                    .collect(Collectors.joining("\n"));
            String constraints = mainController.getConstraints().stream()
                    .filter(constraint -> {
                        if (constraint instanceof constraints.Group) {
                            constraints.Group gc = (constraints.Group) constraint;
                            return gc.getFeatures().stream().anyMatch(feature -> feature.getName().contains(group.getName().getValue()))
                                    || gc.getParent().getName().equals(group.getName().getValue());
                        } else if (constraint instanceof SimpleConstraint) {
                            SimpleConstraint sc = (SimpleConstraint) constraint;
                            return sc.getFeature1().getName().equals(group.getName().getValue())
                                    || sc.getFeature2().getName().equals(group.getName().getValue());
                        } else {
                            return false;
                        }
                    })
                    .map(Constraint::toString)
                    .collect(Collectors.joining("\n"));
            String old_names = group.getName().getValue() + "\n" + String.join("\n", group.getPreviousNames().stream().toList());
            String existing_names = mainController.getOriginalGroups().stream()
                    .map(g -> g.getName().getValue())
                    .filter(name -> !name.startsWith("Group") && !name.equals("Core"))
                    .collect(Collectors.joining("\n"));

            askAiButton.setDisable(true);
            askAiButton.setText("Thinking...");

            CompletableFuture.supplyAsync(() -> {
                try {
                    return llmService.suggestNameAndDescription(
                            group,
                            mainController.getConstraints(),
                            mainController.getOriginalGroups()
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).thenAccept(result -> {
                Platform.runLater(() -> {
                    if (result != null) {
                        detailGroupNameTextField.setText(result.getSuggestedName());
                        detailDescriptionTextArea.setText(result.getShortDescription());
                    }
                    askAiButton.setDisable(false);
                    askAiButton.setText("✨ Ask AI");
                });
            }).exceptionally(e -> {
                Platform.runLater(() -> {
                    e.printStackTrace();
                    askAiButton.setDisable(false);
                    askAiButton.setText("❌ Error");

                    Alert alert = new Alert(AlertType.ERROR,
                            "AI Analysis failed: " + e.getCause().getMessage(),
                            ButtonType.OK);
                    alert.show();
                });
                return null;
            });
        }
    }
}
