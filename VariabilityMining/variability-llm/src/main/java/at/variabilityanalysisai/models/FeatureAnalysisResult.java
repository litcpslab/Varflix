package at.variabilityanalysisai.models;

import dev.langchain4j.model.output.structured.Description;

public class FeatureAnalysisResult {

    @Description("Step-by-step analysis of elements, occurrences, and explicit rejection of OLD NAMES and EXISTING NAMES. Write this FIRST.")
    private String reasoning;

    @Description("A concise feature name for this group. MUST be in CamelCase.")
    private String suggestedName;

    @Description("A concise description (1 to 2 sentences) explaining what this group does in the system.")
    private String shortDescription;

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public String getSuggestedName() { return suggestedName; }
    public void setSuggestedName(String suggestedName) { this.suggestedName = suggestedName; }

    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }

    @Override
    public String toString() {
        return "FeatureAnalysisResult{" +
                "suggestedName='" + suggestedName + '\'' +
                ", shortDescription='" + shortDescription + '\'' +
                '}';
    }
}