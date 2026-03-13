package momzzangseven.mztkbe.modules.verification.infrastructure.ai.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.config.VerificationRuntimeProperties;
import momzzangseven.mztkbe.modules.verification.application.exception.AiMalformedResponseException;
import momzzangseven.mztkbe.modules.verification.application.exception.AiTimeoutException;
import momzzangseven.mztkbe.modules.verification.application.exception.AiUnavailableException;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

@RequiredArgsConstructor
public class SpringAiGoogleGenAiJsonClient implements VerificationAiJsonClient {

  private static final String JSON_MIME_TYPE = "application/json";

  private final ChatModel chatModel;
  private final VerificationRuntimeProperties runtimeProperties;
  private final ExecutorService executor;

  @Override
  public String analyzeWorkoutPhoto(
      Path analysisImagePath,
      String systemInstruction,
      String userPrompt,
      String responseSchemaJson) {
    return analyze(analysisImagePath, systemInstruction, userPrompt, responseSchemaJson);
  }

  @Override
  public String analyzeWorkoutRecord(
      Path analysisImagePath,
      String systemInstruction,
      String userPrompt,
      String responseSchemaJson) {
    return analyze(analysisImagePath, systemInstruction, userPrompt, responseSchemaJson);
  }

  private String analyze(
      Path analysisImagePath,
      String systemInstruction,
      String userPrompt,
      String responseSchemaJson) {
    Future<ChatResponse> future =
        submitChatCall(analysisImagePath, systemInstruction, userPrompt, responseSchemaJson);
    try {
      return extractText(awaitResponse(future));
    } catch (TimeoutException ex) {
      future.cancel(true);
      throw new AiTimeoutException("Gemini call timed out", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new AiTimeoutException("Gemini call interrupted while waiting for response", ex);
    } catch (ExecutionException ex) {
      throw mapFailure(ex.getCause() == null ? ex : ex.getCause());
    } catch (RuntimeException ex) {
      throw mapFailure(ex);
    }
  }

  private Future<ChatResponse> submitChatCall(
      Path analysisImagePath,
      String systemInstruction,
      String userPrompt,
      String responseSchemaJson) {
    return executor.submit(
        () ->
            chatModel.call(
                buildPrompt(analysisImagePath, systemInstruction, userPrompt, responseSchemaJson)));
  }

  private ChatResponse awaitResponse(Future<ChatResponse> future)
      throws InterruptedException, ExecutionException, TimeoutException {
    return future.get(runtimeProperties.ai().timeoutSeconds(), TimeUnit.SECONDS);
  }

  private Prompt buildPrompt(
      Path analysisImagePath,
      String systemInstruction,
      String userPrompt,
      String responseSchemaJson) {
    return new Prompt(
        List.of(
            new SystemMessage(systemInstruction), buildUserMessage(analysisImagePath, userPrompt)),
        buildChatOptions(responseSchemaJson));
  }

  private UserMessage buildUserMessage(Path analysisImagePath, String userPrompt) {
    return UserMessage.builder()
        .text(userPrompt)
        .media(
            Media.builder()
                .mimeType(resolveMimeType(analysisImagePath))
                .data(new FileSystemResource(analysisImagePath))
                .build())
        .build();
  }

  private GoogleGenAiChatOptions buildChatOptions(String responseSchemaJson) {
    return GoogleGenAiChatOptions.builder()
        .model(runtimeProperties.ai().model())
        .temperature(0D)
        .responseMimeType(JSON_MIME_TYPE)
        .responseSchema(responseSchemaJson)
        .build();
  }

  private MimeType resolveMimeType(Path analysisImagePath) {
    String contentType = probeContentType(analysisImagePath);
    return MimeTypeUtils.parseMimeType(
        contentType == null ? fallbackContentType(analysisImagePath) : contentType);
  }

  private String probeContentType(Path analysisImagePath) {
    try {
      return Files.probeContentType(analysisImagePath);
    } catch (Exception ignored) {
      return null;
    }
  }

  private String fallbackContentType(Path analysisImagePath) {
    String filename =
        analysisImagePath.getFileName() == null
            ? ""
            : analysisImagePath.getFileName().toString().toLowerCase();
    if (filename.endsWith(".webp")) {
      return "image/webp";
    }
    if (filename.endsWith(".png")) {
      return MimeTypeUtils.IMAGE_PNG_VALUE;
    }
    return MimeTypeUtils.IMAGE_JPEG_VALUE;
  }

  private String extractText(ChatResponse response) {
    String text =
        response == null || response.getResult() == null || response.getResult().getOutput() == null
            ? null
            : response.getResult().getOutput().getText();
    if (text == null || text.isBlank()) {
      throw new AiMalformedResponseException("Gemini returned empty text response");
    }
    return text;
  }

  private RuntimeException mapFailure(Throwable cause) {
    if (cause instanceof AiTimeoutException timeoutException) {
      return timeoutException;
    }
    return new AiUnavailableException("Gemini call failed", cause);
  }
}
