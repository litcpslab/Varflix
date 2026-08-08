package at.variabilityanalysisgui.controller;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import at.variabilityanalysisgui.changeTracking.ChangeTracker;
import at.variabilityanalysisgui.parser.InputParser;
import at.variabilityanalysisgui.view.FeatureTreeNode;
import guiModel.Difference;
import guiModel.Element;
import guiModel.Group;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Window;

public class FeatureViewController {

	private Controller mainController;
	private TreeViewController treeViewController;
	private FilterController filterController;
	private DetailsController detailsController;
	 
	//FilterController
	@FXML private TextField searchTextField;
	@FXML private Button filterButton;

    // DetailsController
    @FXML private ListView<guiModel.Element> detailSubElementListView;
    @FXML private ScrollPane detailScrollPane;
    @FXML private HBox detailsNameHBox;
    @FXML private Label detailLocationLabel;
    @FXML private TextArea detailLocationTextArea;
    @FXML private TextField detailGroupNameTextField;
    @FXML private Button detailChangeNameButton;
    @FXML private Label detailOccurrenceLabel;
    @FXML private ListView<String> detailOccurrencesListView;
    @FXML private Label detailElementLabel;
    @FXML private TextArea detailElementData;
    @FXML private Label detailSubElementLabel;
    @FXML private Button detailCloseButton;
    @FXML private Label detailNamingHistoryLabel;
    @FXML private ListView<String> detailNamingHistoryListView;
    @FXML private Button detailChangeBackButton;

    //TreeViewController
    @FXML public HBox hierarchyButtonHBox;
    @FXML private TreeView<FeatureTreeNode> featureTreeView;
    @FXML private HBox detailNamingHistoryHBox;

    @FXML private Button undoButton;
    @FXML private Button redoButton;


    @FXML private Label detailDescriptionLabel;
    @FXML private TextArea detailDescription;
    @FXML private Button detailDescriptionChangeButton;
    @FXML private Button askAiButton;

    private final InputParser parser = new InputParser();

    private ChangeTracker<FeatureViewController, TreeViewController> changeTracker;

    public TreeViewController getTreeViewController() {
        return treeViewController;
    }
	
	public void init() {
		this.treeViewController = new TreeViewController(mainController, this, featureTreeView, hierarchyButtonHBox);
        this.filterController = new FilterController(mainController, this, searchTextField, filterButton);
        this.detailsController = new DetailsController(mainController,
                                                        this,
                                                        detailSubElementListView,
                                                        detailScrollPane,
                                                        detailsNameHBox,
                                                        detailLocationLabel,
                                                        detailLocationTextArea,
                                                        detailGroupNameTextField,
                                                        detailChangeNameButton,
                                                        detailDescriptionLabel,
                                                        detailDescription,
                                                        detailDescriptionChangeButton,
                                                        detailOccurrenceLabel,
                                                        detailOccurrencesListView,
                                                        detailElementLabel,
                                                        detailElementData,
                                                        detailSubElementLabel,
                                                        detailCloseButton,
                                                        detailNamingHistoryLabel,
                                                        detailNamingHistoryListView,
                                                        detailChangeBackButton,
                                                        detailNamingHistoryHBox,
                                                        askAiButton);

        treeViewController.initializeHierarchyButtons();
        treeViewController.populateTreeView(filterController.getFilteredGroups(), null); // Populate with parsed data
        detailsController.hideDetailsPane();
        filterController.setupFilterListener();
        
        changeTracker = new ChangeTracker<>(this, treeViewController);
        
        undoButton.disableProperty().bind(changeTracker.canUndoProperty().not());
        redoButton.disableProperty().bind(changeTracker.canRedoProperty().not());  
        
        KeyCombination undoCombination = new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN);
		KeyCombination redoCombination = new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN);

        featureTreeView.sceneProperty().addListener((obs, oldScene, newScene) -> {
			if(newScene != null) {
				newScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
					if(undoCombination.match(event)) {
						undo();
						event.consume();
					} else if(redoCombination.match(event)) {
						redo();
						event.consume();
					} else if(KeyCode.DELETE.equals(event.getCode())) {
						List<TreeItem<FeatureTreeNode>> selectedItems = new ArrayList<>(featureTreeView.getSelectionModel().getSelectedItems());
						
						for(TreeItem<FeatureTreeNode> item: selectedItems) {
							treeViewController.handleDeleteAction(item);
						}
					}
				});
			}
		});
	}
	
    public void loadFile(File selectedFile) {
		if (selectedFile != null) {
            try {
                mainController.setOriginalGroups(parser.parse(selectedFile.getAbsolutePath()));
                mainController.setArtifactType(parser.getType());
                treeViewController.populateTreeView(filterController.getFilteredGroups(), null); // Populate with parsed data
                detailsController.hideDetailsPane();
                filterController.setupFilterListener();
            } catch (IOException e) {
                showErrorDialog("Error Parsing File", "Could not read or parse the file:\n" + e.getMessage());
                e.printStackTrace();
            }
        }
	}
    
    // Error dialogs
    public void showErrorDialog(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.initOwner(getWindow());
        alert.showAndWait();
    }
    
    public void populateTreeView(List<Group> groups, List<Element> elements) {
        treeViewController.populateTreeView(groups, elements);
    }

    public List<Group> getFilteredGroups() {
        return filterController.getFilteredGroups();
    }

    public List<Element> getFilteredElements() {
        return filterController.getFilteredElements();
    }

    public void showDetailsPane(Difference data, TreeItem<FeatureTreeNode> newValue) {
        detailsController.showDetailsPane(data, newValue);
    }
    
    
    // Main window
    public Window getWindow() {
        Node node = treeViewController.getFeatureTreeView().getScene().getRoot();
        if (node != null) {
            return node.getScene().getWindow();
        }
        return null;
    }

    public Group findGroupById(int groupId) {
    	return mainController.findGroupById(groupId);
    }
    
    public void setMainController(Controller mainController) {
        this.mainController = mainController;
    }

    @FXML
    public void undo() {
        changeTracker.undo();
    }

    @FXML
    public void redo() {
        changeTracker.redo();
    }

    public ChangeTracker<FeatureViewController, TreeViewController> getChangeTracker() {
        return changeTracker;
    }

	public List<Group> getOriginalGroups() {
		return mainController.getOriginalGroups();
	}

	public void redrawVisualization() {
		mainController.redrawVisualization();		
	}

	public void resetSelection() {
		featureTreeView.getSelectionModel().clearSelection();
	}
}
