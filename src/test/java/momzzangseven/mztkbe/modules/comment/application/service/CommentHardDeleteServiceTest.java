package momzzangseven.mztkbe.modules.comment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.error.comment.InvalidCommentConfigException;
import momzzangseven.mztkbe.modules.comment.application.config.CommentHardDeleteProperties;
import momzzangseven.mztkbe.modules.comment.application.port.out.DeleteCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import momzzangseven.mztkbe.modules.comment.domain.event.CommentsHardDeletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentHardDeleteService unit test")
class CommentHardDeleteServiceTest {

  @Mock private LoadCommentPort loadCommentPort;
  @Mock private DeleteCommentPort deleteCommentPort;
  @Mock private ApplicationEventPublisher eventPublisher;

  private CommentHardDeleteProperties props;
  private CommentHardDeleteService commentHardDeleteService;

  @BeforeEach
  void setUp() {
    props = new CommentHardDeleteProperties();
    commentHardDeleteService =
        new CommentHardDeleteService(loadCommentPort, deleteCommentPort, props, eventPublisher);
  }

  @Test
  @DisplayName("runBatch() deletes comments and publishes event")
  void runBatch_deletesCommentsAndPublishesEvent() {
    props.setRetentionDays(30);
    props.setBatchSize(100);
    LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);
    LocalDateTime cutoff = now.minusDays(30);
    List<Long> targetIds = List.of(1L, 2L, 3L);

    given(loadCommentPort.loadCommentIdsForDeletion(cutoff, 100)).willReturn(targetIds);

    int deletedCount = commentHardDeleteService.runBatch(now);

    assertThat(deletedCount).isEqualTo(3);
    verify(deleteCommentPort).deleteAllById(targetIds);

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue()).isInstanceOf(CommentsHardDeletedEvent.class);
    CommentsHardDeletedEvent event = (CommentsHardDeletedEvent) eventCaptor.getValue();
    assertThat(event.commentIds()).containsExactly(1L, 2L, 3L);
  }

  @Test
  @DisplayName("runBatch() returns 0 when target list is empty")
  void runBatch_returnsZeroWhenNoTargets() {
    props.setRetentionDays(30);
    props.setBatchSize(100);
    LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);

    given(loadCommentPort.loadCommentIdsForDeletion(now.minusDays(30), 100)).willReturn(List.of());

    int deletedCount = commentHardDeleteService.runBatch(now);

    assertThat(deletedCount).isZero();
    verify(deleteCommentPort, never()).deleteAllById(org.mockito.ArgumentMatchers.anyList());
    verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("runBatch() throws when hard-delete properties are invalid")
  void runBatch_invalidProperties_throwsInvalidCommentConfigException() {
    props.setRetentionDays(0);
    props.setBatchSize(100);

    assertThatThrownBy(() -> commentHardDeleteService.runBatch(LocalDateTime.now()))
        .isInstanceOf(InvalidCommentConfigException.class);

    verify(loadCommentPort, never())
        .loadCommentIdsForDeletion(
            org.mockito.ArgumentMatchers.any(LocalDateTime.class),
            org.mockito.ArgumentMatchers.anyInt());
  }
}
