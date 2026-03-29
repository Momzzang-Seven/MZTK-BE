package momzzangseven.mztkbe.modules.comment.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.comment.application.port.out.GrantCommentXpPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentXpService {

  private final GrantCommentXpPort grantCommentXpPort;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long grantCreateCommentXp(Long userId, Long commentId) {
    log.info(
        "Granting XP for Comment Creation (Transaction B Start): userId={}, commentId={}",
        userId,
        commentId);

    try {
      Long grantedXp = grantCommentXpPort.grantCreateCommentXp(userId, commentId);

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
