package momzzangseven.mztkbe.modules.verification.infrastructure.ai.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.config.VerificationRuntimeProperties;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.exception.AiMalformedResponseException;
import momzzangseven.mztkbe.modules.verification.application.exception.AiResponseSchemaInvalidException;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutImageAiPort;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.infrastructure.ai.client.VerificationAiJsonClient;
import momzzangseven.mztkbe.modules.verification.infrastructure.config.VerificationPromptProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringAiGeminiWorkoutImageAiAdapter implements WorkoutImageAiPort {

  private static final Set<RejectionReasonCode> WORKOUT_PHOTO_REJECTION_CODES =
      Set.of(
          RejectionReasonCode.SCREEN_OR_UI,
          RejectionReasonCode.NO_PERSON_VISIBLE,
          RejectionReasonCode.EQUIPMENT_ONLY,
          RejectionReasonCode.INSUFFICIENT_WORKOUT_CONTEXT,
          RejectionReasonCode.LOW_CONFIDENCE);

  private static final Set<RejectionReasonCode> WORKOUT_RECORD_REJECTION_CODES =
      Set.of(
          RejectionReasonCode.NOT_WORKOUT_RECORD,
          RejectionReasonCode.DATE_NOT_VISIBLE,
          RejectionReasonCode.MISSING_OR_INVALID_DATE,
          RejectionReasonCode.NOT_TODAY_EXERCISE,
          RejectionReasonCode.LOW_CONFIDENCE);

  private final VerificationRuntimeProperties runtimeProperties;
  private final VerificationPromptProvider promptProvider;
  private final VerificationAiJsonClient aiJsonClient;
  private final ObjectMapper objectMapper;
  private final ZoneId appZoneId;

  @Override
  public AiVerificationDecision analyzeWorkoutPhoto(Path analysisImagePath) {
    if (runtimeProperties.ai().stubEnabled()) {
      return AiVerificationDecision.builder()
          .approved(true)
          .exerciseDate(LocalDate.now(appZoneId))
          .build();
    }
    ensurePromptLoaded(
        promptProvider.getWorkoutPhotoSystemInstruction(),
        promptProvider.getWorkoutPhotoUserPrompt(),
        promptProvider.getWorkoutPhotoResponseSchema(),
        "workout photo");

    JsonNode root =
        readTree(
            aiJsonClient.analyzeWorkoutPhoto(
                analysisImagePath,
                promptProvider.getWorkoutPhotoSystemInstruction(),
                promptProvider.getWorkoutPhotoUserPrompt(),
                promptProvider.getWorkoutPhotoResponseSchema()));

    boolean workoutPhoto = requiredBoolean(root, "workoutPhoto");
    RejectionReasonCode rejectionReasonCode =
        nullableRejectionReason(root, "rejectionReasonCode", WORKOUT_PHOTO_REJECTION_CODES);
    requiredScore(root, "confidenceScore");

    if (!workoutPhoto && rejectionReasonCode == null) {
      throw new AiResponseSchemaInvalidException("workoutPhoto=false requires rejectionReasonCode");
    }

    return AiVerificationDecision.builder()
        .approved(workoutPhoto)
        .rejectionReasonCode(workoutPhoto ? null : rejectionReasonCode)
        .build();
  }

  @Override
  public AiVerificationDecision analyzeWorkoutRecord(Path analysisImagePath) {
    if (runtimeProperties.ai().stubEnabled()) {
      return AiVerificationDecision.builder()
          .approved(true)
          .exerciseDate(LocalDate.now(appZoneId))
          .build();
    }
    ensurePromptLoaded(
        promptProvider.getWorkoutRecordSystemInstruction(),
        promptProvider.getWorkoutRecordUserPrompt(),
        promptProvider.getWorkoutRecordResponseSchema(),
        "workout record");

    JsonNode root =
        readTree(
            aiJsonClient.analyzeWorkoutRecord(
                analysisImagePath,
                promptProvider.getWorkoutRecordSystemInstruction(),
                promptProvider.getWorkoutRecordUserPrompt(),
                promptProvider.getWorkoutRecordResponseSchema()));

    boolean workoutRecord = requiredBoolean(root, "workoutRecord");
    RejectionReasonCode rejectionReasonCode =
        nullableRejectionReason(root, "rejectionReasonCode", WORKOUT_RECORD_REJECTION_CODES);
    boolean dateVisible = requiredBoolean(root, "dateVisible");
    LocalDate exerciseDate = nullableDate(root, "exerciseDate");
    requiredScore(root, "confidenceScore");

    if (workoutRecord) {
      if (rejectionReasonCode != null) {
        throw new AiResponseSchemaInvalidException(
            "workoutRecord=true requires rejectionReasonCode=null");
      }
      if (!dateVisible) {
        throw new AiResponseSchemaInvalidException("workoutRecord=true requires dateVisible=true");
      }
      if (exerciseDate == null) {
        throw new AiResponseSchemaInvalidException("workoutRecord=true requires exerciseDate");
      }
      if (!exerciseDate.equals(LocalDate.now(appZoneId))) {
        return AiVerificationDecision.builder()
            .approved(false)
            .exerciseDate(exerciseDate)
            .rejectionReasonCode(RejectionReasonCode.NOT_TODAY_EXERCISE)
            .build();
      }
      return AiVerificationDecision.builder().approved(true).exerciseDate(exerciseDate).build();
    }

    if (rejectionReasonCode == null) {
      throw new AiResponseSchemaInvalidException(
          "workoutRecord=false requires rejectionReasonCode");
    }

    return AiVerificationDecision.builder()
        .approved(false)
        .exerciseDate(exerciseDate)
        .rejectionReasonCode(rejectionReasonCode)
        .build();
  }

  private void ensurePromptLoaded(
      String systemInstruction, String userPrompt, String responseSchema, String target) {
    if (systemInstruction.isBlank() || userPrompt.isBlank() || responseSchema.isBlank()) {
      throw new IllegalStateException("Verification prompt resource is empty for " + target);
    }
  }

  private JsonNode readTree(String response) {
    try {
      return objectMapper.readTree(response);
    } catch (JsonProcessingException ex) {
      throw new AiMalformedResponseException("AI returned malformed JSON", ex);
    }
  }

  private boolean requiredBoolean(JsonNode root, String fieldName) {
    JsonNode node = requiredNode(root, fieldName);
    if (!node.isBoolean()) {
      throw new AiResponseSchemaInvalidException(fieldName + " must be boolean");
    }
    return node.booleanValue();
  }

  private double requiredScore(JsonNode root, String fieldName) {
    JsonNode node = requiredNode(root, fieldName);
    if (!node.isNumber()) {
      throw new AiResponseSchemaInvalidException(fieldName + " must be numeric");
    }
    double score = node.doubleValue();
    if (score < 0 || score > 1) {
      throw new AiResponseSchemaInvalidException(fieldName + " must be between 0 and 1");
    }
    return score;
  }

  private LocalDate nullableDate(JsonNode root, String fieldName) {
    JsonNode node = requiredNode(root, fieldName);
    if (node.isNull()) {
      return null;
    }
    if (!node.isTextual()) {
      throw new AiResponseSchemaInvalidException(fieldName + " must be string or null");
    }
    try {
      return LocalDate.parse(node.textValue());
    } catch (RuntimeException ex) {
      throw new AiResponseSchemaInvalidException(fieldName + " must be YYYY-MM-DD", ex);
    }
  }

  private RejectionReasonCode nullableRejectionReason(
      JsonNode root, String fieldName, Set<RejectionReasonCode> allowedCodes) {
    JsonNode node = requiredNode(root, fieldName);
    if (node.isNull()) {
      return null;
    }
    if (!node.isTextual()) {
      throw new AiResponseSchemaInvalidException(fieldName + " must be string or null");
    }

    RejectionReasonCode code;
    try {
      code = RejectionReasonCode.valueOf(node.textValue());
    } catch (IllegalArgumentException ex) {
      throw new AiResponseSchemaInvalidException(fieldName + " contains unsupported code", ex);
    }
    if (!allowedCodes.contains(code)) {
      throw new AiResponseSchemaInvalidException(fieldName + " contains unsupported code");
    }
    return code;
  }

  private JsonNode requiredNode(JsonNode root, String fieldName) {
    JsonNode node = root.get(fieldName);
    if (node == null) {
      throw new AiResponseSchemaInvalidException("Missing required field: " + fieldName);
    }
    return node;
  }
}
