/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla
 * Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Johannes Kepler University Linz
 * LIT Cyber-Physical Systems Lab
 * Contributors:
 *  Kejda Domi- Added the feature model visualization
 ********************************************************************************/
package at.variabilityanalysisgui.visualization;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.fx_viewer.FxDefaultView;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.graphstream.ui.javafx.FxGraphRenderer;
import org.graphstream.ui.spriteManager.Sprite;
import org.graphstream.ui.spriteManager.SpriteManager;
import org.graphstream.ui.view.View;
import org.graphstream.ui.view.Viewer;
import org.graphstream.ui.view.ViewerListener;
import org.graphstream.ui.view.ViewerPipe;
import variabilityMining.Feature;
import java.util.*;

public class TreeGraph implements ViewerListener {

    private static final int MAX_LABEL_LEN = 9;

    // maximum distance a node can be dragged from its origin in graph units
    private static final double MAX_DRAG_RADIUS_GU = 40.0;

    private Feature root;
    private Graph graph;
    private SpriteManager sman;
    private FxDefaultView view;
    private Tooltip hoverTooltip;

    // Track active drag state
    private String draggedNodeId = null;
    private double initialNodeX;
    private double initialNodeY;

    public TreeGraph(Feature root) {
        this.root = root;
    }

    public interface FeatureSelectionListener {
        void onFeatureSelected(String featureId);
    }

    public interface GroupSelectionListener {
        void onGroupSelected(String parentFeatureId);
    }

    private final List<FeatureSelectionListener> featureSelectionListeners = new ArrayList<>();
    private final List<GroupSelectionListener> groupSelectionListeners = new ArrayList<>();

    private double pressX, pressY;
    private static final double CLICK_THRESHOLD_PX = 5.0;
    private static final double SPRITE_CLICK_RADIUS_PX = 20.0;

    private String draggedSpriteId = null;

    //map group id to all its member features
    private final Map<String, Set<String>> groupChildren = new HashMap<>();
    //map any features to its immediate parent
    private final Map<String, String> nodeParent = new HashMap<>();
    // spriteId mapped to parent feature id
    private final Map<String, String> groupSpriteParent = new HashMap<>();
    private Label hintLabel;
    private VBox hintBox;
    private Button hintToggle;
    private boolean hintExpanded = false;

    private String hintText() {
        return switch (mode) {
            case FEATURE_LIST ->
                    "Click a feature to scroll to it in the list.\nSelect a feature in the list to highlight it here.";
            case GROUP_CONSTRAINTS ->
                    "Click an OR/XOR triangle to select its group in the list.\nSelect a group in the list to highlight it here.";
            case SIMPLE_CONSTRAINTS ->
                    "Select a constraint in the list to highlight its two features here.";
        };
    }

    private void updateHint() {
        if (hintLabel != null) {
            hintLabel.setText(hintText());
        }
    }

    public void addFeatureSelectionListener(FeatureSelectionListener listener) {
        featureSelectionListeners.add(listener);
    }

    public void addGroupSelectionListener(GroupSelectionListener listener) {
        groupSelectionListeners.add(listener);
    }

    public Set<String> getGroupChildFeatureNames(String groupId) {
        return groupChildren.getOrDefault(groupId, Collections.emptySet());
    }


