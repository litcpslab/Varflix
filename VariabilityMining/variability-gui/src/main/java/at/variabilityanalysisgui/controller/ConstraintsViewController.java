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
 *  Kejda Domi- Added the visualization section
 *  Sophie Öttl - Change Tracking
********************************************************************************/

package at.variabilityanalysisgui.controller;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import at.variabilityanalysisgui.changeTracking.*;
import at.variabilityanalysisgui.visualization.TreeGraph;
import org.controlsfx.control.CheckComboBox;

import constraints.AlternativeGroup;
import constraints.Constraint;
import constraints.Equivalence;
import constraints.Group;
import constraints.Implication;
import constraints.MutualExclusion;
import constraints.OrRelation;
import constraints.SimpleConstraint;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.controlsfx.control.SearchableComboBox;
import variabilityMining.ConstraintFileIO;
import variabilityMining.Feature;
import variabilityMining.JSONConstraints;
import variabilityMining.VarflixAPI;

public class ConstraintsViewController {

	private ConstraintInfoController infoController;
	
	//Filter items
    @FXML private TextField searchTextField;
    @FXML private Button filterButton;
    private Set<TreeItem<Constraint>> unfilteredItems; 
    private CheckComboBox<String> typeCheckComboBox;
    private CheckComboBox<Feature> featureCheckComboBox;
    
	
	//GroupInfoView InfoController
    @FXML private ScrollPane infoScrollPane;
    @FXML private HBox detailsNameHBox;
    @FXML private Label groupInfoLabel;
    @FXML private Label parentFeatureLabel;
    @FXML private Text infoText;
    @FXML private ListView<Feature> groupFeatureListView;
    @FXML private TextField detailGroupNameTextField;
    @FXML private Button removeFeatureButton;
    @FXML private Label editLabel;
    @FXML private SearchableComboBox<Feature> featureComboBox;
    @FXML private HBox editButtonBox;
    @FXML private Button infoCloseButton;
    @FXML private TreeView<Constraint> groupTreeView;
    
    //Menu Buttons
    @FXML private Button proceedButton;
    @FXML private Button addConstraintButton;
    @FXML private Button generateButton;

	@FXML private Button undoButton;
	@FXML private Button redoButton;
    
    private ContextMenu constraintFilterMenu;
    
    private VarflixAPI model;
    private List<Constraint> constraints;
    private List<Feature> features;
    private Feature currentBase;
    private TreeGraph featureGraph;
    
    //private Controller mainController;
    private boolean isGroupView = true;

	private ChangeTracker<ConstraintsViewController, ConstraintInfoController> changeTracker;

	private Controller mainController;

