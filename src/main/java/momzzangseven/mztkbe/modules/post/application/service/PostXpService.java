package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.post.application.port.out.GrantPostXpPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostXpService {

  private final GrantPostXpPort grantPostXpPort;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long grantCreatePostXp(Long userId, Long postId) {

    log.info(
        "Granting XP for Post Creation (Transaction B Start): userId={}, postId={}",
        userId,
        postId);

    try {
      Long grantedXp = grantPostXpPort.grantCreatePostXp(userId, postId);

      if (grantedXp > 0) {
        log.info(
            "XP granted successfully (Transaction B Commit): userId={}, xp={}", userId, grantedXp);
      } else {
        log.info("XP not granted (Cap/Duplicate, Transaction B Commit): userId={}", userId);
      }

      return grantedXp;

    } catch (Exception e) {
      log.error(
          "Failed to grant XP due to system error (Transaction B Rollback): userId={}", userId, e);
      throw e;
    }
  }
}
