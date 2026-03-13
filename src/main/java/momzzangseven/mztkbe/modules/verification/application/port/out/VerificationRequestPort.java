package momzzangseven.mztkbe.modules.verification.application.port.out;

import java.time.LocalDate;
import java.util.Optional;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;

public interface VerificationRequestPort {

  Optional<VerificationRequest> findByTmpObjectKey(String tmpObjectKey);

  Optional<VerificationRequest> findByVerificationId(String verificationId);

  Optional<VerificationRequest> findByVerificationIdAndUserId(String verificationId, Long userId);

  Optional<VerificationRequest> findByTmpObjectKeyForUpdate(String tmpObjectKey);

  Optional<VerificationRequest> findByVerificationIdForUpdate(String verificationId);

  Optional<VerificationRequest> findLatestUpdatedToday(Long userId, LocalDate today);

  boolean transitionFailedToAnalyzing(String verificationId);

  VerificationRequest save(VerificationRequest request);
}
