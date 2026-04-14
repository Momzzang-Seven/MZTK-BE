package momzzangseven.mztkbe.modules.verification.infrastructure.ai.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.exception.AiMalformedResponseException;
import momzzangseven.mztkbe.modules.verification.application.exception.AiResponseSchemaInvalidException;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerificationAiResponseParser {

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
          RejectionReasonCode.LOW_CONFIDENCE);

  private final ObjectMapper objectMapper;

  public WorkoutPhotoAiResponse parseWorkoutPhoto(String response) {
    JsonNode root = readTree(response);
    boolean workoutPhoto = requiredBoolean(root, "workoutPhoto");
    RejectionReasonCode rejectionReasonCode =
        nullableRejectionReason(root, "rejectionReasonCode", WORKOUT_PHOTO_REJECTION_CODES);
    double confidenceScore = requiredScore(root, "confidenceScore");
    if (!workoutPhoto && rejectionReasonCode == null) {
      throw new AiResponseSchemaInvalidException("workoutPhoto=false requires rejectionReasonCode");
    }
    return new WorkoutPhotoAiResponse(workoutPhoto, rejectionReasonCode, confidenceScore);
  }

  public WorkoutRecordAiResponse parseWorkoutRecord(String response) {
    JsonNode root = readTree(response);
    boolean workoutRecord = requiredBoolean(root, "workoutRecord");
    RejectionReasonCode rejectionReasonCode =
        nullableRejectionReason(root, "rejectionReasonCode", WORKOUT_RECORD_REJECTION_CODES);
    boolean dateVisible = requiredBoolean(root, "dateVisible");
    LocalDate exerciseDate = nullableDate(root, "exerciseDate");
    double confidenceScore = requiredScore(root, "confidenceScore");

    if (workoutRecord && rejectionReasonCode != null) {
      throw new AiResponseSchemaInvalidException(
          "workoutRecord=true requires rejectionReasonCode=null");
    }
    if (workoutRecord && !dateVisible) {
      throw new AiResponseSchemaInvalidException("workoutRecord=true requires dateVisible=true");
    }
    if (workoutRecord && exerciseDate == null) {
      throw new AiResponseSchemaInvalidException("workoutRecord=true requires exerciseDate");
    }
    if (!workoutRecord && rejectionReasonCode == null) {
      throw new AiResponseSchemaInvalidException(
          "workoutRecord=false requires rejectionReasonCode");
    }

    return new WorkoutRecordAiResponse(
        workoutRecord, rejectionReasonCode, dateVisible, exerciseDate, confidenceScore);
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
