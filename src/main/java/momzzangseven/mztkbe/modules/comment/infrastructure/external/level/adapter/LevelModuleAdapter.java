package momzzangseven.mztkbe.modules.comment.infrastructure.external.level.adapter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.comment.application.port.out.GrantCommentXpPort;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.springframework.stereotype.Component;

@Slf4j
@Component("commentLevelModuleAdapter")
@RequiredArgsConstructor
public class LevelModuleAdapter implements GrantCommentXpPort {

  private final GrantXpUseCase grantXpUseCase;
  private final ZoneId appZoneId;

  @Override
  public Long grantCreateCommentXp(Long userId, Long commentId) {
    LocalDateTime occurredAt = LocalDateTime.now(appZoneId);
    String idempotencyKey = "comment:create:" + commentId;
    String sourceRef = "comment-creation:" + commentId;

    log.debug("Granting XP for Comment Creation: userId={}, key={}", userId, idempotencyKey);

    GrantXpCommand command =
        GrantXpCommand.of(userId, XpType.COMMENT, occurredAt, idempotencyKey, sourceRef);

    GrantXpResult result = grantXpUseCase.execute(command);

    if (result.grantedXp() > 0) {
      log.info("XP granted for Comment Creation: userId={}, xp={}", userId, result.grantedXp());
    } else {
      log.info("XP not granted: userId={}, reason={}", userId, result.status());
    }

    return (long) result.grantedXp();
  }
}
