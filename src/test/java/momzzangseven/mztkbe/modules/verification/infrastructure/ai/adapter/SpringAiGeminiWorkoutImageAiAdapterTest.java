package momzzangseven.mztkbe.modules.verification.infrastructure.ai.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import momzzangseven.mztkbe.modules.verification.application.config.VerificationRuntimeProperties;
import momzzangseven.mztkbe.modules.verification.application.exception.AiMalformedResponseException;
import momzzangseven.mztkbe.modules.verification.application.exception.AiResponseSchemaInvalidException;
import momzzangseven.mztkbe.modules.verification.application.service.VerificationTimePolicy;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.infrastructure.ai.client.VerificationAiJsonClient;
import momzzangseven.mztkbe.modules.verification.infrastructure.config.VerificationPromptProvider;
import org.junit.jupiter.api.Test;

class SpringAiGeminiWorkoutImageAiAdapterTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Test
  void returnsApprovedResultInStubMode() {
    VerificationRuntimeProperties properties =
        new VerificationRuntimeProperties(
            new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, true),
            new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
            new VerificationRuntimeProperties.Image(5242880L, 25000000L));
    SpringAiGeminiWorkoutImageAiAdapter adapter =
        new SpringAiGeminiWorkoutImageAiAdapter(
            properties,
            new VerificationPromptProvider(),
            unavailableClient(),
            new ObjectMapper(),
            fixedTimePolicy());

    var result = adapter.analyzeWorkoutPhoto(Path.of("analysis.webp"));

    assertThat(result.approved()).isTrue();
    assertThat(result.exerciseDate()).isNull();
  }

  @Test
  void parsesRejectedWorkoutPhotoResponseWhenStubDisabled() {
    VerificationRuntimeProperties properties =
        new VerificationRuntimeProperties(
            new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, false),
            new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
            new VerificationRuntimeProperties.Image(5242880L, 25000000L));
    SpringAiGeminiWorkoutImageAiAdapter adapter =
        new SpringAiGeminiWorkoutImageAiAdapter(
            properties,
            new VerificationPromptProvider(),
            fixedPhotoResponse(
                """
                {"workoutPhoto":false,"rejectionReasonCode":"NO_PERSON_VISIBLE","confidenceScore":0.12}
                """),
            new ObjectMapper(),
            fixedTimePolicy());

    var result = adapter.analyzeWorkoutPhoto(Path.of("analysis.webp"));

    assertThat(result.approved()).isFalse();
    assertThat(result.rejectionReasonCode()).isEqualTo(RejectionReasonCode.NO_PERSON_VISIBLE);
  }

  @Test
  void interpretsRecordDateMismatchAsDateMismatch() {
    VerificationRuntimeProperties properties =
        new VerificationRuntimeProperties(
            new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, false),
            new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
            new VerificationRuntimeProperties.Image(5242880L, 25000000L));
    SpringAiGeminiWorkoutImageAiAdapter adapter =
        new SpringAiGeminiWorkoutImageAiAdapter(
            properties,
            new VerificationPromptProvider(),
            fixedRecordResponse(
                """
                {"workoutRecord":true,"rejectionReasonCode":null,"dateVisible":true,"exerciseDate":"2026-03-12","confidenceScore":0.98}
                """),
            new ObjectMapper(),
            fixedTimePolicy());

    var result = adapter.analyzeWorkoutRecord(Path.of("analysis.webp"));

    assertThat(result.approved()).isFalse();
    assertThat(result.rejectionReasonCode()).isEqualTo(RejectionReasonCode.DATE_MISMATCH);
  }

  @Test
  void parsesRejectedWorkoutRecordResponseWhenDateIsNotVisible() {
    VerificationRuntimeProperties properties =
        new VerificationRuntimeProperties(
            new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, false),
            new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
            new VerificationRuntimeProperties.Image(5242880L, 25000000L));
    SpringAiGeminiWorkoutImageAiAdapter adapter =
        new SpringAiGeminiWorkoutImageAiAdapter(
            properties,
            new VerificationPromptProvider(),
            fixedRecordResponse(
                """
                {"workoutRecord":false,"rejectionReasonCode":"DATE_NOT_VISIBLE","dateVisible":false,"exerciseDate":null,"confidenceScore":0.21}
                """),
            new ObjectMapper(),
            fixedTimePolicy());

    var result = adapter.analyzeWorkoutRecord(Path.of("analysis.webp"));

    assertThat(result.approved()).isFalse();
    assertThat(result.rejectionReasonCode()).isEqualTo(RejectionReasonCode.DATE_NOT_VISIBLE);
  }

  @Test
  void throwsMalformedResponseWhenJsonIsInvalid() {
    VerificationRuntimeProperties properties =
        new VerificationRuntimeProperties(
            new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, false),
            new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
            new VerificationRuntimeProperties.Image(5242880L, 25000000L));
    SpringAiGeminiWorkoutImageAiAdapter adapter =
        new SpringAiGeminiWorkoutImageAiAdapter(
            properties,
            new VerificationPromptProvider(),
            fixedPhotoResponse("not-json"),
            new ObjectMapper(),
            fixedTimePolicy());

    assertThatThrownBy(() -> adapter.analyzeWorkoutPhoto(Path.of("analysis.webp")))
        .isInstanceOf(AiMalformedResponseException.class);
  }

  @Test
  void throwsSchemaInvalidWhenRequiredFieldIsMissing() {
    VerificationRuntimeProperties properties =
        new VerificationRuntimeProperties(
            new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, false),
            new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
            new VerificationRuntimeProperties.Image(5242880L, 25000000L));
    SpringAiGeminiWorkoutImageAiAdapter adapter =
        new SpringAiGeminiWorkoutImageAiAdapter(
            properties,
            new VerificationPromptProvider(),
            fixedRecordResponse(
                """
                {"workoutRecord":true,"rejectionReasonCode":null,"exerciseDate":"2026-03-13","confidenceScore":0.98}
                """),
            new ObjectMapper(),
            fixedTimePolicy());

    assertThatThrownBy(() -> adapter.analyzeWorkoutRecord(Path.of("analysis.webp")))
        .isInstanceOf(AiResponseSchemaInvalidException.class);
  }

  private VerificationAiJsonClient unavailableClient() {
    return new VerificationAiJsonClient() {
      @Override
      public String analyzeWorkoutPhoto(
          Path analysisImagePath,
          String systemInstruction,
          String userPrompt,
          String responseSchemaJson) {
        throw new IllegalStateException("unavailable");
      }

      @Override
      public String analyzeWorkoutRecord(
          Path analysisImagePath,
          String systemInstruction,
          String userPrompt,
          String responseSchemaJson) {
        throw new IllegalStateException("unavailable");
      }
    };
  }

  private VerificationAiJsonClient fixedPhotoResponse(String response) {
    return new VerificationAiJsonClient() {
      @Override
      public String analyzeWorkoutPhoto(
          Path analysisImagePath,
          String systemInstruction,
          String userPrompt,
          String responseSchemaJson) {
        return response;
      }

      @Override
      public String analyzeWorkoutRecord(
          Path analysisImagePath,
          String systemInstruction,
          String userPrompt,
          String responseSchemaJson) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private VerificationAiJsonClient fixedRecordResponse(String response) {
    return new VerificationAiJsonClient() {
      @Override
      public String analyzeWorkoutPhoto(
          Path analysisImagePath,
          String systemInstruction,
          String userPrompt,
          String responseSchemaJson) {
        throw new UnsupportedOperationException();
      }

      @Override
      public String analyzeWorkoutRecord(
          Path analysisImagePath,
          String systemInstruction,
          String userPrompt,
          String responseSchemaJson) {
        return response;
      }
    };
  }

  private VerificationTimePolicy fixedTimePolicy() {
    return new VerificationTimePolicy(
        KST, Clock.fixed(Instant.parse("2026-03-12T15:00:00Z"), KST));
  }
}
