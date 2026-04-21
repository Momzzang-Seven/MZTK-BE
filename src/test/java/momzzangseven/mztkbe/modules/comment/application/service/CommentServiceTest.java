package momzzangseven.mztkbe.modules.comment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.comment.CommentNotFoundException;
import momzzangseven.mztkbe.global.error.comment.CommentPostMismatchException;
import momzzangseven.mztkbe.modules.comment.application.dto.CommentResult;
import momzzangseven.mztkbe.modules.comment.application.dto.CreateCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRepliesQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRootCommentsQuery;
import momzzangseven.mztkbe.modules.comment.application.port.out.DeleteCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.GrantCommentXpPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentWriterPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.SaveCommentPort;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentService unit test")
class CommentServiceTest {

  @Mock private LoadCommentPort loadCommentPort;
  @Mock private SaveCommentPort saveCommentPort;
  @Mock private LoadPostPort loadPostPort;
  @Mock private DeleteCommentPort deleteCommentPort;
  @Mock private GrantCommentXpPort grantCommentXpPort;
  @Mock private LoadCommentWriterPort loadCommentWriterPort;

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
    verify(grantCommentXpPort).grantCreateCommentXp(200L, 1L);
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
    verifyNoInteractions(grantCommentXpPort);
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
  @DisplayName("getRootComments() throws when post does not exist")
  void getRootComments_postMissing_throwsBusinessException() {
    Pageable pageable = PageRequest.of(0, 20);
    GetRootCommentsQuery query = new GetRootCommentsQuery(100L, pageable);
    given(loadPostPort.existsPost(100L)).willReturn(false);

    assertThatThrownBy(() -> commentService.getRootComments(query))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.POST_NOT_FOUND.getMessage());

