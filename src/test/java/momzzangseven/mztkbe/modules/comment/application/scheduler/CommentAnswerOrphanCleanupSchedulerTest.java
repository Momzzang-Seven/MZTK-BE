package momzzangseven.mztkbe.modules.comment.application.scheduler;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.comment.application.port.in.RunOrphanAnswerCommentCleanupBatchUseCase;
import momzzangseven.mztkbe.modules.comment.infrastructure.scheduler.CommentAnswerOrphanCleanupScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentAnswerOrphanCleanupScheduler unit test")
class CommentAnswerOrphanCleanupSchedulerTest {

  @Mock private RunOrphanAnswerCommentCleanupBatchUseCase cleanupUseCase;

  @InjectMocks private CommentAnswerOrphanCleanupScheduler scheduler;

  @Test
  @DisplayName("run() calls cleanup once when first batch returns 0")
  void run_callsRunBatchOnceWhenNoWork() {
    given(cleanupUseCase.runBatch()).willReturn(0);

    scheduler.run();

    verify(cleanupUseCase, times(1)).runBatch();
  }

  @Test
  @DisplayName("run() repeats until cleanup returns 0")
  void run_repeatsUntilZero() {
    given(cleanupUseCase.runBatch()).willReturn(2).willReturn(1).willReturn(0);

    scheduler.run();

    verify(cleanupUseCase, times(3)).runBatch();
  }
}
