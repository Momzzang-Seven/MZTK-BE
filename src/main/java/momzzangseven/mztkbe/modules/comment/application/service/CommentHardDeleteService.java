package momzzangseven.mztkbe.modules.comment.application.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.comment.application.config.CommentHardDeleteProperties;
import momzzangseven.mztkbe.modules.comment.application.port.out.DeleteCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import momzzangseven.mztkbe.modules.comment.domain.event.CommentsHardDeletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentHardDeleteService {

  private final LoadCommentPort loadCommentPort;
  private final DeleteCommentPort deleteCommentPort;
  private final CommentHardDeleteProperties props;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public int runBatch(LocalDateTime now) {
    int retentionDays = props.getRetentionDays();
    int batchSize = props.getBatchSize();

    if (retentionDays <= 0 || batchSize <= 0) {
      throw new IllegalArgumentException("Comment hard-delete properties must be > 0");
    }

    // 기준 시간 계산 (오늘 - 30일)
    LocalDateTime cutoff = now.minusDays(retentionDays);

    // 삭제 대상 ID 조회
    List<Long> commentIds = loadCommentPort.loadCommentIdsForDeletion(cutoff, batchSize);

    if (commentIds.isEmpty()) {
      return 0;
    }

    // 1. 다른 모듈에 알리기 위한 이벤트 발행
    eventPublisher.publishEvent(new CommentsHardDeletedEvent(commentIds));

    // 2. 댓글 본체 하드 딜리트
    deleteCommentPort.deleteAllByIdInBatch(commentIds);

    log.info(
        "Hard deleted comments: count={}, cutoff={}, batchSize={}",
        commentIds.size(),
        cutoff,
        batchSize);

    return commentIds.size();
  }
}
