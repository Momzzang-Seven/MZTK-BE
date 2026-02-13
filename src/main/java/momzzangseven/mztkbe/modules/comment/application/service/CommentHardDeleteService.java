package momzzangseven.mztkbe.modules.comment.application.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.comment.InvalidCommentConfigException;
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

    validateProperties();

    LocalDateTime cutoff = now.minusDays(props.getRetentionDays());
    List<Long> targetIds = loadCommentPort.loadCommentIdsForDeletion(cutoff, props.getBatchSize());

    if (targetIds.isEmpty()) {
      return 0;
    }

    deleteCommentPort.deleteAllById(targetIds);

    eventPublisher.publishEvent(new CommentsHardDeletedEvent(targetIds));

    log.info("Comment hard delete batch completed: count={}, cutoff={}", targetIds.size(), cutoff);
    return targetIds.size();
  }

  private void validateProperties() {
    if (props.getRetentionDays() <= 0 || props.getBatchSize() <= 0) {
      throw new InvalidCommentConfigException();
    }
  }
}
