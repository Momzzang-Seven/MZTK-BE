package momzzangseven.mztkbe.modules.verification.application.port.out;

import java.time.LocalDateTime;

/** Outbound port for delegating workout XP grants to the level module. */
public interface GrantXpPort {
  void grantWorkoutXp(String verificationId, Long userId, LocalDateTime occurredAt);
}
