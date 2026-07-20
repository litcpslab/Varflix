package at.variabilityanalysisai.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import java.time.Duration;

public class LlmConfig {

    public enum Provider {
        OLLAMA, OPENAI, ANTHROPIC
    }

    private static final Provider ACTIVE_PROVIDER = Provider.OLLAMA;

    public static ChatModel createModel() {
        switch (ACTIVE_PROVIDER) {
            case OLLAMA:
                return OllamaChatModel.builder()
                        .baseUrl("http://localhost:11434")
                        //.modelName("gwen3.5")
                        .modelName("llama3")
                        .temperature(1.0)
                        .timeout(Duration.ofMinutes(5))
                        //.numCtx(32768) //gwen3.5
                        .numCtx(8192) //llama3
                        .responseFormat(ResponseFormat.JSON)
                        .build();

            case OPENAI:
                return OpenAiChatModel.builder()
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .modelName("gpt-4o")
                        .temperature(1.0)
                        .timeout(Duration.ofMinutes(5))
                        .responseFormat(ResponseFormat.JSON)
                        .build();

            case ANTHROPIC:
                return AnthropicChatModel.builder()
                        .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                        .modelName("claude-3-5-sonnet-20240620")
                        .temperature(1.0)
                        .timeout(Duration.ofMinutes(5))
                        .responseFormat(ResponseFormat.JSON)
                        .build();

            default:
                throw new IllegalArgumentException("Unknown LLM Provider");
        }
    }
}