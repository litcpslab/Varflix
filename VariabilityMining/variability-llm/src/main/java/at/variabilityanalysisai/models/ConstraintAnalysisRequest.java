package at.variabilityanalysisai.models;

import constraints.Constraint;
import constraints.Group;
import constraints.SimpleConstraint;
import dev.langchain4j.model.output.structured.Description;
import variabilityMining.Feature;

import java.util.List;
import java.util.stream.Collectors;

public class ConstraintAnalysisRequest {
    @Description("The formal constraint string, e.g., 'Group1 => Group2' or 'ALT[Group1, Group2]'")
    private String constraintLogic;

    @Description("Information about the features involved in this constraint and their elements.")
    private String involvedFeaturesContext;

    public ConstraintAnalysisRequest(Constraint currentSelectedConstraint, List<guiModel.Group> originalGroups) {

        this.constraintLogic = currentSelectedConstraint.toString();

        StringBuilder contextBuilder = new StringBuilder();

        if (currentSelectedConstraint instanceof Group) {
            Group gc = (Group) currentSelectedConstraint;

            contextBuilder.append("Parent Feature: ").append(gc.getParent().getName()).append("\n");
            contextBuilder.append("Parent Description: ").append(getGroup(gc.getParent(), originalGroups).getDescription()).append("\n");
            contextBuilder.append("Parent Elements: ").append(getFeatureElementsAsString(originalGroups, gc.getParent())).append("\n\n");

            contextBuilder.append("Child Features:\n");
            for (Feature f : gc.getFeatures()) {
                contextBuilder.append(f.getName())
                        .append("\n contains elements: ").append(getFeatureElementsAsString(originalGroups, f))
                        .append("\n has description: ").append(getGroup(f, originalGroups).getDescription())
                        .append("\n\n");
            }
        } else if (currentSelectedConstraint instanceof SimpleConstraint) {
            SimpleConstraint sc = (SimpleConstraint) currentSelectedConstraint;

            contextBuilder.append("Feature 1: ").append(sc.getFeature1().getName()).append("\n");
            contextBuilder.append("Description: ").append(getGroup(sc.getFeature1(), originalGroups).getDescription()).append("\n");
            contextBuilder.append("Elements: ").append(getFeatureElementsAsString(originalGroups, sc.getFeature1())).append("\n\n");

            contextBuilder.append("Feature 2: ").append(sc.getFeature2().getName()).append("\n");
            contextBuilder.append("Description: ").append(getGroup(sc.getFeature2(), originalGroups).getDescription()).append("\n");
            contextBuilder.append("Elements: ").append(getFeatureElementsAsString(originalGroups, sc.getFeature2())).append("\n");
        }

        this.involvedFeaturesContext = contextBuilder.toString();
    }

    public String getConstraint_logic() {
        return constraintLogic;
    }

    public String getInvolved_features_context() {
        return involvedFeaturesContext;
    }

    private guiModel.Group getGroup(Feature feature, List<guiModel.Group>  originalGroups) {
        if (feature != null) {
            return originalGroups.stream()
                    .filter(group -> group.getName() != null && group.getName().get() != null &&
                            group.getName().get().equals(feature.getName()))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private String getFeatureElementsAsString(List<guiModel.Group> originalGroups, Feature feature) {
        try {
            guiModel.Group group = getGroup(feature, originalGroups);
            if (feature == null || group == null || group.getElements() == null) {
                return "No elements";
            }
            return group.getElements().stream()
                    .map(e -> {
                        if (e.getName() != null && e.getName().getValue() != null) {
                            return e.getName().getValue();
                        }
                        return "Unnamed Element";
                    })
                    .collect(Collectors.joining(", "));
        } catch (Exception e) {
            return "Unable to extract elements";
        }
    }
}
