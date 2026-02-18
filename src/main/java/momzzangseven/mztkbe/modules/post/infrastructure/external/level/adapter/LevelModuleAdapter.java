package momzzangseven.mztkbe.modules.post.infrastructure.external.level.adapter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.post.application.port.out.GrantPostXpPort;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LevelModuleAdapter implements GrantPostXpPort {

  private final GrantXpUseCase grantXpUseCase;
  private final ZoneId appZoneId;

  @Override
  public Long grantCreatePostXp(Long userId, Long postId) {

    LocalDateTime occurredAt = LocalDateTime.now(appZoneId);

    String idempotencyKey = "post:create:" + postId;

    String sourceRef = "post-creation:" + postId;

    log.debug("Granting XP for Post Creation: userId={}, key={}", userId, idempotencyKey);

    GrantXpCommand command =
        GrantXpCommand.of(userId, XpType.POST, occurredAt, idempotencyKey, sourceRef);

    GrantXpResult result = grantXpUseCase.execute(command);

    if (result.grantedXp() > 0) {
      log.info("XP granted for Post Creation: userId={}, xp={}", userId, result.grantedXp());
    } else {

      log.info("XP not granted: userId={}, reason={}", userId, result.status());
    }

    return (long) result.grantedXp();
  }
}
