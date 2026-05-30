package momzzangseven.mztkbe.modules.comment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.comment.application.dto.CommentMutationResult;
import momzzangseven.mztkbe.modules.comment.application.dto.CreateCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.port.out.GrantCommentXpPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateCommentFacade unit test (sequential T1 -> T2 orchestration)")
class CreateCommentFacadeTest {

  @Mock private CommentService commentService;
  @Mock private GrantCommentXpPort grantCommentXpPort;

  private CreateCommentFacade facade;

  @BeforeEach
  void setUp() {
    facade = new CreateCommentFacade(commentService, grantCommentXpPort);
  }

  private CommentMutationResult saved() {
    LocalDateTime now = LocalDateTime.now();
    return new CommentMutationResult(1L, "hello", 200L, null, false, now, now);
  }

  @Test
  @DisplayName("saves comment first, then grants XP with writerId and commentId")
  void savesThenGrants() {
    CommentMutationResult result = saved();
    when(commentService.createComment(any())).thenReturn(result);
    when(grantCommentXpPort.grantCreateCommentXp(200L, 1L)).thenReturn(10L);

    CommentMutationResult returned =
        facade.createComment(new CreateCommentCommand(100L, 200L, null, "hello"));

    InOrder inOrder = Mockito.inOrder(commentService, grantCommentXpPort);
    inOrder.verify(commentService).createComment(any());
    inOrder.verify(grantCommentXpPort).grantCreateCommentXp(200L, 1L);

    assertThat(returned).isSameAs(result);
  }

  @Test
  @DisplayName("does not grant XP when the save transaction fails")
  void doesNotGrantWhenSaveFails() {
    when(commentService.createComment(any())).thenThrow(new RuntimeException("save failed"));

    assertThatThrownBy(
            () -> facade.createComment(new CreateCommentCommand(100L, 200L, null, "hello")))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("save failed");

    verify(grantCommentXpPort, never()).grantCreateCommentXp(any(), any());
  }
}