    verify(loadCommentPort, never()).loadRootComments(any(Long.class), any(Pageable.class));
  }

  @Test
  @DisplayName("createComment() creates reply when valid parent exists in same post")
  void createComment_withValidParentId_createsReply() {
    LocalDateTime now = LocalDateTime.now();
    Comment parentComment =
        Comment.builder()
            .id(10L)
            .postId(100L)
            .writerId(99L)
            .content("parent")
            .isDeleted(false)
            .createdAt(now)
            .updatedAt(now)
            .build();
    CreateCommentCommand command = new CreateCommentCommand(100L, 200L, 10L, "reply content");

    given(loadPostPort.existsPost(100L)).willReturn(true);
    given(loadCommentPort.loadComment(10L)).willReturn(Optional.of(parentComment));
    given(saveCommentPort.saveComment(any(Comment.class)))
        .willAnswer(
            invocation -> {
              Comment input = invocation.getArgument(0);
              return Comment.builder()
                  .id(2L)
                  .postId(input.getPostId())
                  .writerId(input.getWriterId())
                  .parentId(input.getParentId())
                  .content(input.getContent())
                  .isDeleted(false)
                  .createdAt(now)
                  .updatedAt(now)
                  .build();
            });

    CommentResult result = commentService.createComment(command);

    assertThat(result.parentId()).isEqualTo(10L);
    assertThat(result.content()).isEqualTo("reply content");
    verify(loadCommentPort).loadComment(10L);
    verify(grantCommentXpPort).grantCreateCommentXp(200L, 2L);
  }

  @Test
  @DisplayName("createComment() throws when parent comment belongs to different post")
  void createComment_parentPostMismatch_throwsException() {
    LocalDateTime now = LocalDateTime.now();
    Comment parentComment =
        Comment.builder()
            .id(10L)
            .postId(999L) // 다른 게시글
            .writerId(99L)
            .content("parent")
            .isDeleted(false)
            .createdAt(now)
            .updatedAt(now)
            .build();
    CreateCommentCommand command = new CreateCommentCommand(100L, 200L, 10L, "reply");

    given(loadPostPort.existsPost(100L)).willReturn(true);
    given(loadCommentPort.loadComment(10L)).willReturn(Optional.of(parentComment));

    assertThatThrownBy(() -> commentService.createComment(command))
        .isInstanceOf(CommentPostMismatchException.class);
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
    verifyNoInteractions(grantCommentXpPort);
  }

  @Test
  @DisplayName("createComment() throws when parent comment is deleted")
  void createComment_deletedParent_throwsException() {
    LocalDateTime now = LocalDateTime.now();
    Comment deletedParent =
        Comment.builder()
            .id(10L)
            .postId(100L)
            .writerId(99L)
            .content("삭제된 댓글입니다.")
            .isDeleted(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
    CreateCommentCommand command = new CreateCommentCommand(100L, 200L, 10L, "reply");

    given(loadPostPort.existsPost(100L)).willReturn(true);
    given(loadCommentPort.loadComment(10L)).willReturn(Optional.of(deletedParent));

    assertThatThrownBy(() -> commentService.createComment(command))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.CANNOT_UPDATE_DELETED_COMMENT.getMessage());
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
    verifyNoInteractions(grantCommentXpPort);
  }

  @Test
  @DisplayName("createComment() throws when parent comment is already a reply")
  void createComment_nestedReplyParent_throwsBusinessException() {
    LocalDateTime now = LocalDateTime.now();
    Comment replyParent =
        Comment.builder()
            .id(10L)
            .postId(100L)
            .writerId(99L)
            .parentId(5L)
            .content("reply parent")
            .isDeleted(false)
            .createdAt(now)
            .updatedAt(now)
            .build();
    CreateCommentCommand command = new CreateCommentCommand(100L, 200L, 10L, "nested reply");

    given(loadPostPort.existsPost(100L)).willReturn(true);
    given(loadCommentPort.loadComment(10L)).willReturn(Optional.of(replyParent));

    assertThatThrownBy(() -> commentService.createComment(command))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.COMMENT_DEPTH_EXCEEDED.getMessage());
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
    verifyNoInteractions(grantCommentXpPort);
  }

  @Test
  @DisplayName("createComment() keeps comment creation successful even when XP grant fails")
  void createComment_xpGrantFails_returnsSavedComment() {
    CreateCommentCommand command = new CreateCommentCommand(100L, 200L, null, "hello");

    given(loadPostPort.existsPost(100L)).willReturn(true);
    given(saveCommentPort.saveComment(any(Comment.class)))
        .willAnswer(
            invocation -> {
              Comment input = invocation.getArgument(0);
              return Comment.builder()
                  .id(3L)
                  .postId(input.getPostId())
                  .writerId(input.getWriterId())
                  .parentId(input.getParentId())
                  .content(input.getContent())
                  .isDeleted(input.isDeleted())
                  .createdAt(input.getCreatedAt())
                  .updatedAt(input.getUpdatedAt())
                  .build();
            });
    given(grantCommentXpPort.grantCreateCommentXp(200L, 3L))
        .willThrow(new IllegalStateException("xp system down"));

    CommentResult result = commentService.createComment(command);

    assertThat(result.id()).isEqualTo(3L);
    assertThat(result.content()).isEqualTo("hello");
    verify(saveCommentPort).saveComment(any(Comment.class));
    verify(grantCommentXpPort).grantCreateCommentXp(200L, 3L);
  }

  @Test
  @DisplayName("getReplies() returns paged replies when parent exists")
  void getReplies_parentExists_returnsPaged() {
    LocalDateTime now = LocalDateTime.now();
    Comment parent =
        Comment.builder()
            .id(10L)
            .postId(100L)
            .writerId(99L)
            .content("parent")
            .isDeleted(false)
            .createdAt(now)
            .updatedAt(now)
            .build();
    Pageable pageable = PageRequest.of(0, 10);
    given(loadCommentPort.loadComment(10L)).willReturn(Optional.of(parent));
    given(loadPostPort.existsPost(100L)).willReturn(true);
    given(loadCommentPort.loadReplies(10L, pageable)).willReturn(Page.empty(pageable));

    Page<CommentResult> result = commentService.getReplies(new GetRepliesQuery(10L, pageable));

    assertThat(result).isNotNull();
    verify(loadCommentPort).loadReplies(10L, pageable);
  }

  @Test
  @DisplayName("getRootComments() enriches comments with writer summaries and direct reply counts")
  void getRootComments_enrichesWriterAndReplyCount() {
    LocalDateTime now = LocalDateTime.now();
    Comment root =
        Comment.builder()
            .id(11L)
            .postId(100L)
            .writerId(201L)
            .content("root")
            .isDeleted(false)
            .createdAt(now)
            .updatedAt(now)
            .build();
    Pageable pageable = PageRequest.of(0, 20);
    given(loadPostPort.existsPost(100L)).willReturn(true);
    given(loadCommentPort.loadRootComments(100L, pageable))
        .willReturn(new PageImpl<>(List.of(root), pageable, 1));
    given(loadCommentPort.countDirectRepliesByParentIds(List.of(11L))).willReturn(Map.of(11L, 2L));
    given(loadCommentWriterPort.loadWritersByIds(java.util.Set.of(201L)))
        .willReturn(
            Map.of(201L, new LoadCommentWriterPort.WriterSummary(201L, "writer", "profile.png")));

    Page<CommentResult> result =
        commentService.getRootComments(new GetRootCommentsQuery(100L, pageable));

    CommentResult first = result.getContent().getFirst();
    assertThat(first.id()).isEqualTo(11L);
    assertThat(first.replyCount()).isEqualTo(2L);
    assertThat(first.writerId()).isEqualTo(201L);
    assertThat(first.writerNickname()).isEqualTo("writer");
    assertThat(first.writerProfileImageUrl()).isEqualTo("profile.png");
  }

  @Test
  @DisplayName("getReplies() enriches replies with writer summaries and keeps Page last metadata")
  void getReplies_enrichesWriterAndKeepsPageMetadata() {
    LocalDateTime now = LocalDateTime.now();
    Comment parent =
        Comment.builder()
            .id(10L)
            .postId(100L)
            .writerId(99L)
            .content("parent")
            .isDeleted(false)
            .createdAt(now)
            .updatedAt(now)
            .build();
    Comment reply =
        Comment.builder()
            .id(12L)
            .postId(100L)
            .writerId(202L)
            .parentId(10L)
            .content("reply")
            .isDeleted(false)
            .createdAt(now)
            .updatedAt(now)
            .build();
    Pageable pageable = PageRequest.of(0, 1);
    given(loadCommentPort.loadComment(10L)).willReturn(Optional.of(parent));
    given(loadPostPort.existsPost(100L)).willReturn(true);
    given(loadCommentPort.loadReplies(10L, pageable))
        .willReturn(new PageImpl<>(List.of(reply), pageable, 2));
    given(loadCommentPort.countDirectRepliesByParentIds(List.of(12L))).willReturn(Map.of());
    given(loadCommentWriterPort.loadWritersByIds(java.util.Set.of(202L)))
        .willReturn(
            Map.of(202L, new LoadCommentWriterPort.WriterSummary(202L, "reply-writer", "p.webp")));

    Page<CommentResult> result = commentService.getReplies(new GetRepliesQuery(10L, pageable));

    assertThat(result.isLast()).isFalse();
    CommentResult first = result.getContent().getFirst();
    assertThat(first.writerId()).isEqualTo(202L);
    assertThat(first.writerNickname()).isEqualTo("reply-writer");
    assertThat(first.writerProfileImageUrl()).isEqualTo("p.webp");
    assertThat(first.replyCount()).isZero();
  }

  @Test
  @DisplayName("getReplies() throws when parent comment's post does not exist")
  void getReplies_parentPostMissing_throwsBusinessException() {
    LocalDateTime now = LocalDateTime.now();
    Comment parent =
        Comment.builder()
            .id(10L)
            .postId(100L)
            .writerId(99L)
            .content("parent")
            .isDeleted(false)
            .createdAt(now)
            .updatedAt(now)
            .build();
    Pageable pageable = PageRequest.of(0, 10);
    given(loadCommentPort.loadComment(10L)).willReturn(Optional.of(parent));
    given(loadPostPort.existsPost(100L)).willReturn(false);

    assertThatThrownBy(() -> commentService.getReplies(new GetRepliesQuery(10L, pageable)))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.POST_NOT_FOUND.getMessage());

    verify(loadCommentPort, never()).loadReplies(any(Long.class), any(Pageable.class));
  }

  @Test
  @DisplayName("deleteCommentsByPostId() delegates to delete port")
  void deleteCommentsByPostId_delegatesToDeletePort() {
    commentService.deleteCommentsByPostId(33L);

    verify(deleteCommentPort).deleteAllByPostId(33L);
  }
}
