package momzzangseven.mztkbe.modules.verification.application.service;

import java.nio.file.Path;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationCommand;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationResult;
import momzzangseven.mztkbe.modules.verification.application.port.in.SubmitWorkoutRecordVerificationUseCase;
import momzzangseven.mztkbe.modules.verification.application.port.out.ExifMetadataPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.GrantXpPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.ImageCodecSupportPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.ObjectStoragePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareAnalysisImagePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutImageAiPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutUploadLookupPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.XpLedgerQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SubmitWorkoutRecordVerificationService extends AbstractSubmitWorkoutVerificationService
    implements SubmitWorkoutRecordVerificationUseCase {

  public SubmitWorkoutRecordVerificationService(
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
        || extension.equals("png")
        || extension.equals("heic")
        || extension.equals("heif");
  }

  @Override
  protected boolean needsExifValidation() {
    return false;
  }

  @Override
  protected String sourceRefPrefix() {
    return "workout-record-verification:";
  }

  @Override
  protected AiVerificationDecision analyzeWithAi(Path analysisImagePath) {
    return workoutImageAiPort.analyzeWorkoutRecord(analysisImagePath);
  }

  @Override
  protected int analysisMaxLongEdge() {
    return 1536;
  }

  @Override
  protected double analysisWebpQuality() {
    return 0.85d;
  }
}
