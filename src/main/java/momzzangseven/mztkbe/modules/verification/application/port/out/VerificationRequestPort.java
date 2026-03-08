package momzzangseven.mztkbe.modules.verification.application.port.out;

import java.time.LocalDate;
import java.util.Optional;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;

/** Outbound port for the verification request SSOT. */
public interface VerificationRequestPort {

  VerificationRequest save(VerificationRequest verificationRequest);

  Optional<VerificationRequest> findTodayActiveOrVerified(Long userId, LocalDate slotDate);

  boolean existsSameFingerprint(
      Long userId, VerificationKind verificationKind, String requestFingerprint);

  Optional<VerificationRequest> findLatestByUserId(Long userId);

  Optional<VerificationRequest> findByVerificationId(String verificationId);
}
