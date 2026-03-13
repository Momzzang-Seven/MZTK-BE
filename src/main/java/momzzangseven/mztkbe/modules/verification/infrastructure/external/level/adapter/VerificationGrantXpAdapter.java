package momzzangseven.mztkbe.modules.verification.infrastructure.external.level.adapter;

import java.time.LocalDateTime;
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

  @Override
  public int grantWorkoutXp(
      Long userId, VerificationKind verificationKind, String verificationId, String sourceRef) {
    String idempotencyKey = buildIdempotencyKey(verificationKind, verificationId);
    GrantXpCommand command =
        GrantXpCommand.of(userId, XpType.WORKOUT, LocalDateTime.now(), idempotencyKey, sourceRef);
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
