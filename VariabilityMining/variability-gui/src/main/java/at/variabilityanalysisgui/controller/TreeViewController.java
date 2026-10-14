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
 	Alexander Stummer - Adapted drag-and-drop and delete actions
    Kejda Domi- Added the visualization section
    Sophie Öttl - Change Tracking
**/

package at.variabilityanalysisgui.controller;

import at.variabilityanalysisgui.changeTracking.DeleteElement;
import at.variabilityanalysisgui.changeTracking.DeleteGroup;
import at.variabilityanalysisgui.changeTracking.MoveElement;
import at.variabilityanalysisgui.view.DifferenceDirectory;
import at.variabilityanalysisgui.view.FeatureTreeCell;
import at.variabilityanalysisgui.view.FeatureTreeNode;
import guiModel.Difference;
import guiModel.Element;
import guiModel.ExtractionType;
import guiModel.Group;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.util.*;

import static at.variabilityanalysisgui.controller.Controller.findGroupContainingElement;

public class TreeViewController {

    enum ViewMode {
        TREE, FLAT, JAVAFILE
    }

    private final TreeView<FeatureTreeNode> featureTreeView;

    private final TreeItem<FeatureTreeNode> rootNode;

    public HBox hierarchyButtonHBox;

    ViewMode viewMode = ViewMode.FLAT;

    FeatureViewController featureViewController;
    
    Controller mainController;

    private List<TreeItem<FeatureTreeNode>> draggedItems = new ArrayList<>();

    // feedback-loop guard: true while its automatically selected due to a graph click
    private boolean isSelectingFromGraph = false;

    private java.util.function.Consumer<String> onListSelectionChanged;

    public void setOnListSelectionChanged(java.util.function.Consumer<String> listener) {
        this.onListSelectionChanged = listener;
    }


