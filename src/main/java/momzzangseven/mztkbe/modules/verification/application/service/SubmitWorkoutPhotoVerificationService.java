package momzzangseven.mztkbe.modules.verification.application.service;

import java.nio.file.Path;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationCommand;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationResult;
import momzzangseven.mztkbe.modules.verification.application.port.in.SubmitWorkoutPhotoVerificationUseCase;
import momzzangseven.mztkbe.modules.verification.application.port.out.ExifMetadataPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.GrantXpPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.ImageCodecSupportPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.ObjectStoragePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareAnalysisImagePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutImageAiPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutUploadLookupPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.XpLedgerQueryPort;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SubmitWorkoutPhotoVerificationService extends AbstractSubmitWorkoutVerificationService
    implements SubmitWorkoutPhotoVerificationUseCase {

  public SubmitWorkoutPhotoVerificationService(
      VerificationRequestPort verificationRequestPort,
      WorkoutUploadLookupPort workoutUploadLookupPort,
      ObjectStoragePort objectStoragePort,
      PrepareAnalysisImagePort prepareAnalysisImagePort,
      ExifMetadataPort exifMetadataPort,
      WorkoutImageAiPort workoutImageAiPort,
      GrantXpPort grantXpPort,
      XpLedgerQueryPort xpLedgerQueryPort,
      ImageCodecSupportPort imageCodecSupportPort,
      VerificationTimePolicy verificationTimePolicy,
      VerificationImagePolicy verificationImagePolicy) {
    super(
        verificationRequestPort,
        workoutUploadLookupPort,
        objectStoragePort,
        prepareAnalysisImagePort,
        exifMetadataPort,
        workoutImageAiPort,
        grantXpPort,
        xpLedgerQueryPort,
        imageCodecSupportPort,
        verificationTimePolicy,
        verificationImagePolicy);
  }

  @Override
  public SubmitWorkoutVerificationResult execute(SubmitWorkoutVerificationCommand command) {
    return super.execute(command);
  }

  @Override
  protected boolean isAllowedExtension(String extension) {
    return extension.equals("jpg")
        || extension.equals("jpeg")
        || extension.equals("heic")
        || extension.equals("heif");
  }

  @Override
  protected boolean needsExifValidation() {
    return true;
  }

  @Override
  protected RejectionReasonCode defaultRejectCode() {
    return RejectionReasonCode.NOT_EXERCISE_PHOTO;
  }

  @Override
  protected String sourceRefPrefix() {
    return "workout-photo-verification:";
  }

  @Override
  protected AiVerificationDecision analyzeWithAi(Path analysisImagePath) {
    return workoutImageAiPort.analyzeWorkoutPhoto(analysisImagePath);
  }

  @Override
  protected int analysisMaxLongEdge() {
    return 1024;
  }

  @Override
  protected double analysisWebpQuality() {
    return 0.80d;
  }
}
