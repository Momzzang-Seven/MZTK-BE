package momzzangseven.mztkbe.modules.verification.application.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.verification.InvalidTmpObjectKeyException;
import momzzangseven.mztkbe.global.error.verification.InvalidVerificationImageExtensionException;
import momzzangseven.mztkbe.global.error.verification.VerificationUploadForbiddenException;
import momzzangseven.mztkbe.modules.verification.application.dto.WorkoutUploadReference;
import momzzangseven.mztkbe.modules.verification.application.port.out.ImageCodecSupportPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerificationSubmissionValidator {

  private final VerificationImagePolicy verificationImagePolicy;
  private final ImageCodecSupportPort imageCodecSupportPort;

  public void validateSubmitInput(String tmpObjectKey, VerificationSubmissionPolicy policy) {
    validateTmpObjectKey(tmpObjectKey);
    validateExtension(extractExtension(tmpObjectKey), policy);
  }

  public void validateExistingOwnership(Long userId, VerificationRequest request) {
    if (!userId.equals(request.getUserId())) {
      throw new VerificationUploadForbiddenException();
    }
  }

  public void validateUploadOwnership(Long userId, WorkoutUploadReference upload) {
    if (!userId.equals(upload.ownerUserId())) {
      throw new VerificationUploadForbiddenException();
    }
  }

  public String extractExtension(String tmpObjectKey) {
    int dot = tmpObjectKey.lastIndexOf('.');
    if (dot <= 0 || dot == tmpObjectKey.length() - 1) {
      throw new InvalidVerificationImageExtensionException();
    }
    return tmpObjectKey.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private void validateTmpObjectKey(String tmpObjectKey) {
    if (tmpObjectKey == null
        || tmpObjectKey.isBlank()
        || !tmpObjectKey.startsWith("private/workout/")) {
      throw new InvalidTmpObjectKeyException();
    }
  }

  private void validateExtension(String extension, VerificationSubmissionPolicy policy) {
    if ((extension.equals("heic") || extension.equals("heif"))
        && (!verificationImagePolicy.isHeifEnabled()
            || !imageCodecSupportPort.isHeifDecodeAvailable())) {
      throw new InvalidVerificationImageExtensionException();
    }
    if (!policy.allowsExtension(extension)) {
      throw new InvalidVerificationImageExtensionException();
    }
  }
}
