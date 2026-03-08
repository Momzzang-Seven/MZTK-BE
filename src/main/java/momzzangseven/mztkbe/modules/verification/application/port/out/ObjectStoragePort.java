package momzzangseven.mztkbe.modules.verification.application.port.out;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;

/** Outbound port for temporary verification image storage. */
public interface ObjectStoragePort {

  StoredObject putTemp(Long userId, VerificationKind verificationKind, byte[] imageBytes);

  byte[] read(String objectKey);

  void delete(String objectKey);

  /** Stored object metadata returned after a temp upload succeeds. */
  record StoredObject(String objectKey, LocalDateTime expiresAt) {}
}