    public View getViewer() {
        setupGraph();
        buildModelRecursive(root);
        applyLayout();
        decorateGroupsRecursive(root);

        FxViewer viewer = new FxViewer(graph, Viewer.ThreadingModel.GRAPH_IN_GUI_THREAD);
        view = (FxDefaultView) viewer.addView("view", new FxGraphRenderer());

        viewer.disableAutoLayout();

        ViewerPipe pipe = viewer.newViewerPipe();
        pipe.addViewerListener(this);
        pipe.addSink(graph);

        Thread pumpThread = new Thread(() -> {
            while (true) {
                try {
                    pipe.pump();
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        pumpThread.setDaemon(true);
        pumpThread.start();

        Platform.runLater(() -> {
            hoverTooltip = new Tooltip();
            hoverTooltip.setAutoHide(false);
            hoverTooltip.setStyle(
                    "-fx-background-color: #ffffe0;" +
                            "-fx-text-fill: black;" +
                            "-fx-font-size: 12px;" +
                            "-fx-border-color: #999;" +
                            "-fx-border-width: 1px;" +
                            "-fx-padding: 4px 8px;"
            );

            //  MOUSE PRESS: Detect and pick target node
            view.setOnMousePressed(event -> {
                double clickRadiusPx = 25.0;
                boolean nodeHit = false;

                for (Node n : graph) {
                    if (!n.hasAttribute("xyz")) continue;
                    Object[] xyz = (Object[]) n.getAttribute("xyz");
                    double nx = toDouble(xyz[0]);
                    double ny = toDouble(xyz[1]);

                    org.graphstream.ui.geom.Point3 nodePx =
                            view.getCamera().transformGuToPx(nx, ny, 0);

                    double dx = nodePx.x - event.getX();
                    double dy = nodePx.y - event.getY();

                    if (Math.sqrt(dx * dx + dy * dy) < clickRadiusPx) {
                        draggedNodeId = n.getId();
                        pressX = event.getX();
                        pressY = event.getY();

                        if (n.hasAttribute("origX") && n.hasAttribute("origY")) {
                            initialNodeX = toDouble(n.getAttribute("origX"));
                            initialNodeY = toDouble(n.getAttribute("origY"));
                        } else {
                            initialNodeX = nx;
                            initialNodeY = ny;
                        }
                        nodeHit = true;
                        break;
                    }
                }

                if (!nodeHit) {
                    for (Map.Entry<String, String> entry : groupSpriteParent.entrySet()) {
                        String spriteId = entry.getKey();
                        String parentId = entry.getValue();
                        Node parentNode = graph.getNode(parentId);
                        if (parentNode == null || !parentNode.hasAttribute("xyz")) continue;

                        Object[] xyz = (Object[]) parentNode.getAttribute("xyz");
                        double nx = toDouble(xyz[0]);
                        double ny = toDouble(xyz[1]) - 20; // matches triangleSprite's setPosition(0, -20, 0) offset

                        org.graphstream.ui.geom.Point3 spritePx =
                                view.getCamera().transformGuToPx(nx, ny, 0);

                        double dx = spritePx.x - event.getX();
                        double dy = spritePx.y - event.getY();

                        if (Math.sqrt(dx * dx + dy * dy) < SPRITE_CLICK_RADIUS_PX) {
                            draggedSpriteId = spriteId;
                            pressX = event.getX();
                            pressY = event.getY();
                            break;
                        }
                    }
                }
            });

            // MOUSE DRAG: Constrain node movement within allowed radius
            view.setOnMouseDragged(event -> {
                if (draggedNodeId == null) return;

                Node n = graph.getNode(draggedNodeId);
                if (n == null) return;

                // Convert pixel cursor coordinates to graph units to compare it with the node
                org.graphstream.ui.geom.Point3 targetGu =
                        view.getCamera().transformPxToGu(event.getX(), event.getY());

                double dx = targetGu.x - initialNodeX;
                double dy = targetGu.y - initialNodeY;
                double distance = Math.sqrt(dx * dx + dy * dy);

                double newX = targetGu.x;
                double newY = targetGu.y;

                // clamp vector to maximum radius if threshold exceeded
                if (distance > MAX_DRAG_RADIUS_GU) {
                    double angle = Math.atan2(dy, dx);
                    newX = initialNodeX + MAX_DRAG_RADIUS_GU * Math.cos(angle);
                    newY = initialNodeY + MAX_DRAG_RADIUS_GU * Math.sin(angle);
                }

                // smooth update on GS rendering loop
                n.setAttribute("xyz", newX, newY, 0);
            });

            //  MOUSE RELEASE: Reset dragging target
            view.setOnMouseReleased(event -> {
                if (draggedNodeId != null) {
                    double dx = event.getX() - pressX;
                    double dy = event.getY() - pressY;
                    boolean isClick = Math.sqrt(dx * dx + dy * dy) < CLICK_THRESHOLD_PX;

                    if (isClick && mode == InteractionMode.FEATURE_LIST) {
                        String clickedId = draggedNodeId;
                        highlightNode(clickedId);
                        for (FeatureSelectionListener l : featureSelectionListeners) {
                            l.onFeatureSelected(clickedId);
                        }
                    }
                } else if (draggedSpriteId != null) {
                    double dx = event.getX() - pressX;
                    double dy = event.getY() - pressY;
                    boolean isClick = Math.sqrt(dx * dx + dy * dy) < CLICK_THRESHOLD_PX;

                    if (isClick && mode == InteractionMode.GROUP_CONSTRAINTS) {
                        String groupId = groupSpriteParent.get(draggedSpriteId);
                        if (groupId != null) {
                            highlightNodes(computeGroupHighlightSet(groupId));
                            for (GroupSelectionListener l : groupSelectionListeners) {
                                l.onGroupSelected(groupId);
                            }
                        }
                    }
                }
                draggedNodeId = null;
                draggedSpriteId = null;
            });

            // MOUSE MOVED: Tooltip hover detection
            view.setOnMouseMoved(event -> {
                String hoveredId = null;
                double threshold = 20.0;

                for (Node n : graph) {
                    if (!n.hasAttribute("xyz")) continue;
                    Object[] xyz = (Object[]) n.getAttribute("xyz");
                    double nx = toDouble(xyz[0]);
                    double ny = toDouble(xyz[1]);

                    org.graphstream.ui.geom.Point3 nodePx =
                            view.getCamera().transformGuToPx(nx, ny, 0);

                    double dx = nodePx.x - event.getX();
                    double dy = nodePx.y - event.getY();

                    if (Math.sqrt(dx * dx + dy * dy) < threshold) {
                        hoveredId = n.getId();
                        break;
                    }
                }

                if (hoveredId != null) {
                    hoverTooltip.setText(hoveredId);
                    if (!hoverTooltip.isShowing()) {
                        hoverTooltip.show(view,
                                event.getScreenX() + 12,
                                event.getScreenY() + 12);
                    } else {
                        hoverTooltip.setX(event.getScreenX() + 12);
                        hoverTooltip.setY(event.getScreenY() + 12);
                    }
                } else {
                    hoverTooltip.hide();
                }
            });

            view.setOnMouseExited(event -> hoverTooltip.hide());


            VBox legend = createLegend();
            legend.setLayoutX(15);
            legend.setLayoutY(15);

            view.widthProperty().addListener((obs, oldVal, newVal) -> {
                legend.setLayoutX(newVal.doubleValue() - legend.getBoundsInLocal().getWidth() - 20);
            });

            view.getChildren().add(legend);
        });

        return view;
    }
    private Set<String> currentlyHighlighted = new HashSet<>();

    public void highlightNodes(Collection<String> featureIds) {
        Platform.runLater(() -> {
            for (String id : currentlyHighlighted) {
                Node prev = graph.getNode(id);
                if (prev == null) continue;
                if (prev.hasAttribute("isAbstract")) {
                    prev.setAttribute("ui.class", "abstract");
                } else {
                    prev.removeAttribute("ui.class");
                }
            }
            currentlyHighlighted = new HashSet<>(featureIds);
            for (String id : currentlyHighlighted) {
                Node n = graph.getNode(id);
                if (n != null) n.setAttribute("ui.class", "highlighted");
            }
        });
    }

    public void highlightNode(String featureId) {
        highlightNodes(featureId == null ? Set.of() : Set.of(featureId));
    }

    public javafx.scene.layout.BorderPane getViewWithHint() {
        View graphView = getViewer();          // the existing method, called once
        hintBox = createHintBar();

        javafx.scene.layout.BorderPane pane = new javafx.scene.layout.BorderPane();
        pane.setCenter((javafx.scene.Node) graphView);
        pane.setBottom(hintBox);
        javafx.scene.layout.BorderPane.setMargin(hintBox, new Insets(6, 10, 10, 10));
        return pane;
    }

    private VBox createLegend() {
        VBox legend = new VBox(8);
        legend.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE);
        legend.setPadding(new Insets(10, 14, 10, 14));
        legend.setStyle(
                "-fx-background-color: rgba(255, 255, 255, 0.96);" +
                        "-fx-border-color: #b0b0b0;" +
                        "-fx-border-radius: 6px;" +
                        "-fx-background-radius: 6px;" +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 6, 0, 0, 2);"
        );

        Label title = new Label("Legend");
        title.setMinWidth(Region.USE_PREF_SIZE);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #111111; -fx-opacity: 1.0;");


        Circle mandatoryIcon = new Circle(5, Color.BLACK);
        mandatoryIcon.setStroke(Color.BLACK);
        HBox mandatoryRow = createLegendRow(mandatoryIcon, "Mandatory Feature");

        Circle optionalIcon = new Circle(5, Color.WHITE);
        optionalIcon.setStroke(Color.BLACK);
        optionalIcon.setStrokeWidth(1.5);
        HBox optionalRow = createLegendRow(optionalIcon, "Optional Feature");

        Polygon orIcon = new Polygon(0.0, 9.0, 5.0, 0.0, 10.0, 9.0);
        orIcon.setFill(Color.BLACK);
        orIcon.setStroke(Color.BLACK);
        HBox orRow = createLegendRow(orIcon, "OR Group");

        Polygon xorIcon = new Polygon(0.0, 9.0, 5.0, 0.0, 10.0, 9.0);
        xorIcon.setFill(Color.WHITE);
        xorIcon.setStroke(Color.BLACK);
        xorIcon.setStrokeWidth(1.5);
        HBox xorRow = createLegendRow(xorIcon, "XOR Group");

        HBox featureRow = createLegendRow(colorBox("#bc99fe", "#333333"), "Feature");
        HBox abstractRow = createLegendRow(colorBox("#dccbff", "#333333"), "Abstract Feature");
        HBox highlightRow = createLegendRow(colorBox("#7ec8ff", "#005ea6"), "Selected Feature");

        legend.getChildren().addAll(title, featureRow, abstractRow, highlightRow,
                mandatoryRow, optionalRow, orRow, xorRow);

        return legend;
    }

    private HBox createLegendRow(javafx.scene.Node icon, String text) {
        HBox row = new HBox(10);
        //row.setAlignment(Pos.CENTER_LEFT);

        StackPane iconBox = new StackPane(icon);
        iconBox.setPrefSize(16, 16);
        iconBox.setAlignment(Pos.CENTER);

        Label label = new Label(text);
        label.setMinWidth(Region.USE_PREF_SIZE);
        label.setStyle("-fx-font-size: 11px; -fx-font-weight: normal; -fx-text-fill: #222222; -fx-opacity: 1.0;");

        row.getChildren().addAll(iconBox, label);
        return row;
    }
    private VBox createHintBar() {
        hintToggle = new Button("+  How it works");
        hintToggle.setStyle("-fx-font-size: 11px; -fx-background-color: transparent; "
                + "-fx-text-fill: #222222; -fx-font-weight: bold; -fx-cursor: hand;");

        hintLabel = new Label(hintText());
        hintLabel.setWrapText(true);
        hintLabel.setMaxWidth(Double.MAX_VALUE);
        hintLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #222222;");
        hintLabel.setVisible(false);
        hintLabel.setManaged(false);   // takes no space while collapsed

        hintToggle.setOnAction(e -> {
            hintExpanded = !hintExpanded;
            hintLabel.setVisible(hintExpanded);
            hintLabel.setManaged(hintExpanded);
            hintToggle.setText(hintExpanded ? "−  How it works" : "+  How it works");
        });

        VBox box = new VBox(2, hintToggle, hintLabel);
        box.setPadding(new Insets(4, 12, 6, 12));
        box.setMaxWidth(Double.MAX_VALUE);
        box.setStyle(
                "-fx-background-color: rgba(255, 255, 255, 0.96);" +
                        "-fx-border-color: #b0b0b0;" +
                        "-fx-border-radius: 6px;" +
                        "-fx-background-radius: 6px;" +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 6, 0, 0, 2);");
        return box;
    }
    
    public void updateGraph(Feature root) {
    	this.root = root;
    	graph.clear();
        groupChildren.clear();
        nodeParent.clear();
        groupSpriteParent.clear();
    	sman = new SpriteManager(graph);
        graph.setAttribute("ui.stylesheet", styleSheet());
        graph.setAttribute("ui.quality");
        graph.setAttribute("ui.antialias");
    	buildModelRecursive(this.root);
        applyLayout();
        decorateGroupsRecursive(this.root);
    }

    private double toDouble(Object o) {
        return o instanceof Double ? (Double) o : Double.parseDouble(o.toString());
    }

    private void setupGraph() {
        graph = new MultiGraph("TreeGraph");
        sman = new SpriteManager(graph);
        graph.setAttribute("ui.stylesheet", styleSheet());
        graph.setAttribute("ui.quality");
        graph.setAttribute("ui.antialias");
    }

    private void buildModelRecursive(Feature current) {
        if (current == null) return;
        addNode(current);
        List<Feature> children = current.getChildren();
        if (children != null) {
            for (Feature child : children) {
                addNode(child);
                nodeParent.put(child.getName(), current.getName());
                String eId = edgeId(current.getName(), child.getName());
                graph.addEdge(eId, current.getName(), child.getName());
                buildModelRecursive(child);
            }
        }
    }

    private void decorateGroupsRecursive(Feature current) {
        if (current == null) return;
        List<Feature> children = current.getChildren();
        if (children != null && !children.isEmpty()) {
            if (current.isOrParent()) {
                addOr(current.getName(), children);
            } else if (current.isAlternativeParent()) {
                addXor(current.getName(), children);
            }
            for (Feature child : children) {
                if (child.isOptional()) {
                    addOptional(edgeId(current.getName(), child.getName()));
                } else if (child.isMandatory()) {
                    addMandatory(edgeId(current.getName(), child.getName()));
                }
                decorateGroupsRecursive(child);
            }
        }
    }

    private void addNode(Feature feature) {
        String id = feature.getName();
        if (graph.getNode(id) == null) {
            Node n = graph.addNode(id);
            String display = id.length() > MAX_LABEL_LEN ? id.substring(0, MAX_LABEL_LEN) + "." : id;
            n.setAttribute("ui.label", display);
            if (feature.isAbstract()) {
                n.setAttribute("isAbstract", true);
                n.setAttribute("ui.class", "abstract");
            }
        }
    }

    private void applyLayout() {
        TreeLayout layout = new TreeLayout(50.0, 200.0);
        layout.apply(graph, root);

        // store initial original pos for each node
        for (Node n : graph) {
            if (n.hasAttribute("xyz")) {
                Object[] xyz = (Object[]) n.getAttribute("xyz");
                n.setAttribute("origX", toDouble(xyz[0]));
                n.setAttribute("origY", toDouble(xyz[1]));
            }
        }
    }

    private void addOptional(String edgeId) {
        Sprite s = circleSprite("opt_" + edgeId, "white");
        s.attachToEdge(edgeId);
        s.setPosition(0.95);
    }

    private void addMandatory(String edgeId) {
        Sprite s = circleSprite("man_" + edgeId, "black");
        s.attachToEdge(edgeId);
        s.setPosition(0.95);
    }

    private void addOr(String parentId, List<Feature> children) {
        double width = calculateGroupWidth(children);
        String spriteId = "or_" + parentId;
        triangleSprite(spriteId, "black", width).attachToNode(parentId);
        groupSpriteParent.put(spriteId, parentId);
        storeGroupChildren(parentId, children);
    }

    private void addXor(String parentId, List<Feature> children) {
        double width = calculateGroupWidth(children);
        String spriteId = "xor_" + parentId;
        triangleSprite(spriteId, "white", width).attachToNode(parentId);
        groupSpriteParent.put(spriteId, parentId);
        storeGroupChildren(parentId, children);
    }

    private void storeGroupChildren(String groupId, List<Feature> children) {
        Set<String> names = new HashSet<>();
        for (Feature c : children) {
            names.add(c.getName());
        }
        groupChildren.put(groupId, names);
    }

    private double calculateGroupWidth(List<Feature> children) {
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        boolean found = false;
        for (Feature child : children) {
            Node n = graph.getNode(child.getName());
            if (n != null && n.hasAttribute("xyz")) {
                double x = toDouble(((Object[]) n.getAttribute("xyz"))[0]);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                found = true;
            }
        }
        if (!found) return 60.0;
        return Math.max((maxX - minX) * 0.20, 25.0);
    }

    private Sprite circleSprite(String id, String fill) {
        Sprite s = sman.addSprite(id);
        s.setAttribute("ui.style", "shape: circle; size: 14px; fill-color: " + fill +
                "; stroke-mode: plain; stroke-color: black; stroke-width: 1.5px; z-index: 4;");
        return s;
    }

    private Sprite triangleSprite(String id, String fill, double width) {
        Sprite s = sman.addSprite(id);
        s.setAttribute("ui.style", "shape: triangle; fill-color: " + fill +
                "; stroke-mode: plain; stroke-color: black; stroke-width: 2px; z-index: 2; " +
                "size: " + width + "px, 35px;");
        s.setPosition(0, -20, 0);
        return s;
    }

    private String edgeId(String p, String c) { return p + "_" + c; }

    private String styleSheet() {
        return """
        graph{
            padding: 50px, 50px;
        }
        node {
            shape: box;
            size-mode: fit;
            padding: 2px, 4px;
            fill-color: #bc99fe;
            stroke-mode: plain;
            stroke-color: #333;
            stroke-width: 1px;
            text-size: 10px;
            text-alignment: center;
            text-offset: 2px, 3px;
            z-index: 3;
        }
        edge {
            size: 1.5px;
            fill-color: #555;
            z-index: 1;
        }
        node.abstract {
        fill-color: #dccbff;
        }
        node.highlighted {
            fill-color: #7ec8ff;
            stroke-color: #005ea6;
            stroke-width: 1px;
        }
        """;
    }

    @Override public void viewClosed(String s) {}

    @Override
    public void buttonPushed(String id) {
        Node n = graph.getNode(id);
        if (n == null) return;
        String current = (String) n.getAttribute("ui.label");
        if (current.equals(id)) {
            String truncated = id.length() > MAX_LABEL_LEN
                    ? id.substring(0, MAX_LABEL_LEN) + "."
                    : id;
            n.setAttribute("ui.label", truncated);
        } else {
            n.setAttribute("ui.label", id);
        }
    }

    private boolean isAbstractFeature(String name) {
        Node n = graph.getNode(name);
        return n != null && n.hasAttribute("isAbstract");
    }

    public String findGroupIdByChildren(Set<String> featureNames) {
        for (Map.Entry<String, Set<String>> entry : groupChildren.entrySet()) {
            if (entry.getValue().equals(featureNames)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Set<String> computeGroupHighlightSet(String groupId) {
        Set<String> ids = new HashSet<>(groupChildren.getOrDefault(groupId, Collections.emptySet()));
        ids.add(groupId);
        if (isAbstractFeature(groupId)) {
            String ancestor = nodeParent.get(groupId);
            if (ancestor != null) {
                ids.add(ancestor);
            }
        }
        return ids;
    }

    @Override public void buttonReleased(String s) {}
    @Override public void mouseOver(String id) {}
    @Override public void mouseLeft(String id) {}

    public enum InteractionMode { FEATURE_LIST, GROUP_CONSTRAINTS, SIMPLE_CONSTRAINTS }

    private InteractionMode mode = InteractionMode.FEATURE_LIST;

    public void setInteractionMode(InteractionMode newMode) {
        this.mode = newMode;
        highlightNodes(Set.of());   // clear leftover highlights from the previous view
        updateHint();
    }

    private Rectangle colorBox(String fill, String stroke) {
        Rectangle r = new Rectangle(14, 10);
        r.setFill(Color.web(fill));
        r.setStroke(Color.web(stroke));
        return r;
    }
}