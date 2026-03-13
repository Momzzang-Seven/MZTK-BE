package momzzangseven.mztkbe.modules.verification.infrastructure.ai.adapter;

import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.config.VerificationRuntimeProperties;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutImageAiPort;
import momzzangseven.mztkbe.modules.verification.application.service.VerificationTimePolicy;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.infrastructure.ai.client.VerificationAiJsonClient;
import momzzangseven.mztkbe.modules.verification.infrastructure.config.VerificationPromptProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringAiGeminiWorkoutImageAiAdapter implements WorkoutImageAiPort {

  private final VerificationRuntimeProperties runtimeProperties;
  private final VerificationPromptProvider promptProvider;
  private final VerificationAiJsonClient aiJsonClient;
  private final VerificationAiResponseParser verificationAiResponseParser;
  private final VerificationTimePolicy verificationTimePolicy;

  @Override
  public AiVerificationDecision analyzeWorkoutPhoto(Path analysisImagePath) {
    if (runtimeProperties.ai().stubEnabled()) {
      return AiVerificationDecision.builder().approved(true).build();
    }

    ensurePromptLoaded(
        promptProvider.getWorkoutPhotoSystemInstruction(),
        promptProvider.getWorkoutPhotoUserPrompt(),
        promptProvider.getWorkoutPhotoResponseSchema(),
        "workout photo");
    WorkoutPhotoAiResponse response =
        verificationAiResponseParser.parseWorkoutPhoto(
            aiJsonClient.analyzeWorkoutPhoto(
                analysisImagePath,
                promptProvider.getWorkoutPhotoSystemInstruction(),
                promptProvider.getWorkoutPhotoUserPrompt(),
                promptProvider.getWorkoutPhotoResponseSchema()));
    return AiVerificationDecision.builder()
        .approved(response.workoutPhoto())
        .rejectionReasonCode(response.workoutPhoto() ? null : response.rejectionReasonCode())
        .build();
  }

  @Override
  public AiVerificationDecision analyzeWorkoutRecord(Path analysisImagePath) {
    if (runtimeProperties.ai().stubEnabled()) {
      return AiVerificationDecision.builder()
          .approved(true)
          .exerciseDate(verificationTimePolicy.today())
          .build();
    }

    ensurePromptLoaded(
        promptProvider.getWorkoutRecordSystemInstruction(),
        promptProvider.getWorkoutRecordUserPrompt(),
        promptProvider.getWorkoutRecordResponseSchema(),
        "workout record");
    WorkoutRecordAiResponse response =
        verificationAiResponseParser.parseWorkoutRecord(
            aiJsonClient.analyzeWorkoutRecord(
                analysisImagePath,
                promptProvider.getWorkoutRecordSystemInstruction(),
                promptProvider.getWorkoutRecordUserPrompt(),
                promptProvider.getWorkoutRecordResponseSchema()));
    return toWorkoutRecordDecision(response);
  }

  private AiVerificationDecision toWorkoutRecordDecision(WorkoutRecordAiResponse response) {
    if (response.workoutRecord()) {
      if (!verificationTimePolicy.isToday(response.exerciseDate())) {
        return AiVerificationDecision.builder()
            .approved(false)
            .exerciseDate(response.exerciseDate())
            .rejectionReasonCode(RejectionReasonCode.DATE_MISMATCH)
            .rejectionReasonDetail("visible date is not today")
            .build();
      }
      return AiVerificationDecision.builder()
          .approved(true)
          .exerciseDate(response.exerciseDate())
          .build();
    }
    return AiVerificationDecision.builder()
        .approved(false)
        .exerciseDate(response.exerciseDate())
        .rejectionReasonCode(response.rejectionReasonCode())
        .build();
  }

  private void ensurePromptLoaded(
      String systemInstruction, String userPrompt, String responseSchema, String target) {
    if (systemInstruction.isBlank() || userPrompt.isBlank() || responseSchema.isBlank()) {
      throw new IllegalStateException("Verification prompt resource is empty for " + target);
    }
  }
}
