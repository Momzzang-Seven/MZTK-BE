package momzzangseven.mztkbe.modules.comment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.comment.CommentNotFoundException;
import momzzangseven.mztkbe.modules.comment.application.dto.CommentResult;
import momzzangseven.mztkbe.modules.comment.application.dto.CreateCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRepliesQuery;
import momzzangseven.mztkbe.modules.comment.application.port.out.DeleteCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.SaveCommentPort;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentService unit test")
class CommentServiceTest {

  @Mock private LoadCommentPort loadCommentPort;
  @Mock private SaveCommentPort saveCommentPort;
  @Mock private LoadPostPort loadPostPort;
  @Mock private DeleteCommentPort deleteCommentPort;

  @InjectMocks private CommentService commentService;

  @Test
  @DisplayName("createComment() creates root comment when post exists")
  void createComment_createsRootCommentWhenPostExists() {
    CreateCommentCommand command = new CreateCommentCommand(100L, 200L, null, "hello");

    given(loadPostPort.existsPost(100L)).willReturn(true);
    given(saveCommentPort.saveComment(any(Comment.class)))
        .willAnswer(
            invocation -> {
              Comment input = invocation.getArgument(0);
              return Comment.builder()
                  .id(1L)
                  .postId(input.getPostId())
                  .writerId(input.getWriterId())
                  .parentId(input.getParentId())
                  .content(input.getContent())
                  .isDeleted(input.isDeleted())
                  .createdAt(input.getCreatedAt())
                  .updatedAt(input.getUpdatedAt())
                  .build();
            });

    CommentResult result = commentService.createComment(command);

    assertThat(result.id()).isEqualTo(1L);
    assertThat(result.content()).isEqualTo("hello");
    assertThat(result.writerId()).isEqualTo(200L);
    assertThat(result.parentId()).isNull();
    assertThat(result.isDeleted()).isFalse();

    verify(loadPostPort).existsPost(100L);
    verify(saveCommentPort).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("createComment() throws when post does not exist")
  void createComment_postMissing_throwsBusinessException() {
    CreateCommentCommand command = new CreateCommentCommand(100L, 200L, null, "hello");
    given(loadPostPort.existsPost(100L)).willReturn(false);

    assertThatThrownBy(() -> commentService.createComment(command))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.POST_NOT_FOUND.getMessage());

    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("getReplies() throws when parent comment does not exist")
  void getReplies_parentMissing_throwsCommentNotFoundException() {
    GetRepliesQuery query = new GetRepliesQuery(10L, PageRequest.of(0, 20));
    given(loadCommentPort.loadComment(10L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> commentService.getReplies(query))
        .isInstanceOf(CommentNotFoundException.class)
        .hasMessage(ErrorCode.COMMENT_NOT_FOUND.getMessage());

    verify(loadCommentPort, never())
        .loadReplies(any(Long.class), any(org.springframework.data.domain.Pageable.class));
  }

  @Test
  @DisplayName("deleteCommentsByPostId() delegates to delete port")
  void deleteCommentsByPostId_delegatesToDeletePort() {
    commentService.deleteCommentsByPostId(33L);

    verify(deleteCommentPort).deleteAllByPostId(33L);
  }
}
