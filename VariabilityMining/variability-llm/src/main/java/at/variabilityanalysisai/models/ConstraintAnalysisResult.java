package at.variabilityanalysisai.models;

import dev.langchain4j.model.output.structured.Description;

public class ConstraintAnalysisResult {

    @Description("Result of the constraint analysis")
    private String ConstraintAnalysis;

    public String getConstraintAnalysis() {
        return ConstraintAnalysis;
    }
}
