package momzzangseven.mztkbe.modules.verification.infrastructure.external.level.adapter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.verification.application.port.out.GrantXpPort;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerificationGrantXpAdapter implements GrantXpPort {

  private final GrantXpUseCase grantXpUseCase;
  private final Clock appClock;
  private final ZoneId appZoneId;

  @Override
  public int grantWorkoutXp(
      Long userId, VerificationKind verificationKind, String verificationId, String sourceRef) {
    String idempotencyKey = buildIdempotencyKey(verificationKind, verificationId);
    LocalDateTime occurredAt = LocalDateTime.ofInstant(appClock.instant(), appZoneId);
    GrantXpCommand command =
        GrantXpCommand.of(userId, XpType.WORKOUT, occurredAt, idempotencyKey, sourceRef);
    GrantXpResult result = grantXpUseCase.execute(command);
    return result.grantedXp();
  }

  private String buildIdempotencyKey(VerificationKind verificationKind, String verificationId) {
    return switch (verificationKind) {
      case WORKOUT_PHOTO -> "workout:photo-verification:" + verificationId;
      case WORKOUT_RECORD -> "workout:record-verification:" + verificationId;
    };
  }
}
