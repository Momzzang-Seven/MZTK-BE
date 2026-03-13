package momzzangseven.mztkbe.modules.verification.application.port.out;

import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;

public interface GrantXpPort {
  int grantWorkoutXp(
      Long userId, VerificationKind verificationKind, String verificationId, String sourceRef);
}
