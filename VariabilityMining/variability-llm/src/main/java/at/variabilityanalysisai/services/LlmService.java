package at.variabilityanalysisai.services;

import at.variabilityanalysisai.config.LlmConfig;
import at.variabilityanalysisai.models.ConstraintAnalysisRequest;
import at.variabilityanalysisai.models.ConstraintAnalysisResult;
import at.variabilityanalysisai.models.FeatureAnalysisRequest;
import at.variabilityanalysisai.models.FeatureAnalysisResult;
import at.variabilityanalysisai.prompts.LlmRequest;
import dev.langchain4j.model.chat.ChatModel;
import guiModel.Group;
import constraints.Constraint;
import constraints.SimpleConstraint;

import java.util.List;

public class LlmService {
    private final ChatModel chatModel = LlmConfig.createModel();
    private final String artifactType;

    public LlmService(String artifactType) {
        this.artifactType = artifactType;
    }

    public FeatureAnalysisResult suggestNameAndDescription(Group group, List<Constraint> allConstraints, List<Group> allGroups) {
        FeatureAnalysisRequest featureAnalysisRequest = new FeatureAnalysisRequest(group, allConstraints, allGroups);
        LlmRequest<FeatureAnalysisRequest, FeatureAnalysisResult> featureAnalysis =
                new LlmRequest<>(artifactType, featureAnalysisRequest, FeatureAnalysisRequest.class, FeatureAnalysisResult.class);
        return featureAnalysis.sendRequest();
    }

    public ConstraintAnalysisResult suggestConstraintDescription(Constraint currentSelectedConstraint, List<guiModel.Group> originalGroups) {
        ConstraintAnalysisRequest constraintAnalysisRequest = new ConstraintAnalysisRequest(currentSelectedConstraint, originalGroups);
        LlmRequest<ConstraintAnalysisRequest, ConstraintAnalysisResult> constraintAnalysis =
                new LlmRequest<>(artifactType, constraintAnalysisRequest, ConstraintAnalysisRequest.class, ConstraintAnalysisResult.class);
        return constraintAnalysis.sendRequest();
    }
}