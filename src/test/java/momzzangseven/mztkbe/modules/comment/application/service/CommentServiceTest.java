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
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.CursorScope;
import momzzangseven.mztkbe.modules.comment.application.dto.CommentMutationResult;
import momzzangseven.mztkbe.modules.comment.application.dto.CommentResult;
import momzzangseven.mztkbe.modules.comment.application.dto.CreateCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.dto.DeleteAnswerCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.dto.DeleteCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.dto.GetCommentsCursorResult;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRepliesCursorQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRepliesQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRootCommentsCursorQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRootCommentsQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.UpdateAnswerCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.port.out.DeleteCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.GrantCommentXpPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentWriterPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.SaveCommentPort;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import momzzangseven.mztkbe.modules.comment.domain.model.CommentTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
  @Mock private LoadAnswerPort loadAnswerPort;
  @Mock private DeleteCommentPort deleteCommentPort;
  @Mock private GrantCommentXpPort grantCommentXpPort;
  @Mock private LoadCommentWriterPort loadCommentWriterPort;

  @InjectMocks private CommentService commentService;

  @Test
  @DisplayName("createComment() creates root comment when post exists")
  void createComment_createsRootCommentWhenPostExists() {
    CreateCommentCommand command = new CreateCommentCommand(100L, 200L, null, "hello");

    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
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

    CommentMutationResult result = commentService.createComment(command);

    assertThat(result.id()).isEqualTo(1L);
    assertThat(result.content()).isEqualTo("hello");
    assertThat(result.writerId()).isEqualTo(200L);
    assertThat(result.parentId()).isNull();
    assertThat(result.isDeleted()).isFalse();

    verify(loadPostPort).loadPostVisibilityContext(100L);
    verify(saveCommentPort).saveComment(any(Comment.class));
    verify(grantCommentXpPort).grantCreateCommentXp(200L, 1L);
  }

  @Test
  @DisplayName("createComment() creates answer comment using answerId target")
  void createComment_answerTarget_createsCommentWithAnswerId() {
    CreateCommentCommand command =
        CreateCommentCommand.forAnswer(300L, 200L, null, "answer comment");

    given(loadAnswerPort.loadAnswerCommentContext(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
    given(saveCommentPort.saveComment(any(Comment.class)))
        .willAnswer(
            invocation -> {
              Comment input = invocation.getArgument(0);
              return Comment.builder()
                  .id(3L)
                  .targetType(input.getTargetType())
                  .answerId(input.getAnswerId())
                  .writerId(input.getWriterId())
                  .parentId(input.getParentId())
                  .content(input.getContent())
                  .isDeleted(input.isDeleted())
                  .createdAt(input.getCreatedAt())
                  .updatedAt(input.getUpdatedAt())
                  .build();
            });

    CommentMutationResult result = commentService.createComment(command);

    ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
    verify(saveCommentPort).saveComment(captor.capture());
    assertThat(captor.getValue().getTargetType()).isEqualTo(CommentTargetType.ANSWER);
    assertThat(captor.getValue().getAnswerId()).isEqualTo(300L);
    assertThat(captor.getValue().getPostId()).isNull();
    assertThat(result.id()).isEqualTo(3L);
  }

  @Test
  @DisplayName("createComment() throws when post does not exist")
  void createComment_postMissing_throwsBusinessException() {
    CreateCommentCommand command = new CreateCommentCommand(100L, 200L, null, "hello");
    given(loadPostPort.loadPostVisibilityContext(100L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> commentService.createComment(command))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.POST_NOT_FOUND.getMessage());

    verify(saveCommentPort, never()).saveComment(any(Comment.class));
    verifyNoInteractions(grantCommentXpPort);
  }

  @Test
  @DisplayName("createComment() throws when post is not publicly writable")
  void createComment_nonPublicPost_throwsBusinessException() {
    CreateCommentCommand command = new CreateCommentCommand(100L, 200L, null, "hello");
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(hiddenPostContext(100L, 200L)));

    assertThatThrownBy(() -> commentService.createComment(command))
        .isInstanceOf(BusinessException.class);

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
    given(loadPostPort.loadPostVisibilityContext(100L)).willReturn(Optional.empty());

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

    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
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

    CommentMutationResult result = commentService.createComment(command);

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

    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
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

    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
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

    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
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

    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
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

    CommentMutationResult result = commentService.createComment(command);

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
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
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
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
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
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
    given(loadCommentPort.loadReplies(10L, pageable))
        .willReturn(new PageImpl<>(List.of(reply), pageable, 2));
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
    verify(loadCommentPort, never()).countDirectRepliesByParentIds(any());
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
    given(loadPostPort.loadPostVisibilityContext(100L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> commentService.getReplies(new GetRepliesQuery(10L, pageable)))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.POST_NOT_FOUND.getMessage());

    verify(loadCommentPort, never()).loadReplies(any(Long.class), any(Pageable.class));
  }

  @Test
  @DisplayName("getReplies() throws when parent comment is already a reply")
  void getReplies_nestedReplyParent_throwsBusinessException() {
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
    Pageable pageable = PageRequest.of(0, 10);
    given(loadCommentPort.loadComment(10L)).willReturn(Optional.of(replyParent));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));

    assertThatThrownBy(() -> commentService.getReplies(new GetRepliesQuery(10L, pageable)))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.COMMENT_DEPTH_EXCEEDED.getMessage());

    verify(loadCommentPort, never()).loadReplies(any(Long.class), any(Pageable.class));
  }

  @Test
  @DisplayName("getRootCommentsByCursor trims probe row and builds next cursor")
  void getRootCommentsByCursor_trimsProbeAndBuildsNextCursor() {
    LocalDateTime firstTime = LocalDateTime.of(2026, 4, 24, 10, 0);
    LocalDateTime secondTime = LocalDateTime.of(2026, 4, 24, 11, 0);
    LocalDateTime probeTime = LocalDateTime.of(2026, 4, 24, 12, 0);
    Comment first = comment(11L, 100L, 201L, null, "first", false, firstTime);
    Comment second = comment(12L, 100L, 202L, null, "second", false, secondTime);
    Comment probe = comment(13L, 100L, 203L, null, "probe", false, probeTime);
    CursorPageRequest pageRequest =
        CursorPageRequest.of(null, 2, 20, 50, CursorScope.rootComments(100L));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
    given(loadCommentPort.loadRootCommentsByCursor(100L, pageRequest))
        .willReturn(List.of(first, second, probe));
    given(loadCommentPort.countDirectRepliesByParentIds(List.of(11L, 12L)))
        .willReturn(Map.of(11L, 1L, 12L, 2L));
    given(loadCommentWriterPort.loadWritersByIds(java.util.Set.of(201L, 202L)))
        .willReturn(Map.of());

    GetCommentsCursorResult result =
        commentService.getRootCommentsByCursor(new GetRootCommentsCursorQuery(100L, pageRequest));

    assertThat(result.hasNext()).isTrue();
    assertThat(result.comments()).extracting("id").containsExactly(11L, 12L);
    assertThat(result.comments()).extracting("replyCount").containsExactly(1L, 2L);
    assertThat(CursorCodec.decode(result.nextCursor(), pageRequest.scope()).id()).isEqualTo(12L);
    verify(loadCommentPort).countDirectRepliesByParentIds(List.of(11L, 12L));
  }

  @Test
  @DisplayName("getRepliesByCursor does not count nested replies")
  void getRepliesByCursor_doesNotCountNestedReplies() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment parent = comment(10L, 100L, 99L, null, "parent", false, now);
    Comment firstReply = comment(21L, 100L, 201L, 10L, "reply", false, now.plusMinutes(1));
    Comment probe = comment(22L, 100L, 202L, 10L, "probe", false, now.plusMinutes(2));
    CursorPageRequest pageRequest = CursorPageRequest.of(null, 1, 10, 50, CursorScope.replies(10L));
    given(loadCommentPort.loadComment(10L)).willReturn(Optional.of(parent));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
    given(loadCommentPort.loadRepliesByCursor(10L, pageRequest))
        .willReturn(List.of(firstReply, probe));
    given(loadCommentWriterPort.loadWritersByIds(java.util.Set.of(201L))).willReturn(Map.of());

    GetCommentsCursorResult result =
        commentService.getRepliesByCursor(new GetRepliesCursorQuery(10L, pageRequest));

    assertThat(result.hasNext()).isTrue();
    assertThat(result.comments()).extracting("id").containsExactly(21L);
    assertThat(result.comments().getFirst().replyCount()).isZero();
    assertThat(CursorCodec.decode(result.nextCursor(), pageRequest.scope()).id()).isEqualTo(21L);
    verify(loadCommentPort, never()).countDirectRepliesByParentIds(any());
  }

  @Test
  @DisplayName("deleteCommentsByPostId() delegates to delete port")
  void deleteCommentsByPostId_delegatesToDeletePort() {
    commentService.deleteCommentsByPostId(33L);

    verify(deleteCommentPort).deleteAllByPostId(33L);
  }

  @Test
  @DisplayName("deleteCommentsByAnswerId() delegates to delete port")
  void deleteCommentsByAnswerId_delegatesToDeletePort() {
    commentService.deleteCommentsByAnswerId(44L);

    verify(deleteCommentPort).deleteAllByAnswerId(44L);
  }

  @Test
  @DisplayName("deleteComment() soft-deletes writer comment when parent post is writable")
  void deleteComment_visibleParentPost_deletesWriterComment() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = comment(31L, 100L, 200L, null, "comment", false, now);
    given(loadCommentPort.loadComment(31L)).willReturn(Optional.of(comment));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));

    commentService.deleteComment(new DeleteCommentCommand(31L, 200L));

    verify(loadPostPort).loadPostVisibilityContext(100L);
    verify(saveCommentPort).saveComment(org.mockito.ArgumentMatchers.argThat(Comment::isDeleted));
  }

  @Test
  @DisplayName("updateAnswerComment() updates writer answer comment when answerId matches")
  void updateAnswerComment_matchingAnswer_updatesContent() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(41L, 300L, 200L, null, "before", false, now);
    given(loadCommentPort.loadComment(41L)).willReturn(Optional.of(comment));
    given(loadAnswerPort.loadAnswerCommentContext(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
    given(saveCommentPort.saveComment(any(Comment.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    CommentMutationResult result =
        commentService.updateAnswerComment(
            new UpdateAnswerCommentCommand(300L, 41L, 200L, "after"));

    assertThat(result.content()).isEqualTo("after");
    verify(saveCommentPort)
        .saveComment(
            org.mockito.ArgumentMatchers.argThat(saved -> "after".equals(saved.getContent())));
  }

  @Test
  @DisplayName("updateAnswerComment() throws when answerId does not match")
  void updateAnswerComment_answerMismatch_throwsException() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(42L, 300L, 200L, null, "before", false, now);
    given(loadCommentPort.loadComment(42L)).willReturn(Optional.of(comment));

    assertThatThrownBy(
            () ->
                commentService.updateAnswerComment(
                    new UpdateAnswerCommentCommand(301L, 42L, 200L, "after")))
        .isInstanceOf(CommentPostMismatchException.class);

    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("updateAnswerComment() throws when target comment is post comment")
  void updateAnswerComment_postComment_throwsException() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = comment(43L, 100L, 200L, null, "before", false, now);
    given(loadCommentPort.loadComment(43L)).willReturn(Optional.of(comment));

    assertThatThrownBy(
            () ->
                commentService.updateAnswerComment(
                    new UpdateAnswerCommentCommand(300L, 43L, 200L, "after")))
        .isInstanceOf(CommentPostMismatchException.class);

    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("deleteAnswerComment() soft-deletes writer answer comment when answerId matches")
  void deleteAnswerComment_matchingAnswer_deletesComment() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(44L, 300L, 200L, null, "comment", false, now);
    given(loadCommentPort.loadComment(44L)).willReturn(Optional.of(comment));
    given(loadAnswerPort.loadAnswerCommentContext(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));

    commentService.deleteAnswerComment(new DeleteAnswerCommentCommand(300L, 44L, 200L));

    verify(saveCommentPort).saveComment(org.mockito.ArgumentMatchers.argThat(Comment::isDeleted));
  }

  @Test
  @DisplayName("deleteAnswerComment() throws when answerId does not match")
  void deleteAnswerComment_answerMismatch_throwsException() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(45L, 300L, 200L, null, "comment", false, now);
    given(loadCommentPort.loadComment(45L)).willReturn(Optional.of(comment));

    assertThatThrownBy(
            () ->
                commentService.deleteAnswerComment(new DeleteAnswerCommentCommand(301L, 45L, 200L)))
        .isInstanceOf(CommentPostMismatchException.class);

    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("deleteAnswerComment() throws when target comment is post comment")
  void deleteAnswerComment_postComment_throwsException() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = comment(46L, 100L, 200L, null, "comment", false, now);
    given(loadCommentPort.loadComment(46L)).willReturn(Optional.of(comment));

    assertThatThrownBy(
            () ->
                commentService.deleteAnswerComment(new DeleteAnswerCommentCommand(300L, 46L, 200L)))
        .isInstanceOf(CommentPostMismatchException.class);

    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("deleteComment() throws when parent post is not publicly writable")
  void deleteComment_hiddenParentPost_throwsBusinessException() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = comment(32L, 100L, 200L, null, "comment", false, now);
    given(loadCommentPort.loadComment(32L)).willReturn(Optional.of(comment));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(hiddenPostContext(100L, 200L)));

    assertThatThrownBy(() -> commentService.deleteComment(new DeleteCommentCommand(32L, 200L)))
        .isInstanceOf(BusinessException.class);

    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  private Comment comment(
      Long id,
      Long postId,
      Long writerId,
      Long parentId,
      String content,
      boolean isDeleted,
      LocalDateTime createdAt) {
    return Comment.builder()
        .id(id)
        .postId(postId)
        .writerId(writerId)
        .parentId(parentId)
        .content(content)
        .isDeleted(isDeleted)
        .createdAt(createdAt)
        .updatedAt(createdAt)
        .build();
  }

  private Comment answerComment(
      Long id,
      Long answerId,
      Long writerId,
      Long parentId,
      String content,
      boolean isDeleted,
      LocalDateTime createdAt) {
    return Comment.builder()
        .id(id)
        .targetType(CommentTargetType.ANSWER)
        .answerId(answerId)
        .writerId(writerId)
        .parentId(parentId)
        .content(content)
        .isDeleted(isDeleted)
        .createdAt(createdAt)
        .updatedAt(createdAt)
        .build();
  }

  private LoadPostPort.PostVisibilityContext visiblePostContext(Long postId) {
    return new LoadPostPort.PostVisibilityContext(postId, 1L, true);
  }

  private LoadPostPort.PostVisibilityContext hiddenPostContext(Long postId, Long writerId) {
    return new LoadPostPort.PostVisibilityContext(postId, writerId, false);
  }
}
