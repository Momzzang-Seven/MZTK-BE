package momzzangseven.mztkbe.modules.verification.infrastructure.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import momzzangseven.mztkbe.modules.verification.application.config.VerificationRuntimeProperties;
import momzzangseven.mztkbe.modules.verification.application.exception.AiTimeoutException;
import momzzangseven.mztkbe.modules.verification.application.exception.AiUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

class SpringAiGoogleGenAiJsonClientTest {

  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  @AfterEach
  void tearDown() {
    executor.close();
  }

  @Test
  void buildsJsonPromptAndReturnsAssistantText() throws IOException {
    ChatModel chatModel = mock(ChatModel.class);
    ChatResponse response = mock(ChatResponse.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    when(response.getResult().getOutput().getText()).thenReturn("{\"workoutPhoto\":true}");
    when(chatModel.call(any(Prompt.class))).thenReturn(response);

    SpringAiGoogleGenAiJsonClient client =
        new SpringAiGoogleGenAiJsonClient(chatModel, runtimeProperties(3), executor);

    Path image = Files.createTempFile("verification-ai", ".png");
    try {
      String json =
          client.analyzeWorkoutPhoto(
              image,
              "system instruction",
              "Analyze this image and return the result JSON.",
              "{\"type\":\"object\"}");

      assertThat(json).isEqualTo("{\"workoutPhoto\":true}");

      ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
      verify(chatModel).call(captor.capture());

      Prompt prompt = captor.getValue();
      assertThat(prompt.getInstructions()).hasSize(2);
      assertThat(prompt.getInstructions().getFirst()).isInstanceOf(SystemMessage.class);
      assertThat(((SystemMessage) prompt.getInstructions().getFirst()).getText())
          .isEqualTo("system instruction");
      assertThat(prompt.getInstructions().get(1)).isInstanceOf(UserMessage.class);
      UserMessage userMessage = (UserMessage) prompt.getInstructions().get(1);
      assertThat(userMessage.getText()).isEqualTo("Analyze this image and return the result JSON.");
      assertThat(userMessage.getMedia()).hasSize(1);
      assertThat(prompt.getOptions()).isInstanceOf(GoogleGenAiChatOptions.class);
      GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) prompt.getOptions();
      assertThat(options.getModel()).isEqualTo("gemini-2.5-flash-lite");
      assertThat(options.getResponseMimeType()).isEqualTo("application/json");
      assertThat(options.getResponseSchema()).isEqualTo("{\"type\":\"object\"}");
    } finally {
      Files.deleteIfExists(image);
    }
  }

  @Test
  void mapsTimeoutToAiTimeoutException() throws IOException {
    ChatModel chatModel = mock(ChatModel.class);
    when(chatModel.call(any(Prompt.class)))
        .thenAnswer(
            invocation -> {
              Thread.sleep(1_500L);
              return mock(ChatResponse.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            });

    SpringAiGoogleGenAiJsonClient client =
        new SpringAiGoogleGenAiJsonClient(chatModel, runtimeProperties(1), executor);

    Path image = Files.createTempFile("verification-ai-timeout", ".png");
    try {
      assertThatThrownBy(
              () ->
                  client.analyzeWorkoutRecord(
                      image,
                      "system",
                      "Analyze this image and return the result JSON.",
                      "{\"type\":\"object\"}"))
          .isInstanceOf(AiTimeoutException.class);
    } finally {
      Files.deleteIfExists(image);
    }
  }

  @Test
  void mapsModelFailuresToAiUnavailableException() throws IOException {
    ChatModel chatModel = mock(ChatModel.class);
    when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("boom"));

    SpringAiGoogleGenAiJsonClient client =
        new SpringAiGoogleGenAiJsonClient(chatModel, runtimeProperties(3), executor);

    Path image = Files.createTempFile("verification-ai-unavailable", ".png");
    try {
      assertThatThrownBy(
              () ->
                  client.analyzeWorkoutPhoto(
                      image,
                      "system",
                      "Analyze this image and return the result JSON.",
                      "{\"type\":\"object\"}"))
          .isInstanceOf(AiUnavailableException.class)
          .hasMessageContaining("Gemini");
    } finally {
      Files.deleteIfExists(image);
    }
  }

  private VerificationRuntimeProperties runtimeProperties(int timeoutSeconds) {
    return new VerificationRuntimeProperties(
        new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", timeoutSeconds, 2, false),
        new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
        new VerificationRuntimeProperties.Image(5242880L, 25000000L));
  }
}
