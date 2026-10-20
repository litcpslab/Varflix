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
 *  Sophie Öttl - Change Tracking
 ********************************************************************************/

package at.variabilityanalysisgui.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import at.variabilityanalysisgui.changeTracking.DeleteConstraintChild;
import constraints.AlternativeGroup;
import constraints.Constraint;
import constraints.Equivalence;
import constraints.Group;
import constraints.Implication;
import constraints.MutualExclusion;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import org.controlsfx.control.SearchableComboBox;
import variabilityMining.Feature;

public class ConstraintInfoController {
	
	private ConstraintsViewController controller;
	
	private final ScrollPane infoScrollPane;
	private final Label groupInfoLabel;
	private final Label parentFeatureLabel;
	private final Text infoText;
	private final ListView<Feature> groupFeatureList;
	private final Button removeFeatureButton;
	private final Label editLabel;
	private final SearchableComboBox<Feature> featureComboBox;
	private final HBox editButtonBox;
	
	private TreeItem<Constraint> currentInfoItem = null;

	private int currentItemIndex;
	
	public ConstraintInfoController(ConstraintsViewController controller, ScrollPane infoScrollPane, Label groupInfoLabel, Label parentFeatureLabel, Text infoText, 
			ListView<Feature> groupFeatureList, Button removeFeatureButton, Label editLabel, SearchableComboBox<Feature> featureComboBox, HBox editButtonBox, Button infoCloseButton) {
		this.controller = controller;
		this.infoScrollPane = infoScrollPane;
		this.groupInfoLabel = groupInfoLabel;
		this.parentFeatureLabel = parentFeatureLabel;
		this.infoText = infoText;
		this.groupFeatureList = groupFeatureList;
		this.removeFeatureButton = removeFeatureButton;
		this.featureComboBox = featureComboBox;
		this.editButtonBox = editButtonBox;
		this.editLabel = editLabel;
		infoCloseButton.setOnAction(event -> hideInfoPane());
		featureComboBox.setPromptText("Select a feature");
		featureComboBox.setEditable(false);
		featureComboBox.setButtonCell(new ListCell<Feature>() {
			
		    @Override
		    protected void updateItem(Feature item, boolean empty) {
		        super.updateItem(item, empty);
		        if(empty || item == null) {
		        	setText(featureComboBox.getPromptText());
		        } else {
		            setText(item.toString());
		        }
		    }
		});
		infoText.setVisible(false);
		infoScrollPane.setPadding(new Insets(10));
		infoScrollPane.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> {
		    infoText.setWrappingWidth(newVal.getWidth() - 50);
		});
	}

	/*
	 * Hiding the Info window when it is displayed
	 */
	public void hideInfoPane() {
		infoScrollPane.setVisible(false);
	    infoScrollPane.setManaged(false);
	    currentInfoItem = null;
	    groupFeatureList.setItems(FXCollections.emptyObservableList());
	    featureComboBox.getItems().clear();
	    featureComboBox.setValue(null);
	   
	}

	/*
	 * Showing the info window when it is hidden
	 */
	public void showInfoPane(Constraint constraint, TreeItem<Constraint> infoItem) {

		currentInfoItem = infoItem;
		currentItemIndex = controller.getGroupTreeView().getRoot().getChildren().indexOf(currentInfoItem);

		List<Feature> newSelection = new ArrayList<>();
		
		if(constraint instanceof Group group) {
			for(Feature feature: controller.getSelectedFeatures()) {
				 if(!group.getFeatures().contains(feature) && !group.getParent().equals(feature) && !(feature.getName().startsWith("ALT") || feature.getName().startsWith("OR"))) {
					 newSelection.add(feature);

				 }
			 }
			
		}

		featureComboBox.getSelectionModel().clearSelection();
		featureComboBox.setValue(null);
		featureComboBox.getItems().setAll(newSelection);
		 
		if(constraint instanceof Group group) {
			showGroupInfo(group);
		} else {
			showConstraintInfo(constraint);
		}
	}

	/*
	 * Setting up the information in the window for a simple constraint
	 */
	private void showConstraintInfo(Constraint constraint) {
		groupInfoLabel.setText(constraint.getType());	
		
		if(constraint instanceof Implication implication) {
			infoText.setText("If feature " + implication.getFeature1() + " is chosen, feature " + implication.getFeature2() + " has to be selected as well!");
		} else if(constraint instanceof MutualExclusion mutex) {
			infoText.setText("Only one of the two features " + mutex.getFeature1() + " and " + mutex.getFeature2()+ " can be selected to be present in one variant!");
		} else {
			Equivalence equivalence = (Equivalence) constraint;
			infoText.setText("Both of the features " + equivalence.getFeature1() + " and " + equivalence.getFeature2()+ " are always selected together!");
		}
		 
		setNodeVisibility(true, infoScrollPane, groupInfoLabel, infoText);	
		setNodeVisibility(false, parentFeatureLabel, groupFeatureList, editLabel, editButtonBox, featureComboBox, removeFeatureButton);
	}

	/*
	 * Setting up the information in the window for a group constraint
	 */
	private void showGroupInfo(Group constraint) {
		groupInfoLabel.setText(constraint.getType());	
		
		parentFeatureLabel.setText("Parent: " + constraint.getParent().getName());
		parentFeatureLabel.setStyle("-fx-font-weight: bold;");
		groupFeatureList.setItems(FXCollections.observableArrayList(constraint.getFeatures()));
		
		
		if(constraint instanceof AlternativeGroup) {
			infoText.setText("Only one of the following features can be selected to be present in a system variant if "+ constraint.getParent().getName() + " is selected:\r\n");
		} else {
			infoText.setText("At least one of the following features has to be selected if "+ constraint.getParent().getName() +" is selected:\r\n");
		}
		
		setNodeVisibility(true, infoScrollPane, groupInfoLabel, parentFeatureLabel, infoText, groupFeatureList, editLabel, removeFeatureButton, featureComboBox, editButtonBox);
	}
	
	/*
	 * Helper method to show/hide elements
	 */
	 private void setNodeVisibility(boolean visible, Node... node) {
		 for(Node n : node) {
			 if(n != null) {
				 n.setVisible(visible);
	             n.setManaged(visible);
	         }
	     }
	 }
	 
	 /*
	  * Removing a feature from a group by pressing the remove button in the information window
	  */
	 public void removeGroupFeature() {
		 Feature feature = groupFeatureList.getSelectionModel().getSelectedItem();
		 TreeView<Constraint> groupTreeView = controller.getGroupTreeView();
		 
		 if(feature == null) {
			 Alert errorAlert = new Alert(AlertType.ERROR, "Please select a feature above to remove", ButtonType.OK);
			 errorAlert.setHeaderText("Removal Error");
			 errorAlert.show();
		 } else {
			 ButtonType yesButton = new ButtonType("Yes");
			 ButtonType noButton = new ButtonType("No");
			 Alert removeAlert = new Alert(AlertType.CONFIRMATION, "Should the feature " + feature.getName() + " be removed from the group?", yesButton, noButton);
			 removeAlert.setHeaderText("Removal Confirmation");
			 
			 Optional<ButtonType> result = removeAlert.showAndWait();
		     if(result.isPresent() && result.get() == yesButton) {
                 Group group = (Group) currentInfoItem.getValue();
                 controller.removeFeatureFromSelectedGroup(group, feature);
             }
		 }
	 
	 }

	 public ScrollPane getInfoPane() {
		 return infoScrollPane;
	 }
	 
	 public TreeItem<Constraint> getCurrentInfoItem() {
		return currentInfoItem;
	}
}
