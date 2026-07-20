package at.variabilityanalysisai.models;

import constraints.Constraint;
import constraints.SimpleConstraint;
import dev.langchain4j.model.output.structured.Description;
import guiModel.Group;

import java.util.List;
import java.util.stream.Collectors;

public class FeatureAnalysisRequest {
    @Description("elements are code snippets only existing for this feature")
    String elements;
    @Description("occurrences are the variant names where this code appears")
    String occurrences;
    @Description("constraints shows how this feature relates to others")
    String constraints;
    @Description("oldNames are names already given to this feature before, SO DON'T RECOMMEND THEM AGAIN")
    String oldNames;
    @Description("existingNames are names already given to other features, so you can avoid duplicates, ensure consistency and enrich context.")
    String existingNames;

    public FeatureAnalysisRequest(Group group, List<Constraint> allConstraints, List<Group> allGroups) {
        this.occurrences = String.join("\n", group.getOccurrences());
        this.elements = group.getElements().stream()
                .map(element -> element.getName().getValue())
                .collect(Collectors.joining("\n"));

        if (elements == null || elements.isEmpty()) {
            throw new IllegalArgumentException("Cannot analyze a group with no elements.");
        }

        this.constraints = allConstraints.stream()
                .filter(constraint -> {
                    if (constraint instanceof constraints.Group) {
                        constraints.Group gc = (constraints.Group) constraint;
                        return gc.getFeatures().stream().anyMatch(f -> f.getName().contains(group.getName().getValue()))
                                || gc.getParent().getName().equals(group.getName().getValue());
                    } else if (constraint instanceof SimpleConstraint) {
                        SimpleConstraint sc = (SimpleConstraint) constraint;
                        return sc.getFeature1().getName().equals(group.getName().getValue())
                                || sc.getFeature2().getName().equals(group.getName().getValue());
                    }
                    return false;
                })
                .map(Constraint::toString)
                .collect(Collectors.joining("\n"));

        this.oldNames = group.getName().getValue() + "\n" + String.join("\n", group.getPreviousNames().stream().toList());

        this.existingNames = allGroups.stream()
                .map(g -> g.getName().getValue())
                .filter(name -> !name.startsWith("Group") && !name.equals("Core"))
                .collect(Collectors.joining("\n"));
    }

    public String getElements() {
        return elements;
    }

    public String getOccurrences() {
        return occurrences;
    }

    public String getConstraints() {
        return constraints;
    }

    public String getOldNames() {
        return oldNames;
    }

    public String getExistingNames() {
        return existingNames;
    }
}