    public void init() {
    	this.infoController = new ConstraintInfoController(this, infoScrollPane, groupInfoLabel, parentFeatureLabel, infoText, groupFeatureListView, removeFeatureButton, editLabel, featureComboBox, editButtonBox, infoCloseButton);
    	searchTextField.textProperty().addListener((observable, oldValue, newValue) -> {
    	    if(newValue != null && !newValue.isEmpty()) {
    	    	filterConstraints(newValue);
    	    } else {
    	    	groupTreeView.getRoot().getChildren().clear();
    			groupTreeView.getRoot().getChildren().addAll(unfilteredItems);
    	    }
    	});
    	unfilteredItems = new HashSet<>();

    	changeTracker = new ChangeTracker<>(this, infoController);
		undoButton.disableProperty().bind(changeTracker.canUndoProperty().not());
		redoButton.disableProperty().bind(changeTracker.canRedoProperty().not());

    	infoScrollPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
    		if(newScene != null) {
    	        newScene.windowProperty().addListener((obs2, oldWin, newWin) -> {
    	            if (newWin != null) {
    	            	Stage stage = (Stage) infoScrollPane.getScene().getWindow();
    	        		stage.setOnCloseRequest(new EventHandler<WindowEvent>(){

    	    				@Override
    	    				public void handle(WindowEvent event) {
    	    					model.generateModel(currentBase, features, new ArrayList<>(constraints), true);
    	    				}
    	        			
    	        		});
    	            }
    	        });
    	    }
    	});
    	
    	groupTreeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    	
    	KeyCombination undoCombination = new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN);
		KeyCombination redoCombination = new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN);
		
		groupFeatureListView.sceneProperty().addListener((obs, oldScene, newScene) -> {
			if (newScene != null) {
				newScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
					if (undoCombination.match(event)) {
						undo();
						event.consume();
					} else if (redoCombination.match(event)) {
						redo();
						event.consume();
					} else if(KeyCode.DELETE.equals(event.getCode())) {
						List<TreeItem<Constraint>> selectedItems =
                                groupTreeView.getSelectionModel().getSelectedItems().stream().filter(Objects::nonNull).toList();
						
						for(TreeItem<Constraint> item: selectedItems) {
							deleteConstraintItem(item.getValue(), item);
						}
					}
				});
			}
		});
    }
    
	/*
	 * Method to load the constraints from a file and display items in the GUI 
	 */
	@FXML
    public void handleLoadAction() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Constraints file");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        fileChooser.setInitialDirectory(new File("./"));
        File selectedFile = fileChooser.showOpenDialog(groupTreeView.getScene().getWindow());

        JSONConstraints jsonConstraints = ConstraintFileIO.readInformationfromFile(selectedFile);
        constraints = new ArrayList<>();
        constraints.addAll(jsonConstraints.getConstraints());
        constraints.addAll(jsonConstraints.getGroups());
        features = jsonConstraints.getFeatures();
        currentBase = jsonConstraints.getBase();
        
        featureCheckComboBox.getItems().clear();
        featureCheckComboBox.getItems().addAll(features);
        featureCheckComboBox.getCheckModel().clearChecks();
        
        List<Constraint> currentItems = isGroupView ? constraints.stream().filter(c -> c instanceof Group).toList():constraints.stream().filter(c -> c instanceof SimpleConstraint).toList();
        setUpTreeItems(currentItems);
    }
	
	/*
	 * Method to save the current status of the constraints to a .json file
	 */
	@FXML
    public void handleSaveAction() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Constraints to file");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        fileChooser.setInitialDirectory(new File("./"));
        fileChooser.setInitialFileName("constraints");
        File selectedFile = fileChooser.showSaveDialog(groupTreeView.getScene().getWindow());

        ConstraintFileIO.writeConstraintsToFile(selectedFile, constraints, features, currentBase);
    }
	
	
	/*
	 * Defining the action performed when pressing the add button to add a new feature to a group
	 */
	@FXML
	public void handleAddGroupFeatureAction(ActionEvent e) {
		Feature addFeature = featureComboBox.getValue();
		int comboBoxIndex = featureComboBox.getSelectionModel().getSelectedIndex();
		
		Constraint constraint = infoController.getCurrentInfoItem().getValue();
		boolean shouldBeAdded = true;
		boolean featureInGroup = false;
		
		if(addFeature == null) {
			return;
		}
		
		if(constraint instanceof Group group) {

            List<Group> groups = constraints.stream().filter(c -> c instanceof Group).map(c -> ((Group) c)).toList();
			
			for(Group g: groups) {
				if(g != group && g.getFeatures().contains(addFeature)) {
					ButtonType yesButton = new ButtonType("Yes");
					ButtonType noButton = new ButtonType("No");
					ButtonType buttonKeepConstraints = new ButtonType("Keep Constraints");
					Alert warningAlert = new Alert(AlertType.WARNING, "The feature you are trying to add is already part of another group. Should it be moved here? (It will be removed from its current group)", yesButton, buttonKeepConstraints, noButton);
					warningAlert.setHeaderText("Addition Warning");
					
					Optional<ButtonType> result = warningAlert.showAndWait();
					featureInGroup = true;
				    if(result.isPresent() && result.get() == yesButton) {
				    	g.removeFeature(addFeature);
						changeTracker.addUndo(new AddConstraintChildSet(addFeature, g, group, new LinkedList<>(), comboBoxIndex));

				    } else if(result.isPresent() && result.get() == buttonKeepConstraints) {
						List<Constraint> newConstraints = new LinkedList<>();

				    	if(group instanceof AlternativeGroup) {
				    		 
				    		for(Feature f: g.getFeatures()) {
				    			if(f != addFeature) {
									newConstraints.add(new MutualExclusion(addFeature, f));
				    			}
				    		}
				    	}

						newConstraints.add(new Implication(addFeature, g.getParent()));
				    	g.removeFeature(addFeature);
						changeTracker.addUndo(new AddConstraintChildSet(addFeature, g, group, newConstraints, comboBoxIndex));
						constraints.addAll(newConstraints);

				    } else {
				    	shouldBeAdded = false;
				    }

				}

			}
			if (!featureInGroup) {
				changeTracker.addUndo(new AddConstraintChild(addFeature, group, comboBoxIndex));
			}

			if(shouldBeAdded && !group.getFeatures().contains(addFeature) && !group.getParent().equals(addFeature)) {
				group.getFeatures().add(addFeature);
				featureComboBox.getSelectionModel().clearSelection();
				List<Feature> newList = new ArrayList<>(featureComboBox.getItems());
				newList.remove(addFeature);
				featureComboBox.setItems(FXCollections.observableArrayList(newList));
				groupFeatureListView.getItems().add(addFeature);
			}
			
		} 
		
		groupTreeView.refresh();
		featureComboBox.setValue(null);
        updateConstraintModel();
	}

	public void updateConstraintModel(){
        constraints = model.generateModel(currentBase, features, constraints, false);
        
        featureGraph.updateGraph(currentBase);
        
        if(isGroupView) {
        	initializeTreeView(constraints.stream().filter(c -> c instanceof Group).toList());
        }
        
    }

	
	/*
	 * Handling the action performed to remove a group from a feature
	 */
	@FXML
	public void handleRemoveAction(ActionEvent e) {
		infoController.removeGroupFeature();
		groupTreeView.refresh();
        updateConstraintModel();
	}
	
	/*
	 * Defining the action that is performed when pressing the proceed button (switching scenes and loading the new data)
	 */
	@FXML
	public void handleProceedAction() {
		groupTreeView.setRoot(null);
		
		List<Constraint> simpleConstraints = constraints.stream().filter(c -> !(c instanceof Group)).toList();
		setUpTreeItems(simpleConstraints);
		
		generateButton.setVisible(true);
		
		addConstraintButton.setVisible(true);
		
		addConstraintButton.setOnAction(e -> addConstraint(e));
		
		infoController.hideInfoPane();
		
		proceedButton.setText("Group Constraints");
		
		proceedButton.setOnAction(e -> {
			handleGoBackAction();
		});
		
		isGroupView = false;
		
		typeCheckComboBox.getItems().clear();
		typeCheckComboBox.getItems().addAll("Mutual Exclusion", "Implication", "Equivalence");
	}
	
	/*
	 * Performing the generate model action
	 */
	@FXML
	public void handleGenerateAction() {
        List<Constraint> constraintsList = new ArrayList<>(constraints);
		model.generateModel(currentBase, features, constraintsList, false);
		Alert confirmationAlert = new Alert(AlertType.CONFIRMATION, "The model was successfully generated in the file model.uvl!", ButtonType.FINISH);
		confirmationAlert.setHeaderText("Generation Confirmation");
		 
		Optional<ButtonType> result = confirmationAlert.showAndWait();
	    if(result.isPresent() && result.get() == ButtonType.FINISH) {
	    	//Platform.exit();
	    }
	}
	
	public void resetConstraintsViewButtons() {
		changeTracker.clearStack();
		infoController.hideInfoPane();
		generateButton.setVisible(false);
		addConstraintButton.setVisible(false);
		proceedButton.setText("Simple Constraints");
		proceedButton.setOnAction(e -> handleProceedAction());
	}
	
    /*
     * Filter items based on the input string entered in the search field
     */
	private void filterConstraints(String newValue) {
		
		List<TreeItem<Constraint>> filteredItems = unfilteredItems.stream().filter(e -> e.getValue().toString().toLowerCase().contains(newValue.toLowerCase())).toList();
		
		groupTreeView.getRoot().getChildren().clear();
		groupTreeView.getRoot().getChildren().addAll(filteredItems);
	}

	
	/*
	 * Setup and logic for the Add Constraint window
	 */
	private void addConstraint(ActionEvent e) {
		
		Stage popupStage = new Stage();
        popupStage.initOwner((Stage)((Node) e.getSource()).getScene().getWindow()); 
        popupStage.initModality(Modality.NONE); 
        popupStage.setAlwaysOnTop(true); 
        popupStage.setTitle("Add a constraint!");
        popupStage.setResizable(false);
		
		ComboBox<String> constraintTypeComboBox = new ComboBox<>(FXCollections.observableArrayList("Implication", "Equivalence", "Mutual Exclusion"));
		constraintTypeComboBox.setPromptText("Select constraint type");
		constraintTypeComboBox.setMaxWidth(Double.MAX_VALUE);
		
		List<Feature> selectableFeatures = features.stream().filter(f -> !(f.getName().startsWith("ALT") || f.getName().startsWith("OR"))).toList();
		
		ComboBox<Feature> leftFeatureComboBox = new ComboBox<>(FXCollections.observableArrayList(selectableFeatures));
		leftFeatureComboBox.setPromptText("Select left feature");
		leftFeatureComboBox.setMaxWidth(Double.MAX_VALUE);
		
		ComboBox<Feature> rightFeatureComboBox = new ComboBox<>(FXCollections.observableArrayList(selectableFeatures));
		rightFeatureComboBox.setPromptText("Select right feature");
		rightFeatureComboBox.setMaxWidth(Double.MAX_VALUE);
		
		Button addButton = new Button("Add");
		addButton.setDefaultButton(true);
		addButton.setMaxWidth(Double.MAX_VALUE);

		EventHandler<ActionEvent> event = new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {
				String type = constraintTypeComboBox.getValue();

				Feature left = leftFeatureComboBox.getValue();
				Feature right = rightFeatureComboBox.getValue();
				if (left != right) {
					SimpleConstraint constraint = switch (type) {
                        case "Implication" -> new Implication(left, right);
                        case "Mutual Exclusion" -> new MutualExclusion(left, right);
                        case "Equivalence" -> new Equivalence(left, right);
                        default -> null;//new SimpleConstraint(left, right, type);
                    };

                    if (constraint != null) {
						if (!constraintIsPresent(constraint)) {
							TreeItem<Constraint> addItem = addSimpleConstraintTreeItem(constraint);
							changeTracker.addUndo(new AddSimpleConstraint(addItem, groupTreeView.getRoot().getChildren().indexOf(addItem)));
						}
					}

				} else {
					Alert addError = new Alert(AlertType.ERROR, "The same feature can't be chosen for both sides of the logical constraint!");
					addError.show();
				}

				popupStage.close();
			}
		};
		
		addButton.setOnAction(event);
		
		//VBox layout = new VBox(10, constraintTypeComboBox, leftFeatureComboBox, rightFeatureComboBox, addButton);
		VBox layout = new VBox(12);
		layout.setPadding(new Insets(20));
		layout.setAlignment(Pos.TOP_CENTER);
		layout.getChildren().addAll(new Label("Constraint Type"), constraintTypeComboBox, 
				new Label("Left Feature"), leftFeatureComboBox, 
				new Label("Right Feature"), rightFeatureComboBox);
		
		HBox buttonBar = new HBox(10, addButton);
		buttonBar.setAlignment(Pos.CENTER_RIGHT);
		HBox.setHgrow(addButton, Priority.ALWAYS);
		layout.getChildren().add(buttonBar);
		
		Scene scene = new Scene(layout);
		scene.getStylesheets().add(getClass().getResource("/popup.css").toExternalForm());
		popupStage.setScene(scene);
		popupStage.sizeToScene();
		popupStage.setMinWidth(280);
	    popupStage.show();
	}

	private boolean constraintIsPresent(SimpleConstraint constraint) {

		for(Constraint c: constraints){
			if(c instanceof SimpleConstraint compareConstraint){
                if((compareConstraint.getFeature1().equals(constraint.getFeature1()) && compareConstraint.getFeature2().equals(constraint.getFeature2())) ||
						(compareConstraint.getFeature1().equals(constraint.getFeature2()) && compareConstraint.getFeature2().equals(constraint.getFeature1()))){
                    return !compareConstraint.getType().equals("Implication") || !constraint.getType().equals("Equivalence");
				}
			}
		}

		return true;
	}

	public TreeItem<Constraint> addSimpleConstraintTreeItem(Constraint constraint) {
		TreeItem<Constraint> addItem = new TreeItem<Constraint>(constraint);
		Button button = new Button("X");
		button.setOnAction(e -> deleteConstraintItem(constraint, addItem));

		addItem.setGraphic(button);

		groupTreeView.getRoot().getChildren().add(addItem);
		unfilteredItems.add(addItem);
		groupTreeView.refresh();

		return addItem;
	}

	/*
	 * Handling the action to press the go back button
	 */
	private void handleGoBackAction() {
		groupTreeView.setRoot(null);
		
		List<Constraint> groups = constraints.stream().filter(c -> c instanceof Group).toList();
		setUpTreeItems(groups);
		proceedButton.setText("Simple Constraints");
		
		proceedButton.setOnAction(e -> {
			handleProceedAction();
		});
		
		addConstraintButton.setVisible(false);
		
		infoController.hideInfoPane();
		
		isGroupView = true;
		
		typeCheckComboBox.getItems().clear();
		typeCheckComboBox.getItems().addAll("Or Group", "Alternative");
	}
	
	public void deleteConstraintItem(Constraint constraint, TreeItem<Constraint> constraintItem) {
		if(constraint instanceof SimpleConstraint) {
			ButtonType yesButton = new ButtonType("Yes");
			ButtonType noButton = new ButtonType("No");
			Alert removeAlert = new Alert(AlertType.CONFIRMATION, "Should the constraint " + constraintItem.getValue() + " be removed?", yesButton, noButton);
			removeAlert.setHeaderText("Removal Confirmation");

			Optional<ButtonType> result = removeAlert.showAndWait();
			if(result.isPresent() && result.get() == yesButton) {
				int index = groupTreeView.getRoot().getChildren().indexOf(constraintItem);
				groupTreeView.getRoot().getChildren().remove(constraintItem);
				constraints.remove(constraintItem.getValue());
				unfilteredItems.remove(constraintItem);
				changeTracker.addUndo(new DeleteConstraint(constraintItem, index, false));
				updateConstraintModel();
			}
		} else {
			ButtonType yesButton = new ButtonType("Yes");
			ButtonType noButton = new ButtonType("No");
			Alert removeAlert = new Alert(AlertType.CONFIRMATION, "Should the constraint " + constraint + " be removed?", yesButton, noButton);
			removeAlert.setHeaderText("Removal Confirmation");
			
			ButtonType buttonKeepConstraints = new ButtonType("Keep constraints");
			
			if(constraint instanceof Group) {
				removeAlert.getButtonTypes().add(buttonKeepConstraints);
			}
			 
			Optional<ButtonType> result = removeAlert.showAndWait();
		    if(result.isPresent() && result.get() == yesButton) {
				int index = groupTreeView.getRoot().getChildren().indexOf(constraintItem);
				groupTreeView.getRoot().getChildren().remove(constraintItem);
		    	unfilteredItems.remove(constraintItem);
		    	this.constraints.remove(constraint);
				changeTracker.addUndo(new DeleteConstraint(constraintItem, index, constraint instanceof Group));
				updateConstraintModel();
		    } else if(result.isPresent() && result.get() == buttonKeepConstraints) {
				List<Constraint> newConstraints = new LinkedList<>();
				int index = groupTreeView.getRoot().getChildren().indexOf(constraintItem);
		    	groupTreeView.getRoot().getChildren().remove(constraintItem);
		    	this.constraints.remove(constraint);
		    	resolveGroupConstraint(constraint, newConstraints);
		    	unfilteredItems.remove(constraintItem);
				changeTracker.addUndo(new DeleteGroupConstraintSet(constraintItem, index, newConstraints));
				updateConstraintModel();
		    }
		}
	}

	/*
	 * Loading all the elements that should be displayed initially in the window and the logic to show the info window
	 */
	private void initializeTreeView(List<Constraint> groups) {
		setUpTreeItems(groups);
		
		groupTreeView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			if(oldValue != null) {
                oldValue.setValue(oldValue.getValue()); 
            }
            if(newValue != null && newValue.getValue() != null) {
                Constraint constraint = newValue.getValue();
                showInfoPane(constraint, newValue);
            }
        });		
	}
	
	/*
	 * Method to show the Info window
	 */
	private void showInfoPane(Constraint constraint, TreeItem<Constraint> newValue) {
		infoController.showInfoPane(constraint, newValue);
	}
	
	public void setModel(VarflixAPI model) {
		this.model = model;
		initData();
	}

	/*
	 * Initializing the constraints derivation and setting up the window initially
	 */
	private void initData() {
		List<Constraint> constraints = model.performFCA();
		List<Feature> features = model.getFeatures();
		Feature base = model.getBaseFeature();
		constraints = model.generateModel(base, features, new ArrayList<>(constraints), true);
		if(this.constraints != null && this.features != null && currentBase != null) {
			if(!(this.constraints.equals(constraints) && 
					this.features.equals(features) && currentBase.equals(base))) {
				this.constraints = constraints;
				this.features = features;
				currentBase = base;	
			} 
		} else {
			this.constraints = constraints;
			this.features = features;
			currentBase = base;
		}
	
		if(featureGraph == null) {
			featureGraph = mainController.getFeatureGraph();
		}
		
		featureGraph.updateGraph(base);
		
		initializeTreeView(this.constraints.stream().filter(c -> c instanceof Group).collect(Collectors.toList()));	
		setUpFilterMenu();
	}
	
	/*
	 * Initializing the filter button and the submenu with the different filters
	 */
	private void setUpFilterMenu() {
		constraintFilterMenu = new ContextMenu();
		
		constraintFilterMenu.setMinWidth(300);
		constraintFilterMenu.setMaxWidth(300);
		constraintFilterMenu.setPrefWidth(300);
		    
		featureCheckComboBox = new CheckComboBox<>();
		featureCheckComboBox.getItems().addAll(features);
		featureCheckComboBox.setMinWidth(150);
		featureCheckComboBox.setPrefWidth(150);
		featureCheckComboBox.setMaxWidth(150);
		Label featureLabel = new Label("Features");
		HBox spacer = new HBox();
	    spacer.setMinWidth(21);
	    spacer.setMaxWidth(21);
	    spacer.setPrefWidth(21);
	    
		HBox featureFilterBox = new HBox(featureLabel, spacer, featureCheckComboBox);
		featureCheckComboBox.getCheckModel().getCheckedItems().addListener((ListChangeListener<Feature>) c -> {
			
			Set<TreeItem<Constraint>> addItems = computeFilteredItems();
			
			if(!addItems.isEmpty()) {
				groupTreeView.getRoot().getChildren().clear();
				groupTreeView.getRoot().getChildren().addAll(addItems);
			} else {
				groupTreeView.getRoot().getChildren().clear();
			}
		});
		
		
		Label typeLabel = new Label("Type");
		HBox typeSpacer = new HBox();
	    typeSpacer.setMinWidth(40);
	    typeSpacer.setMaxWidth(40);
	    typeSpacer.setPrefWidth(40);
	    
		typeCheckComboBox = new CheckComboBox<>();
		typeCheckComboBox.getItems().addAll("Or Group", "Alternative");
		typeCheckComboBox.setMinWidth(150);
		typeCheckComboBox.setPrefWidth(150);
		typeCheckComboBox.setMaxWidth(150);
		HBox typeFilterBox = new HBox(typeLabel, typeSpacer, typeCheckComboBox);
		
		typeCheckComboBox.getCheckModel().getCheckedItems().addListener((ListChangeListener<String>) c -> {
			
			Set<TreeItem<Constraint>> addItems = computeFilteredItems();
			
			if(!addItems.isEmpty()) {
				groupTreeView.getRoot().getChildren().clear();
				groupTreeView.getRoot().getChildren().addAll(addItems);
			} else {
				groupTreeView.getRoot().getChildren().clear();
			}
			
		});
		
		CustomMenuItem featureMenuItem = new CustomMenuItem(featureFilterBox, false);
        constraintFilterMenu.getItems().add(featureMenuItem);
        
        CustomMenuItem typeMenuItem = new CustomMenuItem(typeFilterBox, false);
        constraintFilterMenu.getItems().add(typeMenuItem);
		
		constraintFilterMenu.hide();
	    filterButton.setOnAction(event -> {
	    	if(constraintFilterMenu.isShowing()) {
	    		constraintFilterMenu.hide();
	        } else {
	        	constraintFilterMenu.show(filterButton, javafx.geometry.Side.BOTTOM, 0, 0);
	        }
	    });
		
	}
	
	/*
	 * Helper method for finding all display items that contain a specific feature
	 */
	private Set<TreeItem<Constraint>> findRelevantItems(Feature feature){
		Set<TreeItem<Constraint>> relevantItems = new HashSet<>();
		
    	if(isGroupView) {
    		for(TreeItem<Constraint> addConstraint: unfilteredItems) {
    			Group group = (Group) addConstraint.getValue();
    			if(group.getParent().equals(feature) || group.getFeatures().contains(feature)) {
    				relevantItems.add(addConstraint);
    			}
    		}
    	} else {
    		for(TreeItem<Constraint> addConstraint: unfilteredItems) {
    			SimpleConstraint sc = (SimpleConstraint) addConstraint.getValue();
    			if(sc.getFeature1().equals(feature) || sc.getFeature2().equals(feature)) {
    				relevantItems.add(addConstraint);
    			}
    		}
    	}
    		
    	return relevantItems;
	}
	
	private Set<TreeItem<Constraint>> computeFilteredItems(){
		
		ObservableList<Feature> checkedFeatures = featureCheckComboBox.getCheckModel().getCheckedItems();
		ObservableList<String> checkedTypes = typeCheckComboBox.getCheckModel().getCheckedItems();
		
		if(checkedTypes.isEmpty() && checkedFeatures.isEmpty()) {
			return unfilteredItems;
		}
		
		Set<TreeItem<Constraint>> addItems = new HashSet<>();
		
		for(Feature feature: checkedFeatures) {
			addItems.addAll(findRelevantItems(feature));	 
		}
		
		for(String type: checkedTypes) {
			if(addItems.isEmpty()) {
				addItems.addAll(unfilteredItems.stream().filter(constraint -> constraint.getValue().getType().equals(type)).toList());	 
			} else {
				addItems = addItems.stream().filter(constraint -> constraint.getValue().getType().equals(type)).collect(Collectors.toSet());	 
			}
			
		}
			
		return addItems;
	}

	/*
	 * Setting up the single items for the view
	 */
	public void setUpTreeItems(List<Constraint> constraints) {
		TreeItem<Constraint> root = new TreeItem<>();
		
		unfilteredItems = new HashSet<>();
		
		for(Constraint constraint: constraints) {
			Button button = new Button("X");
			
			TreeItem<Constraint> constraintItem = new TreeItem<>(constraint);
			button.setOnAction(e -> deleteConstraintItem(constraint, constraintItem));
			
			constraintItem.setGraphic(button);
			unfilteredItems.add(constraintItem);
			root.getChildren().add(constraintItem);
		}
		
		groupTreeView.setRoot(root);
		groupTreeView.setShowRoot(false);
	}
	
	/*
	 * Logic to keep the constraints when removing (a feature from) a group
	 */
	private void resolveGroupConstraint(Constraint constraint, List<Constraint> newConstraints) {
		if(constraint instanceof AlternativeGroup alternative) {

            Set<Feature> processed = new HashSet<>();
			
			for(Feature f: alternative.getFeatures()) {
				newConstraints.add(new Implication(f, alternative.getParent()));
				
				for(Feature f2: processed) {
					newConstraints.add(new MutualExclusion(f, f2));
				}
				
				processed.add(f);
			}
		} else {
			OrRelation orGroup = (OrRelation) constraint;
			
			for(Feature f: orGroup.getFeatures()) {
				newConstraints.add(new Implication(f, orGroup.getParent()));
			}
		}
		
		newConstraints.stream().forEach((c) -> {
			if(!constraints.contains(c)) {
				constraints.add(c);
			}
		});
		newConstraints.retainAll(constraints);
	}
	
	/*
	 * Helper method to get the list of all features
	 */
	public List<Feature> getFeatures(){
		return features;
	}

	public ComboBox<Feature> getFeatureComboBox() {
		return featureComboBox;
	}

	public ListView<Feature> getGroupFeatureListView() {
		return groupFeatureListView;
	}

	public TreeView<Constraint> getGroupTreeView() {
		return groupTreeView;
	}

	public Set<TreeItem<Constraint>> getUnfilteredItems() {
		return unfilteredItems;
	}

	public List<Constraint> getConstraints() {
		return constraints;
	}

	public boolean getIsGroupView() {
		return isGroupView;
	}
	
	@FXML
	public void undo() {
		changeTracker.undo();
	}

	@FXML
	public void redo() {
		changeTracker.redo();
	}

	public ChangeTracker<ConstraintsViewController, ConstraintInfoController> getChangeTracker() {
		return changeTracker;
	}
	
	public void resetSelection() {
		groupTreeView.getSelectionModel().clearSelection();
	}

	public void setMainController(Controller controller) {
		this.mainController = controller;		
	}

	public void isGroupView(boolean groupView) {
		isGroupView = groupView;		
	}
}