    public TreeViewController(Controller mainController, FeatureViewController featureViewController, TreeView<FeatureTreeNode> featureTreeView, HBox hierarchyButtonHBox) {
        this.featureTreeView = featureTreeView;
        this.mainController = mainController;
        this.featureViewController = featureViewController;
        this.hierarchyButtonHBox = hierarchyButtonHBox;

        // Setup TreeView with custom cell factory
        featureTreeView.setCellFactory(tv -> new FeatureTreeCell(this));

        // Create an invisible root item
        rootNode = new TreeItem<>(new FeatureTreeNode(new Difference(), false)); // Dummy root node
        featureTreeView.setRoot(rootNode);
        featureTreeView.setShowRoot(false);

        // selection model
        featureTreeView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.setValue(oldValue.getValue()); //refresh FeatureTreeCell
                oldValue.getValue().getData().getName().unbind();
            }
            if (newValue != null && newValue.getValue() != null) {
                FeatureTreeNode node = newValue.getValue();
                featureViewController.showDetailsPane(node.getData(), newValue);
                if (!isSelectingFromGraph && onListSelectionChanged != null) {
                    onListSelectionChanged.accept(node.getTechnicalName());
                }
            }
        });
        
        featureTreeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    public void selectAndScrollTo(String featureId) {
        TreeItem<FeatureTreeNode> item = findTreeItemByFeatureName(rootNode, featureId);
        if (item == null) return;

        collapseAllGroups();

        // expand every parent so the item has a valid row index
        TreeItem<FeatureTreeNode> parent = item.getParent();
        while (parent != null && parent != rootNode) {
            parent.setExpanded(true);
            parent = parent.getParent();
        }

        isSelectingFromGraph = true;
        featureTreeView.getSelectionModel().clearSelection();
        featureTreeView.getSelectionModel().select(item);
        int row = featureTreeView.getRow(item);
        if (row >= 0) {
            featureTreeView.scrollTo(row);
        }
        item.setExpanded(true);
        isSelectingFromGraph = false;
    }

    private void collapseAllGroups(){
        TreeItem<FeatureTreeNode> root = featureTreeView.getRoot();

        for(TreeItem<FeatureTreeNode> current: root.getChildren()){
            if(current.isExpanded()){
                current.setExpanded(false);
            }
        }

    }

    private TreeItem<FeatureTreeNode> findTreeItemByFeatureName(TreeItem<FeatureTreeNode> node, String featureId) {
        for (TreeItem<FeatureTreeNode> child : node.getChildren()) {
            if (featureId.equals(child.getValue().getTechnicalName())) {
                return child;
            }
            TreeItem<FeatureTreeNode> found = findTreeItemByFeatureName(child, featureId);
            if (found != null) return found;
        }
        return null;
    }

    public void populateTreeView(List<Group> groups, List<Element> visibleElements) {
        Set<FeatureTreeNode> expandedNodes = new HashSet<>();
        for(TreeItem<FeatureTreeNode> groupItem: rootNode.getChildren()) {
            expandedNodes.addAll(getExpandedElement(groupItem));
        }
        rootNode.getChildren().clear();

        if (groups == null) return;

        for (Group group : groups) {
            populateGroup(group, visibleElements, null);
            expandNodes(expandedNodes, rootNode);
        }
        featureTreeView.getSelectionModel().clearSelection();

    }

    private void expandNodes(Set<FeatureTreeNode> expandedNodes, TreeItem<FeatureTreeNode> rootNode) {
        for (FeatureTreeNode node : expandedNodes) {
            TreeItem<FeatureTreeNode> foundItem = findTreeItemByPath(rootNode, node.getPath());
            if (foundItem != null) {
                foundItem.setExpanded(true);
                while(foundItem.getParent() != null && !foundItem.getParent().isExpanded()) {
                    foundItem.getParent().setExpanded(true);
                    foundItem = foundItem.getParent();
                }
            }
        }
    }

    public void refreshGroup(TreeItem<FeatureTreeNode> groupItem) {
        Group group = (Group) groupItem.getValue().getData();
        int index = rootNode.getChildren().indexOf(groupItem);
        rootNode.getChildren().remove(index);
        Set<FeatureTreeNode> expandedNodes = getExpandedElement(groupItem);
        groupItem = populateGroup(group, featureViewController.getFilteredElements(), index);
        expandNodes(expandedNodes, groupItem);
        groupItem.setExpanded(true);
    }

    public void refreshGroup(Group group) {
        TreeItem<FeatureTreeNode> groupItem = findTreeItemByPath(rootNode, group.getName().get());
        if (groupItem != null) {
            refreshGroup(groupItem);
        }
    }

    private Set<FeatureTreeNode> getExpandedElement(TreeItem<FeatureTreeNode> groupItem) {
        Set<FeatureTreeNode> expandedElements = new HashSet<>();
        if (!groupItem.isExpanded()){
            return expandedElements;
        }
        for (TreeItem<FeatureTreeNode> child : groupItem.getChildren()) {
            if (child.getValue().getType() == FeatureTreeNode.DataType.ELEMENT) {
                expandedElements.add(child.getValue());
            }
            expandedElements.addAll(getExpandedElement(child));
        }
        return expandedElements;
    }

    public TreeView<FeatureTreeNode> getFeatureTreeView() {
        return featureTreeView;
    }

    private TreeItem<FeatureTreeNode> populateGroup(Group group, List<Element> visibleElements, Integer index) {
        FeatureTreeNode groupNodeData = new FeatureTreeNode(group, false);
        TreeItem<FeatureTreeNode> groupItem = new TreeItem<>(groupNodeData);
        groupItem.setExpanded(false);

        organizeHierarchy(groupItem, groupNodeData);

        // Add elements as children
        if (group.getElements() != null) {
            for (Element element : group.getElements()) {
                if(visibleElements != null && !visibleElements.isEmpty() && !visibleElements.contains(element)) {
                    continue;
                }
                FeatureTreeNode elementNodeData = new FeatureTreeNode(element, false);

                // Check if the element is already in the tree
                TreeItem<FeatureTreeNode> existingItem = findTreeItemByPath(groupItem, element.getLocation() + element.getSeparateSymbol() + element.getName().get());
                if (existingItem != null && existingItem.getValue().isDirectory()) {
                    elementNodeData.setDirectory(true);
                    existingItem.setValue(elementNodeData);
                } else {
                    TreeItem<FeatureTreeNode> elementItem = new TreeItem<>(elementNodeData);
                    ObservableList<TreeItem<FeatureTreeNode>> children = groupItem.getChildren();

                    while (true) {
                        Optional<TreeItem<FeatureTreeNode>> c = children.stream()
                                .filter(child -> child.getValue().isDirectory())
                                .filter(child -> {
                                    String path = child.getValue().getPath();
                                    if( viewMode == ViewMode.JAVAFILE) {
                                        path = path.split(":")[0];
                                        return ((Element) elementItem.getValue().getData()).getLocation().split(":")[0].startsWith(path);
                                    }
                                    return ((Element) elementItem.getValue().getData()).getLocation().startsWith(path);
                                })
                                .findFirst();
                        if (c.isPresent()) {
                            children = c.get().getChildren();
                        } else {
                            break;
                        }
                    }
                    children.add(elementItem);
                }
            }
        }
        collapseTillMultipleElements(groupItem);
        if (index != null) {
            rootNode.getChildren().add(index, groupItem);
        } else {
            rootNode.getChildren().add(groupItem);
        }
        return groupItem;
    }

    private void collapseTillMultipleElements(TreeItem<FeatureTreeNode> groupItem) {
        if (viewMode == ViewMode.FLAT) return;
        boolean viewCriteria = true;
        if (viewMode == ViewMode.JAVAFILE) {
            for(TreeItem<FeatureTreeNode> child : groupItem.getChildren()) {
                if (child.getValue().getDisplayName().contains(".java")) viewCriteria = false;
            }
        }

        if (groupItem.getChildren().size() == 1
                && groupItem.getChildren().get(0).getValue().isDirectory()
                && viewCriteria) {
            TreeItem<FeatureTreeNode> oldChild = groupItem.getChildren().get(0);
            collapseTillMultipleElements(oldChild);

            ObservableList<TreeItem<FeatureTreeNode>> newChildren = oldChild.getChildren();
            groupItem.getChildren().clear();
            for (TreeItem<FeatureTreeNode> childItem : newChildren) {
                groupItem.getChildren().add(childItem);
            }
        }
    }

    private void organizeHierarchy(TreeItem<FeatureTreeNode> groupItem, FeatureTreeNode groupNodeData) {
        if (viewMode == ViewMode.FLAT) return;

        List<Element> elements = ((Group) groupNodeData.getData()).getElements();
        for (Element element : elements) {
            String[] pathParts = element.getLocation().split(mainController.seperatorMap.get(mainController.getArtifactType()));
            ObservableList<TreeItem<FeatureTreeNode>> children = groupItem.getChildren();
            for (int i = 0; i < pathParts.length; i++) {
                // Check if the path part already exists in the children
                String pathPart;
                if(viewMode == ViewMode.JAVAFILE) {
                    pathPart = pathParts[i].split(":")[0]; // For Java file view, we only want the part before the colon
                } else {
                    pathPart = pathParts[i];
                }
                Optional<TreeItem<FeatureTreeNode>> existingChild = children.stream()
                        .filter(child -> child.getValue().getTechnicalName().equals(pathPart))
                        .findFirst();
                if (existingChild.isPresent()) {
                    children = existingChild.get().getChildren();
                } else {
                    String name = String.join(mainController.seperatorMap.get(mainController.getArtifactType()), Arrays.asList(pathParts).subList(0, i + 1));
                    if(viewMode == ViewMode.JAVAFILE) name = name.split(":")[0];
                    DifferenceDirectory dir = new DifferenceDirectory(name, mainController.seperatorMap.get(mainController.getArtifactType()));
                    TreeItem<FeatureTreeNode> conTreeItem = new TreeItem<>(new FeatureTreeNode(dir, true));
                    children.add(conTreeItem);
                    children = conTreeItem.getChildren();
                }
            }
        }
    }

    public static TreeItem<FeatureTreeNode> findTreeItemByPath(TreeItem<FeatureTreeNode> rootNode, String location) {
        for (TreeItem<FeatureTreeNode> child : rootNode.getChildren()) {
            if (child.getValue().getPath() != null && child.getValue().getPath().equals(location)) {
                return child;
            }
            TreeItem<FeatureTreeNode> found = findTreeItemByPath(child, location);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public void initializeHierarchyButtons() {
        ToggleButton hierarchyTreeButton = new ToggleButton("Tree");
        hierarchyButtonHBox.getChildren().add(hierarchyTreeButton);
        hierarchyTreeButton.setOnAction(event -> {
            if (hierarchyTreeButton.isSelected()) {
                hierarchyButtonHBox.getChildren().forEach(child -> {
                    if(child instanceof ToggleButton && child != hierarchyTreeButton) {
                        ((ToggleButton) child).setSelected(false);
                    }
                });
                this.viewMode = ViewMode.TREE;
                populateTreeView(featureViewController.getFilteredGroups(), null);
            } else {
                hierarchyTreeButton.setSelected(true);
            }
        });

        if (mainController.getArtifactType() == ExtractionType.JAVA) {
            ToggleButton hierarchyJavaFileButton = new ToggleButton("File");
            hierarchyButtonHBox.getChildren().add(hierarchyJavaFileButton);
            hierarchyJavaFileButton.setOnAction(event -> {
                if (hierarchyJavaFileButton.isSelected()) {
                    hierarchyButtonHBox.getChildren().forEach(child -> {
                        if(child instanceof ToggleButton && child != hierarchyJavaFileButton) {
                            ((ToggleButton) child).setSelected(false);
                        }
                    });
                    this.viewMode = ViewMode.JAVAFILE;
                    populateTreeView(featureViewController.getFilteredGroups(), null);
                } else {
                    hierarchyJavaFileButton.setSelected(true);
                }
            });
        } else if (mainController.getArtifactType() == ExtractionType.IEC61499) {
            // IEC61499 specific hierarchy button here
        }

        ToggleButton hierarchyFlatButton = new ToggleButton("Flat");
        hierarchyButtonHBox.getChildren().add(hierarchyFlatButton);
        hierarchyFlatButton.setOnAction(event -> {
            if (hierarchyFlatButton.isSelected()) {
                hierarchyButtonHBox.getChildren().forEach(child -> {
                    if(child instanceof ToggleButton && child != hierarchyFlatButton) {
                        ((ToggleButton) child).setSelected(false);
                    }
                });
                this.viewMode = ViewMode.FLAT;
                populateTreeView(featureViewController.getFilteredGroups(), null);
            } else {
                hierarchyFlatButton.setSelected(true);
            }
        });
        hierarchyFlatButton.setSelected(true);
    }

    // Actions
    public void handleDeleteAction(TreeItem<FeatureTreeNode> item) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Deletion");
        alert.setHeaderText("Remove '" + item.getValue().getDisplayName() + "'?");
        ButtonType yesButton = new ButtonType("Yes");
        ButtonType onlyDirectoryButton = new ButtonType("Keep Subelements");
        ButtonType noButton = new ButtonType("No");
        if (item.getValue().isDirectory() && item.getValue().getType() == FeatureTreeNode.DataType.ELEMENT) {
            alert.setContentText("Do you want to remove this item and all its subelements?");
            alert.getButtonTypes().setAll(yesButton, onlyDirectoryButton, noButton);
        } else {
            alert.setContentText("Do you want to remove this item from the view?");
            alert.getButtonTypes().setAll(yesButton, noButton);
        }
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == yesButton) {
            Difference data = item.getValue().getData();
            if (data instanceof Element element) {
                Group sourceGroup = findGroupContainingElement(mainController.getOriginalGroups(), element);
                featureViewController.getChangeTracker().addUndo(new DeleteElement(element, sourceGroup.getElements().indexOf(element), sourceGroup.getId()));
            } else if (data instanceof Group group) {
                featureViewController.getChangeTracker().addUndo(new DeleteGroup(group, mainController.getOriginalGroups().indexOf(group)));
            }
            for(TreeItem<FeatureTreeNode> child: item.getChildren()) {
            	if(child.getValue().getData() instanceof Element) {
	                Element element = (Element) child.getValue().getData();
	                Group group = findGroupContainingElement(mainController.getOriginalGroups(), element);
	                if (group != null) {
	                    group.getElements().remove(element);
	                }
            	}   
            }
            deleteItem(item);
            removeElementAndRefreshGroup(item);

        }
        if (result.isPresent() && result.get() == onlyDirectoryButton) {
            removeElementAndRefreshGroup(item);
        }
    }

    private void removeElementAndRefreshGroup(TreeItem<FeatureTreeNode> item) {
        if (item.getValue().getType() == FeatureTreeNode.DataType.ELEMENT) {
            Element element = (Element) item.getValue().getData();
            Group group = findGroupContainingElement(mainController.getOriginalGroups(), element);
            if (group != null) {
                group.getElements().remove(element);
                TreeItem<FeatureTreeNode> groupItem = findTreeItemByPath(rootNode, group.getName().get());
                if (groupItem != null) {
                    refreshGroup(groupItem);
                }
            }
        }
    }

    private void deleteItem(TreeItem<FeatureTreeNode> item) {
        if (item != null) {
            TreeItem<FeatureTreeNode> parent = item.getParent();
            if (parent != null) {
                parent.getChildren().remove(item);
                if(item.getValue().getData() instanceof Group) {
                	mainController.getOriginalGroups().remove(item.getValue().getData());
                    mainController.redrawVisualization();
                }
                System.out.println("Removed item: " + item.getValue().getDisplayName());
                removeEmptyContainers(parent);
            }
        }
    }

    private void removeEmptyContainers(TreeItem<FeatureTreeNode> item) {
        if (item != null && item.getChildren().isEmpty() && item.getValue().getType() == FeatureTreeNode.DataType.CONTAINER) {
            item.getParent().getChildren().remove(item);
            removeEmptyContainers(item.getParent());
        }
    }

    public List<TreeItem<FeatureTreeNode>> getDraggedItems() {
        return draggedItems;
    }

    public void setDraggedItems(List<TreeItem<FeatureTreeNode>> selectedItems) {
    	this.draggedItems = selectedItems;
    }

    public void moveElementsToGroup(List<TreeItem<FeatureTreeNode>> elementItems, TreeItem<FeatureTreeNode> targetGroupItem) {
        if(elementItems == null || targetGroupItem == null ||
                targetGroupItem.getValue().isDirectory() && targetGroupItem.getValue().getType() != FeatureTreeNode.DataType.ELEMENT) {
            System.err.println("Invalid types for moveElementToGroup");
            return;
        }
        Map<Integer, List<Element>> movedElements = new HashMap<>();
        Set<TreeItem<FeatureTreeNode>> deletedItems = new HashSet<>();

        int index = 0;
        for(TreeItem<FeatureTreeNode> elementItem: elementItems) {
        	 // Open dialogue for recursive move of directory
            if(elementItem.getValue().isDirectory()) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Move Directory");
                alert.setHeaderText("Move Directory and all its subelements?");
                alert.setContentText("Do you want to move the directory and all its subelements to the new group?");
                ButtonType yesButton = new ButtonType("Yes");
                ButtonType onlyDirectoryButton = new ButtonType("Keep Subelements");
                ButtonType noButton = new ButtonType("No");
                alert.getButtonTypes().setAll(yesButton, onlyDirectoryButton, noButton);
                Optional<ButtonType> result = alert.showAndWait();
                if(result.isPresent() && result.get() == noButton) {
                    return;
                }
                if(result.isPresent() && result.get() == yesButton) {
                	moveElementsToGroup(elementItem.getChildren(), targetGroupItem);
                }
            }


            while(targetGroupItem.getValue().getType() != FeatureTreeNode.DataType.GROUP) {
                targetGroupItem = targetGroupItem.getParent();
            }

            Group sourceGroup = null;
            if (elementItem.getValue().getData() instanceof Element) {

                Element element = (Element) elementItem.getValue().getData();
                Group targetGroup = (Group) targetGroupItem.getValue().getData();

                sourceGroup = Controller.findGroupContainingElement(mainController.getOriginalGroups(), element);

                movedElements.computeIfAbsent(sourceGroup.getId(), k -> new ArrayList<>()).add(element);

                // Remove from old group's element list
                boolean removed = sourceGroup.getElements().remove(element);
                if (!removed) {
                    System.err.println("Failed to remove element from source group's list in model.");
                } else {
                    // Add to new group's element list
                    targetGroup.getElements().add(element);

                    //refresh old group TreeItem
                    TreeItem<FeatureTreeNode> sourceGroupItem = findTreeItemByPath(rootNode, sourceGroup.getName().get());
                    int sourceIndex = rootNode.getChildren().indexOf(sourceGroupItem);
                    if (sourceGroupItem != null) {
                        refreshGroup(sourceGroupItem);

                        if (sourceGroup.getElements().isEmpty()) {
                            sourceGroupItem = rootNode.getChildren().get(sourceIndex);
                            deletedItems.add(sourceGroupItem);
                        }
                    }
                    // Add new group TreeItem
                    index = rootNode.getChildren().indexOf(targetGroupItem);
                    refreshGroup(targetGroupItem);
                    targetGroupItem = rootNode.getChildren().get(index);
                    // remove old group TreeItem
                    deleteItem(elementItem);

                    System.out.println("Moved element " + element.getName().get());
                }
            } else if (elementItem.getValue().getData() instanceof Group) {
                Group element = (Group) elementItem.getValue().getData();
                Group targetGroup = (Group) targetGroupItem.getValue().getData();

                targetGroup.getElements().addAll(element.getElements());
                movedElements.put(element.getId(), new ArrayList<>(element.getElements()));
                element.getElements().clear();

                index = rootNode.getChildren().indexOf(targetGroupItem);
                refreshGroup(targetGroupItem);
                targetGroupItem = rootNode.getChildren().get(index);

                TreeItem<FeatureTreeNode> sourceGroupItem = findTreeItemByPath(rootNode, element.getName().get());
                int sourceIndex = rootNode.getChildren().indexOf(sourceGroupItem);
                if (sourceGroupItem != null) {
                    refreshGroup(sourceGroupItem);
                    sourceGroupItem = rootNode.getChildren().get(sourceIndex);
                    deletedItems.add(sourceGroupItem);
                }

                System.out.println("Moved element " + element.getName().get());
            }

        }
        featureViewController.getChangeTracker().addUndo(new MoveElement(((Group) targetGroupItem.getValue().getData()).getId(), movedElements));
        for (TreeItem<FeatureTreeNode> item: deletedItems) {
            handleDeleteAction(item);
        }
    }

}
