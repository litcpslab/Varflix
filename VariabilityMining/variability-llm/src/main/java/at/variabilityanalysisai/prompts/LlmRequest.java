package at.variabilityanalysisai.prompts;

import at.variabilityanalysisai.config.LlmConfig;
import at.variabilityanalysisai.models.FeatureAnalysisResult;
import com.google.gson.Gson;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.structured.Description;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

public class LlmRequest<T, R> {

    protected ChatModel model = LlmConfig.createModel();
    protected String artifactType;
    private T request;
    private Class<T> requestClass;
    private Class<R> responseClass;
    private Prompt systemPrompt;
    private Prompt userPrompt;

    public LlmRequest(String artifactType, T request, Class<T> requestClass, Class<R> responseClass) {
        this.artifactType = artifactType;
        this.request = request;
        this.requestClass = requestClass;
        this.responseClass = responseClass;
        String systemTemplate = loadResourceString("prompts/" + requestClass.getSimpleName() + "/" + artifactType + "/system.txt");
        this.systemPrompt = PromptTemplate.from(systemTemplate).apply(Map.of(
                "parameterInstructions", generateParameterInstruction(),
                "formatInstructions", generateFormatInstructions()));
        this.userPrompt = Prompt.from(generateUserPrompt());
    }

    private String loadResourceString(String resourcePath){
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new java.io.FileNotFoundException("Could not find resource file: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    public R sendRequest() {
        if (systemPrompt == null || userPrompt == null) {
            throw new IllegalStateException("You must call initializeParameters() before askAi()");
        }

        SystemMessage systemMessage = systemPrompt.toSystemMessage();
        UserMessage userMessage = userPrompt.toUserMessage();

        ChatResponse response = model.chat(systemMessage, userMessage);

        String rawJson = response.aiMessage().text();

        String cleanJson = rawJson.replaceAll("(?s)<think>.*?</think>", "")
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();

        Gson gson = new Gson();
        try {
            return gson.fromJson(cleanJson, responseClass);
        } catch (Exception e) {
            System.err.println("FAILED TO PARSE JSON! Raw output was:\n" + rawJson);
            System.err.println("Cleaned output was:\n" + cleanJson);
            throw new RuntimeException("LLM returned invalid JSON format.", e);
        }
    }

    private String generateParameterInstruction() {
        Field[] fields = requestClass.getDeclaredFields();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            Field f = fields[i];
            String name = f.getName();
            Description desc = f.getAnnotation(Description.class);
            String descText = desc != null ? String.join(" ", desc.value()) : "A string value.";
            sb.append(name).append(": ").append(descText).append("\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private String generateUserPrompt() {
        StringBuilder sb = new StringBuilder();
        Field[] fields = requestClass.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            Field f = fields[i];
            String name = f.getName();
            f.setAccessible(true);
            try {
                sb.append(name).append(": \n")
                  .append(f.get(request)).append("\n")
                  .append("\n");
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return sb.toString();
    }

    private String generateFormatInstructions() {
        StringBuilder sb = new StringBuilder();
        sb.append("You MUST return a valid JSON object. Do not include any markdown formatting or text outside the JSON.\n");
        sb.append("The JSON MUST perfectly match this schema:\n{\n");

        Field[] fields = responseClass.getDeclaredFields();

        for (int i = 0; i < fields.length; i++) {
            Field f = fields[i];
            String name = f.getName();

            Description desc = f.getAnnotation(Description.class);
            String descText = desc != null ? String.join(" ", desc.value()) : "A string value.";

            sb.append("  \"").append(name).append("\": \"").append(descText).append("\"");
            if (i < fields.length - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}