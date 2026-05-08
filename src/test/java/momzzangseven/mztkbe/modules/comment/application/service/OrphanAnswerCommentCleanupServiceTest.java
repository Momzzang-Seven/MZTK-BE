package momzzangseven.mztkbe.modules.comment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.comment.application.port.out.DeleteCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadOrphanAnswerCommentCleanupBatchSizePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrphanAnswerCommentCleanupService unit test")
class OrphanAnswerCommentCleanupServiceTest {

  @Mock private DeleteCommentPort deleteCommentPort;
  @Mock private LoadOrphanAnswerCommentCleanupBatchSizePort loadBatchSizePort;

  @InjectMocks private OrphanAnswerCommentCleanupService cleanupService;

  @BeforeEach
  void setUp() {
    given(loadBatchSizePort.loadBatchSize()).willReturn(100);
  }

  @Test
  @DisplayName("runBatch() soft-deletes active orphan answer comments")
  void runBatch_softDeletesActiveOrphanAnswerComments() {
    given(deleteCommentPort.softDeleteActiveOrphanAnswerComments(100)).willReturn(2);

    int result = cleanupService.runBatch();

    assertThat(result).isEqualTo(2);
    verify(deleteCommentPort).softDeleteActiveOrphanAnswerComments(100);
  }

  @Test
  @DisplayName("runBatch() returns 0 when no orphan answer comments exist")
  void runBatch_returnsZeroWhenNoOrphans() {
    given(deleteCommentPort.softDeleteActiveOrphanAnswerComments(100)).willReturn(0);

    int result = cleanupService.runBatch();

    assertThat(result).isZero();
    verify(deleteCommentPort).softDeleteActiveOrphanAnswerComments(100);
  }

  @Test
  @DisplayName("runBatch() throws when batch size is not positive")
  void runBatch_invalidBatchSize_throwsIllegalStateException() {
    given(loadBatchSizePort.loadBatchSize()).willReturn(0);

    assertThatThrownBy(() -> cleanupService.runBatch())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("comment.answer-orphan-cleanup.batch-size must be positive");

    verify(deleteCommentPort, never()).softDeleteActiveOrphanAnswerComments(anyInt());
  }
}
