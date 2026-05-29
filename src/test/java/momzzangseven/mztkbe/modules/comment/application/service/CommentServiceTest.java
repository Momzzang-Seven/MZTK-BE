package momzzangseven.mztkbe.modules.comment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.answer.CannotAnswerSolvedPostException;
import momzzangseven.mztkbe.global.error.answer.CannotDeleteAnswerOnSolvedPostException;
import momzzangseven.mztkbe.global.error.answer.CannotUpdateAnswerOnSolvedPostException;
import momzzangseven.mztkbe.global.error.comment.CommentNotFoundException;
import momzzangseven.mztkbe.global.error.comment.CommentPostMismatchException;
import momzzangseven.mztkbe.global.error.comment.CommentTargetMismatchException;
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.CursorScope;
import momzzangseven.mztkbe.modules.comment.application.dto.CommentMutationResult;
import momzzangseven.mztkbe.modules.comment.application.dto.CommentResult;
import momzzangseven.mztkbe.modules.comment.application.dto.CreateCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.dto.DeleteAnswerCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.dto.DeleteCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.dto.GetAnswerRootCommentsCursorQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.GetAnswerRootCommentsQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.GetCommentsCursorResult;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRepliesCursorQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRepliesQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRootCommentsCursorQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRootCommentsQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.UpdateAnswerCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.dto.UpdateCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.port.out.DeleteCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentWriterPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.SaveCommentPort;
import momzzangseven.mztkbe.modules.comment.domain.event.CommentCreatedEvent;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import momzzangseven.mztkbe.modules.comment.domain.model.CommentTargetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
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
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private LoadCommentWriterPort loadCommentWriterPort;

  private CommentService commentService;

  @BeforeEach
  void setUp() {
    commentService =
        new CommentService(
            loadCommentPort,
            saveCommentPort,
            loadPostPort,
            loadAnswerPort,
            deleteCommentPort,
            eventPublisher,
            loadCommentWriterPort,
            ZoneId.of("Asia/Seoul"));
  }

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
    verifyCommentCreatedEventPublished(200L, 1L);
  }

  @Test
  @DisplayName(
      "createComment() on a FREE post invokes the lock-free LoadPostPort.loadPostVisibilityContext"
          + " only — MOM-459 contract")
  void createComment_freePost_usesLockFreeVisibilityLookupOnly() {
    CreateCommentCommand command = new CreateCommentCommand(100L, 200L, null, "hello");
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
    given(saveCommentPort.saveComment(any(Comment.class)))
        .willAnswer(
            invocation -> {
              Comment input = invocation.getArgument(0);
              return Comment.builder()
                  .id(11L)
                  .postId(input.getPostId())
                  .writerId(input.getWriterId())
                  .parentId(input.getParentId())
                  .content(input.getContent())
                  .isDeleted(input.isDeleted())
                  .createdAt(input.getCreatedAt())
                  .updatedAt(input.getUpdatedAt())
                  .build();
            });

    commentService.createComment(command);

    verify(loadPostPort).loadPostVisibilityContext(100L);
    verifyNoInteractions(loadAnswerPort);
  }

  @Test
  @DisplayName("createComment() creates answer comment using answerId target")
  void createComment_answerTarget_createsCommentWithAnswerId() {
    CreateCommentCommand command =
        CreateCommentCommand.forAnswer(300L, 200L, null, "answer comment");

    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L))
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
                  .postId(input.getPostId())
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
    assertThat(captor.getValue().getPostId()).isEqualTo(100L);
    assertThat(captor.getValue().getAnswerId()).isEqualTo(300L);
    assertThat(result.id()).isEqualTo(3L);
    verify(loadAnswerPort).loadAnswerCommentContextForUpdate(300L);
    verify(loadAnswerPort, never()).loadAnswerCommentContext(300L);
  }

  @Test
  @DisplayName("createComment() maps missing answer target to ANSWER_NOT_FOUND")
  void createComment_answerTargetMissing_throwsAnswerNotFound() {
    CreateCommentCommand command =
        CreateCommentCommand.forAnswer(300L, 200L, null, "answer comment");
    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> commentService.createComment(command))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.ANSWER_NOT_FOUND.getMessage());

    verify(loadAnswerPort).loadAnswerCommentContextForUpdate(300L);
    verify(loadAnswerPort, never()).loadAnswerCommentContext(300L);
    verify(loadPostPort, never()).loadPostVisibilityContext(any());
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("createComment() rejects answer comment when parent question is answer locked")
  void createComment_answerLocked_throwsCannotAnswerSolvedPost() {
    CreateCommentCommand command =
        CreateCommentCommand.forAnswer(300L, 200L, null, "answer comment");
    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L, true)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));

    assertThatThrownBy(() -> commentService.createComment(command))
        .isInstanceOf(CannotAnswerSolvedPostException.class)
        .hasMessage(ErrorCode.CANNOT_ANSWER_SOLVED_POST.getMessage());

    verify(loadAnswerPort).loadAnswerCommentContextForUpdate(300L);
    verify(loadPostPort).loadPostVisibilityContext(100L);
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("createComment() keeps POST_NOT_FOUND when answer root post is missing")
  void createComment_answerRootPostMissing_throwsPostNotFound() {
    CreateCommentCommand command =
        CreateCommentCommand.forAnswer(300L, 200L, null, "answer comment");
    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> commentService.createComment(command))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.POST_NOT_FOUND.getMessage());

    verify(loadAnswerPort).loadAnswerCommentContextForUpdate(300L);
    verify(loadPostPort).loadPostVisibilityContext(100L);
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
    verifyNoInteractions(eventPublisher);
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
    verifyNoInteractions(eventPublisher);
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
    verifyNoInteractions(eventPublisher);
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
  @DisplayName("getAnswerRootComments() maps missing answer target to ANSWER_NOT_FOUND")
  void getAnswerRootComments_answerMissing_throwsAnswerNotFound() {
    Pageable pageable = PageRequest.of(0, 20);
    given(loadAnswerPort.loadAnswerCommentContext(300L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                commentService.getAnswerRootComments(
                    new GetAnswerRootCommentsQuery(300L, null, pageable)))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.ANSWER_NOT_FOUND.getMessage());

    verify(loadAnswerPort).loadAnswerCommentContext(300L);
    verify(loadPostPort, never()).loadPostVisibilityContext(any());
    verify(loadCommentPort, never())
        .loadRootCommentsByAnswerId(any(Long.class), any(Pageable.class));
  }

  @Test
  @DisplayName("getAnswerRootComments() keeps POST_NOT_FOUND when answer root post is missing")
  void getAnswerRootComments_rootPostMissing_throwsPostNotFound() {
    Pageable pageable = PageRequest.of(0, 20);
    given(loadAnswerPort.loadAnswerCommentContext(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                commentService.getAnswerRootComments(
                    new GetAnswerRootCommentsQuery(300L, null, pageable)))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.POST_NOT_FOUND.getMessage());

    verify(loadAnswerPort).loadAnswerCommentContext(300L);
    verify(loadPostPort).loadPostVisibilityContext(100L);
    verify(loadCommentPort, never())
        .loadRootCommentsByAnswerId(any(Long.class), any(Pageable.class));
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
    verifyCommentCreatedEventPublished(200L, 2L);
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
        .isInstanceOf(CommentPostMismatchException.class)
        .hasMessage(ErrorCode.COMMENT_POST_MISMATCH.getMessage());
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("createComment() creates reply when valid parent exists in same answer target")
  void createComment_withValidAnswerParentId_createsReply() {
    LocalDateTime now = LocalDateTime.now();
    Comment parentComment = answerComment(10L, 300L, 99L, null, "parent", false, now);
    CreateCommentCommand command =
        CreateCommentCommand.forAnswer(300L, 200L, 10L, "answer reply content");

    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
    given(loadCommentPort.loadComment(10L)).willReturn(Optional.of(parentComment));
    given(saveCommentPort.saveComment(any(Comment.class)))
        .willAnswer(
            invocation -> {
              Comment input = invocation.getArgument(0);
              return Comment.builder()
                  .id(4L)
                  .targetType(input.getTargetType())
                  .postId(input.getPostId())
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
    assertThat(captor.getValue().getPostId()).isEqualTo(100L);
    assertThat(captor.getValue().getAnswerId()).isEqualTo(300L);
    assertThat(result.parentId()).isEqualTo(10L);
    assertThat(result.content()).isEqualTo("answer reply content");
    verify(loadCommentPort).loadComment(10L);
    verifyCommentCreatedEventPublished(200L, 4L);
  }

  @Test
  @DisplayName("createComment() maps missing answer reply parent to COMMENT_NOT_FOUND")
  void createComment_answerReplyParentMissing_throwsCommentNotFound() {
    CreateCommentCommand command = CreateCommentCommand.forAnswer(300L, 200L, 10L, "reply");

    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
    given(loadCommentPort.loadComment(10L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> commentService.createComment(command))
        .isInstanceOf(CommentNotFoundException.class)
        .hasMessage(ErrorCode.COMMENT_NOT_FOUND.getMessage());
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("createComment() rejects deleted answer reply parent")
  void createComment_answerReplyDeletedParent_throwsCannotUpdateDeletedComment() {
    LocalDateTime now = LocalDateTime.now();
    Comment deletedParent = answerComment(10L, 300L, 99L, null, "삭제된 댓글입니다.", true, now);
    CreateCommentCommand command = CreateCommentCommand.forAnswer(300L, 200L, 10L, "reply");

    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
    given(loadCommentPort.loadComment(10L)).willReturn(Optional.of(deletedParent));

    assertThatThrownBy(() -> commentService.createComment(command))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.CANNOT_UPDATE_DELETED_COMMENT.getMessage());
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("createComment() maps answer parent from different answer to target mismatch")
  void createComment_answerParentAnswerMismatch_throwsTargetMismatch() {
    LocalDateTime now = LocalDateTime.now();
    Comment parentComment = answerComment(10L, 301L, 99L, null, "parent", false, now);
    CreateCommentCommand command = CreateCommentCommand.forAnswer(300L, 200L, 10L, "reply");

    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
    given(loadCommentPort.loadComment(10L)).willReturn(Optional.of(parentComment));

    assertThatThrownBy(() -> commentService.createComment(command))
        .isInstanceOf(CommentTargetMismatchException.class)
        .hasMessage(ErrorCode.COMMENT_TARGET_MISMATCH.getMessage());
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("createComment() maps post parent for answer reply to target mismatch")
  void createComment_answerParentPostTargetMismatch_throwsTargetMismatch() {
    LocalDateTime now = LocalDateTime.now();
    Comment parentComment = comment(10L, 100L, 99L, null, "parent", false, now);
    CreateCommentCommand command = CreateCommentCommand.forAnswer(300L, 200L, 10L, "reply");

    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
    given(loadCommentPort.loadComment(10L)).willReturn(Optional.of(parentComment));

    assertThatThrownBy(() -> commentService.createComment(command))
        .isInstanceOf(CommentTargetMismatchException.class)
        .hasMessage(ErrorCode.COMMENT_TARGET_MISMATCH.getMessage());
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
    verifyNoInteractions(eventPublisher);
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
    verifyNoInteractions(eventPublisher);
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
    verifyNoInteractions(eventPublisher);
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
  @DisplayName(
      "getAnswerRootComments() enriches answer roots with writer summaries and reply counts")
  void getAnswerRootComments_returnsPagedAnswerRootCommentsWithWriterAndReplyCount() {
    LocalDateTime now = LocalDateTime.now();
    Comment active = answerComment(21L, 300L, 201L, null, "answer-root", false, now);
    Comment deleted = answerComment(22L, 300L, 202L, null, "삭제된 댓글입니다.", true, now);
    Pageable pageable = PageRequest.of(0, 20);

    given(loadAnswerPort.loadAnswerCommentContext(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
    given(loadCommentPort.loadRootCommentsByAnswerId(300L, pageable))
        .willReturn(new PageImpl<>(List.of(active, deleted), pageable, 2));
    given(loadCommentPort.countDirectRepliesByParentIds(List.of(21L, 22L)))
        .willReturn(Map.of(21L, 2L, 22L, 1L));
    given(loadCommentWriterPort.loadWritersByIds(java.util.Set.of(201L)))
        .willReturn(
            Map.of(
                201L,
                new LoadCommentWriterPort.WriterSummary(201L, "answer-writer", "answer.png")));

    Page<CommentResult> result =
        commentService.getAnswerRootComments(new GetAnswerRootCommentsQuery(300L, null, pageable));

    assertThat(result.getContent()).hasSize(2);
    CommentResult first = result.getContent().get(0);
    CommentResult second = result.getContent().get(1);
    assertThat(first.id()).isEqualTo(21L);
    assertThat(first.writerId()).isEqualTo(201L);
    assertThat(first.writerNickname()).isEqualTo("answer-writer");
    assertThat(first.writerProfileImageUrl()).isEqualTo("answer.png");
    assertThat(first.replyCount()).isEqualTo(2L);
    assertThat(second.id()).isEqualTo(22L);
    assertThat(second.isDeleted()).isTrue();
    assertThat(second.writerNickname()).isNull();
    assertThat(second.replyCount()).isEqualTo(1L);
    verify(loadCommentPort).loadRootCommentsByAnswerId(300L, pageable);
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
  @DisplayName("getAnswerRootCommentsByCursor() maps missing answer target to ANSWER_NOT_FOUND")
  void getAnswerRootCommentsByCursor_answerMissing_throwsAnswerNotFound() {
    CursorPageRequest pageRequest =
        CursorPageRequest.of(null, 2, 20, 50, CursorScope.answerRootComments(300L));
    given(loadAnswerPort.loadAnswerCommentContext(300L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                commentService.getAnswerRootCommentsByCursor(
                    new GetAnswerRootCommentsCursorQuery(300L, null, pageRequest)))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.ANSWER_NOT_FOUND.getMessage());

    verify(loadAnswerPort).loadAnswerCommentContext(300L);
    verify(loadPostPort, never()).loadPostVisibilityContext(any());
    verify(loadCommentPort, never())
        .loadRootCommentsByAnswerIdCursor(any(Long.class), any(CursorPageRequest.class));
  }

  @Test
  @DisplayName("getAnswerRootCommentsByCursor trims probe row and builds next cursor")
  void getAnswerRootCommentsByCursor_trimsProbeAndBuildsNextCursor() {
    LocalDateTime firstTime = LocalDateTime.of(2026, 4, 24, 10, 0);
    LocalDateTime secondTime = LocalDateTime.of(2026, 4, 24, 11, 0);
    LocalDateTime probeTime = LocalDateTime.of(2026, 4, 24, 12, 0);
    Comment first = answerComment(31L, 300L, 201L, null, "first", false, firstTime);
    Comment second = answerComment(32L, 300L, 202L, null, "second", false, secondTime);
    Comment probe = answerComment(33L, 300L, 203L, null, "probe", false, probeTime);
    CursorPageRequest pageRequest =
        CursorPageRequest.of(null, 2, 20, 50, CursorScope.answerRootComments(300L));

    given(loadAnswerPort.loadAnswerCommentContext(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
    given(loadCommentPort.loadRootCommentsByAnswerIdCursor(300L, pageRequest))
        .willReturn(List.of(first, second, probe));
    given(loadCommentPort.countDirectRepliesByParentIds(List.of(31L, 32L)))
        .willReturn(Map.of(31L, 1L, 32L, 2L));
    given(loadCommentWriterPort.loadWritersByIds(java.util.Set.of(201L, 202L)))
        .willReturn(
            Map.of(
                201L,
                new LoadCommentWriterPort.WriterSummary(201L, "first-writer", "first.png"),
                202L,
                new LoadCommentWriterPort.WriterSummary(202L, "second-writer", "second.png")));

    GetCommentsCursorResult result =
        commentService.getAnswerRootCommentsByCursor(
            new GetAnswerRootCommentsCursorQuery(300L, null, pageRequest));

    assertThat(result.hasNext()).isTrue();
    assertThat(result.comments()).extracting("id").containsExactly(31L, 32L);
    assertThat(result.comments()).extracting("replyCount").containsExactly(1L, 2L);
    assertThat(result.comments())
        .extracting("writerNickname")
        .containsExactly("first-writer", "second-writer");
    assertThat(CursorCodec.decode(result.nextCursor(), pageRequest.scope()).id()).isEqualTo(32L);
    verify(loadCommentPort).loadRootCommentsByAnswerIdCursor(300L, pageRequest);
  }

  @Test
  @DisplayName(
      "getAnswerRootCommentsByCursor keeps POST_NOT_FOUND when answer root post is missing")
  void getAnswerRootCommentsByCursor_rootPostMissing_throwsPostNotFound() {
    CursorPageRequest pageRequest =
        CursorPageRequest.of(null, 2, 20, 50, CursorScope.answerRootComments(300L));
    given(loadAnswerPort.loadAnswerCommentContext(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                commentService.getAnswerRootCommentsByCursor(
                    new GetAnswerRootCommentsCursorQuery(300L, null, pageRequest)))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.POST_NOT_FOUND.getMessage());

    verify(loadAnswerPort).loadAnswerCommentContext(300L);
    verify(loadPostPort).loadPostVisibilityContext(100L);
    verify(loadCommentPort, never())
        .loadRootCommentsByAnswerIdCursor(any(Long.class), any(CursorPageRequest.class));
    verify(loadCommentPort, never()).countDirectRepliesByParentIds(any());
    verify(loadCommentWriterPort, never()).loadWritersByIds(any());
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
  @DisplayName("softDeleteAllCommentsByRootPostId() delegates to delete port")
  void softDeleteAllCommentsByRootPostId_delegatesToDeletePort() {
    commentService.softDeleteAllCommentsByRootPostId(33L);

    verify(deleteCommentPort).softDeleteAllByRootPostId(33L);
  }

  @Test
  @DisplayName("deleteCommentsByAnswerId() delegates to delete port")
  void deleteCommentsByAnswerId_delegatesToDeletePort() {
    commentService.deleteCommentsByAnswerId(44L);

    verify(deleteCommentPort).deleteAllByAnswerId(44L);
  }

  @Test
  @DisplayName("updateComment() updates writer post comment with locking comment load")
  void updateComment_postComment_usesLockingCommentLoad() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = comment(30L, 100L, 200L, null, "before", false, now);
    given(loadCommentPort.loadCommentForUpdate(30L)).willReturn(Optional.of(comment));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
    given(saveCommentPort.saveComment(any(Comment.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    CommentMutationResult result =
        commentService.updateComment(new UpdateCommentCommand(30L, 200L, "after"));

    assertThat(result.content()).isEqualTo("after");
    verify(loadCommentPort).loadCommentForUpdate(30L);
    verify(loadCommentPort, never()).loadComment(30L);
    verify(saveCommentPort)
        .saveComment(
            org.mockito.ArgumentMatchers.argThat(saved -> "after".equals(saved.getContent())));
  }

  @Test
  @DisplayName("deleteComment() soft-deletes writer comment when parent post is writable")
  void deleteComment_visibleParentPost_deletesWriterComment() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = comment(31L, 100L, 200L, null, "comment", false, now);
    given(loadCommentPort.loadCommentForUpdate(31L)).willReturn(Optional.of(comment));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));

    commentService.deleteComment(new DeleteCommentCommand(31L, 200L));

    verify(loadCommentPort).loadCommentForUpdate(31L);
    verify(loadCommentPort, never()).loadComment(31L);
    verify(loadPostPort).loadPostVisibilityContext(100L);
    verify(saveCommentPort).saveComment(org.mockito.ArgumentMatchers.argThat(Comment::isDeleted));
  }

  @Test
  @DisplayName(
      "updateComment() allows answer comment through common API using locking answer context")
  void updateComment_commonApiAnswerComment_usesLockingAnswerContext() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(33L, 300L, 200L, null, "before", false, now);
    given(loadCommentPort.loadCommentForUpdate(33L)).willReturn(Optional.of(comment));
    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
    given(saveCommentPort.saveComment(any(Comment.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    CommentMutationResult result =
        commentService.updateComment(new UpdateCommentCommand(33L, 200L, "after"));

    assertThat(result.content()).isEqualTo("after");
    verify(loadCommentPort).loadCommentForUpdate(33L);
    verify(loadCommentPort, never()).loadComment(33L);
    verify(loadAnswerPort).loadAnswerCommentContextForUpdate(300L);
    verify(loadAnswerPort, never()).loadAnswerCommentContext(300L);
    verify(saveCommentPort)
        .saveComment(
            org.mockito.ArgumentMatchers.argThat(saved -> "after".equals(saved.getContent())));
  }

  @Test
  @DisplayName("updateComment() maps missing answer target to ANSWER_NOT_FOUND for answer comment")
  void updateComment_commonApiAnswerCommentMissingAnswer_throwsAnswerNotFound() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(35L, 300L, 200L, null, "before", false, now);
    given(loadCommentPort.loadCommentForUpdate(35L)).willReturn(Optional.of(comment));
    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () -> commentService.updateComment(new UpdateCommentCommand(35L, 200L, "after")))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.ANSWER_NOT_FOUND.getMessage());

    verify(loadCommentPort).loadCommentForUpdate(35L);
    verify(loadCommentPort, never()).loadComment(35L);
    verify(loadAnswerPort).loadAnswerCommentContextForUpdate(300L);
    verify(loadAnswerPort, never()).loadAnswerCommentContext(300L);
    verify(loadPostPort, never()).loadPostVisibilityContext(any());
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName(
      "deleteComment() allows answer comment through common API using locking answer context")
  void deleteComment_commonApiAnswerComment_usesLockingAnswerContext() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(34L, 300L, 200L, null, "comment", false, now);
    given(loadCommentPort.loadCommentForUpdate(34L)).willReturn(Optional.of(comment));
    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));

    commentService.deleteComment(new DeleteCommentCommand(34L, 200L));

    verify(loadCommentPort).loadCommentForUpdate(34L);
    verify(loadCommentPort, never()).loadComment(34L);
    verify(loadAnswerPort).loadAnswerCommentContextForUpdate(300L);
    verify(loadAnswerPort, never()).loadAnswerCommentContext(300L);
    verify(saveCommentPort).saveComment(org.mockito.ArgumentMatchers.argThat(Comment::isDeleted));
  }

  @Test
  @DisplayName("deleteComment() maps missing answer target to ANSWER_NOT_FOUND for answer comment")
  void deleteComment_commonApiAnswerCommentMissingAnswer_throwsAnswerNotFound() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(36L, 300L, 200L, null, "comment", false, now);
    given(loadCommentPort.loadCommentForUpdate(36L)).willReturn(Optional.of(comment));
    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> commentService.deleteComment(new DeleteCommentCommand(36L, 200L)))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.ANSWER_NOT_FOUND.getMessage());

    verify(loadCommentPort).loadCommentForUpdate(36L);
    verify(loadCommentPort, never()).loadComment(36L);
    verify(loadAnswerPort).loadAnswerCommentContextForUpdate(300L);
    verify(loadAnswerPort, never()).loadAnswerCommentContext(300L);
    verify(loadPostPort, never()).loadPostVisibilityContext(any());
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("updateAnswerComment() updates writer answer comment when answerId matches")
  void updateAnswerComment_matchingAnswer_updatesContent() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(41L, 300L, 200L, null, "before", false, now);
    given(loadCommentPort.loadCommentForUpdate(41L)).willReturn(Optional.of(comment));
    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));
    given(saveCommentPort.saveComment(any(Comment.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    CommentMutationResult result =
        commentService.updateAnswerComment(
            new UpdateAnswerCommentCommand(300L, 41L, 200L, "after"));

    assertThat(result.content()).isEqualTo("after");
    verify(loadCommentPort).loadCommentForUpdate(41L);
    verify(loadCommentPort, never()).loadComment(41L);
    verify(loadAnswerPort).loadAnswerCommentContextForUpdate(300L);
    verify(loadAnswerPort, never()).loadAnswerCommentContext(300L);
    verify(saveCommentPort)
        .saveComment(
            org.mockito.ArgumentMatchers.argThat(saved -> "after".equals(saved.getContent())));
  }

  @Test
  @DisplayName("updateAnswerComment() throws when answerId does not match")
  void updateAnswerComment_answerMismatch_throwsException() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(42L, 300L, 200L, null, "before", false, now);
    given(loadCommentPort.loadCommentForUpdate(42L)).willReturn(Optional.of(comment));
    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));

    assertThatThrownBy(
            () ->
                commentService.updateAnswerComment(
                    new UpdateAnswerCommentCommand(301L, 42L, 200L, "after")))
        .isInstanceOf(CommentTargetMismatchException.class)
        .hasMessage(ErrorCode.COMMENT_TARGET_MISMATCH.getMessage());

    verify(loadCommentPort).loadCommentForUpdate(42L);
    verify(loadCommentPort, never()).loadComment(42L);
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("updateAnswerComment() returns unauthorized before answer mismatch for non-writer")
  void updateAnswerComment_answerMismatchNonWriter_throwsUnauthorizedFirst() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(142L, 300L, 200L, null, "before", false, now);
    given(loadCommentPort.loadCommentForUpdate(142L)).willReturn(Optional.of(comment));

    assertThatThrownBy(
            () ->
                commentService.updateAnswerComment(
                    new UpdateAnswerCommentCommand(301L, 142L, 201L, "after")))
        .isInstanceOf(momzzangseven.mztkbe.global.error.comment.CommentUnauthorizedException.class);

    verify(loadCommentPort).loadCommentForUpdate(142L);
    verify(loadAnswerPort, never()).loadAnswerCommentContextForUpdate(any());
    verify(loadPostPort, never()).loadPostVisibilityContext(any());
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("updateAnswerComment() throws when target comment is post comment")
  void updateAnswerComment_postComment_throwsException() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = comment(43L, 100L, 200L, null, "before", false, now);
    given(loadCommentPort.loadCommentForUpdate(43L)).willReturn(Optional.of(comment));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));

    assertThatThrownBy(
            () ->
                commentService.updateAnswerComment(
                    new UpdateAnswerCommentCommand(300L, 43L, 200L, "after")))
        .isInstanceOf(CommentTargetMismatchException.class)
        .hasMessage(ErrorCode.COMMENT_TARGET_MISMATCH.getMessage());

    verify(loadCommentPort).loadCommentForUpdate(43L);
    verify(loadCommentPort, never()).loadComment(43L);
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("updateAnswerComment() returns unauthorized before target mismatch for post comment")
  void updateAnswerComment_postCommentNonWriter_throwsUnauthorizedFirst() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = comment(143L, 100L, 200L, null, "before", false, now);
    given(loadCommentPort.loadCommentForUpdate(143L)).willReturn(Optional.of(comment));

    assertThatThrownBy(
            () ->
                commentService.updateAnswerComment(
                    new UpdateAnswerCommentCommand(300L, 143L, 201L, "after")))
        .isInstanceOf(momzzangseven.mztkbe.global.error.comment.CommentUnauthorizedException.class);

    verify(loadCommentPort).loadCommentForUpdate(143L);
    verify(loadPostPort, never()).loadPostVisibilityContext(any());
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("updateAnswerComment() checks target writability before answer mismatch for owner")
  void updateAnswerComment_answerMismatchHiddenTarget_throwsWritableErrorFirst() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(144L, 300L, 200L, null, "before", false, now);
    given(loadCommentPort.loadCommentForUpdate(144L)).willReturn(Optional.of(comment));
    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(hiddenPostContext(100L, 200L)));

    assertThatThrownBy(
            () ->
                commentService.updateAnswerComment(
                    new UpdateAnswerCommentCommand(301L, 144L, 200L, "after")))
        .isInstanceOf(BusinessException.class)
        .hasMessage("Post is not in a state that allows comment interactions.");

    verify(loadCommentPort).loadCommentForUpdate(144L);
    verify(loadAnswerPort).loadAnswerCommentContextForUpdate(300L);
    verify(loadPostPort).loadPostVisibilityContext(100L);
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("updateAnswerComment() throws when writer does not own answer comment")
  void updateAnswerComment_nonWriter_throwsException() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(47L, 300L, 200L, null, "before", false, now);
    given(loadCommentPort.loadCommentForUpdate(47L)).willReturn(Optional.of(comment));

    assertThatThrownBy(
            () ->
                commentService.updateAnswerComment(
                    new UpdateAnswerCommentCommand(300L, 47L, 201L, "after")))
        .isInstanceOf(momzzangseven.mztkbe.global.error.comment.CommentUnauthorizedException.class);

    verify(loadCommentPort).loadCommentForUpdate(47L);
    verify(loadCommentPort, never()).loadComment(47L);
    verify(loadAnswerPort, never()).loadAnswerCommentContextForUpdate(300L);
    verify(loadAnswerPort, never()).loadAnswerCommentContext(300L);
    verify(loadPostPort, never()).loadPostVisibilityContext(any());
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("updateAnswerComment() throws when target answer is missing")
  void updateAnswerComment_missingAnswer_throwsException() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(48L, 300L, 200L, null, "before", false, now);
    given(loadCommentPort.loadCommentForUpdate(48L)).willReturn(Optional.of(comment));
    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                commentService.updateAnswerComment(
                    new UpdateAnswerCommentCommand(300L, 48L, 200L, "after")))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.ANSWER_NOT_FOUND.getMessage());

    verify(loadCommentPort).loadCommentForUpdate(48L);
    verify(loadCommentPort, never()).loadComment(48L);
    verify(loadAnswerPort).loadAnswerCommentContextForUpdate(300L);
    verify(loadAnswerPort, never()).loadAnswerCommentContext(300L);
    verify(loadPostPort, never()).loadPostVisibilityContext(any());
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("updateAnswerComment() rejects soft-deleted answer comment before answer lookup")
  void updateAnswerComment_deletedComment_throwsBeforeAnswerLookup() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(49L, 300L, 200L, null, "삭제된 댓글입니다.", true, now);
    given(loadCommentPort.loadCommentForUpdate(49L)).willReturn(Optional.of(comment));

    assertThatThrownBy(
            () ->
                commentService.updateAnswerComment(
                    new UpdateAnswerCommentCommand(300L, 49L, 200L, "after")))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.CANNOT_UPDATE_DELETED_COMMENT.getMessage());

    verify(loadCommentPort).loadCommentForUpdate(49L);
    verify(loadCommentPort, never()).loadComment(49L);
    verify(loadAnswerPort, never()).loadAnswerCommentContextForUpdate(300L);
    verify(loadAnswerPort, never()).loadAnswerCommentContext(300L);
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("deleteAnswerComment() soft-deletes writer answer comment when answerId matches")
  void deleteAnswerComment_matchingAnswer_deletesComment() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(44L, 300L, 200L, null, "comment", false, now);
    given(loadCommentPort.loadCommentForUpdate(44L)).willReturn(Optional.of(comment));
    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));

    commentService.deleteAnswerComment(new DeleteAnswerCommentCommand(300L, 44L, 200L));

    verify(loadCommentPort).loadCommentForUpdate(44L);
    verify(loadCommentPort, never()).loadComment(44L);
    verify(loadAnswerPort).loadAnswerCommentContextForUpdate(300L);
    verify(loadAnswerPort, never()).loadAnswerCommentContext(300L);
    verify(saveCommentPort).saveComment(org.mockito.ArgumentMatchers.argThat(Comment::isDeleted));
  }

  @Test
  @DisplayName("deleteAnswerComment() throws when answerId does not match")
  void deleteAnswerComment_answerMismatch_throwsException() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(45L, 300L, 200L, null, "comment", false, now);
    given(loadCommentPort.loadCommentForUpdate(45L)).willReturn(Optional.of(comment));
    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));

    assertThatThrownBy(
            () ->
                commentService.deleteAnswerComment(new DeleteAnswerCommentCommand(301L, 45L, 200L)))
        .isInstanceOf(CommentTargetMismatchException.class)
        .hasMessage(ErrorCode.COMMENT_TARGET_MISMATCH.getMessage());

    verify(loadCommentPort).loadCommentForUpdate(45L);
    verify(loadCommentPort, never()).loadComment(45L);
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("deleteAnswerComment() returns unauthorized before answer mismatch for non-writer")
  void deleteAnswerComment_answerMismatchNonWriter_throwsUnauthorizedFirst() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(145L, 300L, 200L, null, "comment", false, now);
    given(loadCommentPort.loadCommentForUpdate(145L)).willReturn(Optional.of(comment));

    assertThatThrownBy(
            () ->
                commentService.deleteAnswerComment(
                    new DeleteAnswerCommentCommand(301L, 145L, 201L)))
        .isInstanceOf(momzzangseven.mztkbe.global.error.comment.CommentUnauthorizedException.class);

    verify(loadCommentPort).loadCommentForUpdate(145L);
    verify(loadAnswerPort, never()).loadAnswerCommentContextForUpdate(any());
    verify(loadPostPort, never()).loadPostVisibilityContext(any());
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("deleteAnswerComment() throws when target comment is post comment")
  void deleteAnswerComment_postComment_throwsException() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = comment(46L, 100L, 200L, null, "comment", false, now);
    given(loadCommentPort.loadCommentForUpdate(46L)).willReturn(Optional.of(comment));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));

    assertThatThrownBy(
            () ->
                commentService.deleteAnswerComment(new DeleteAnswerCommentCommand(300L, 46L, 200L)))
        .isInstanceOf(CommentTargetMismatchException.class)
        .hasMessage(ErrorCode.COMMENT_TARGET_MISMATCH.getMessage());

    verify(loadCommentPort).loadCommentForUpdate(46L);
    verify(loadCommentPort, never()).loadComment(46L);
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("deleteAnswerComment() returns unauthorized before target mismatch for post comment")
  void deleteAnswerComment_postCommentNonWriter_throwsUnauthorizedFirst() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = comment(146L, 100L, 200L, null, "comment", false, now);
    given(loadCommentPort.loadCommentForUpdate(146L)).willReturn(Optional.of(comment));

    assertThatThrownBy(
            () ->
                commentService.deleteAnswerComment(
                    new DeleteAnswerCommentCommand(300L, 146L, 201L)))
        .isInstanceOf(momzzangseven.mztkbe.global.error.comment.CommentUnauthorizedException.class);

    verify(loadCommentPort).loadCommentForUpdate(146L);
    verify(loadPostPort, never()).loadPostVisibilityContext(any());
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("deleteAnswerComment() throws when writer does not own answer comment")
  void deleteAnswerComment_nonWriter_throwsException() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(50L, 300L, 200L, null, "comment", false, now);
    given(loadCommentPort.loadCommentForUpdate(50L)).willReturn(Optional.of(comment));

    assertThatThrownBy(
            () ->
                commentService.deleteAnswerComment(new DeleteAnswerCommentCommand(300L, 50L, 201L)))
        .isInstanceOf(momzzangseven.mztkbe.global.error.comment.CommentUnauthorizedException.class);

    verify(loadCommentPort).loadCommentForUpdate(50L);
    verify(loadCommentPort, never()).loadComment(50L);
    verify(loadAnswerPort, never()).loadAnswerCommentContextForUpdate(300L);
    verify(loadAnswerPort, never()).loadAnswerCommentContext(300L);
    verify(loadPostPort, never()).loadPostVisibilityContext(any());
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("deleteAnswerComment() throws when target answer is missing")
  void deleteAnswerComment_missingAnswer_throwsException() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(51L, 300L, 200L, null, "comment", false, now);
    given(loadCommentPort.loadCommentForUpdate(51L)).willReturn(Optional.of(comment));
    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                commentService.deleteAnswerComment(new DeleteAnswerCommentCommand(300L, 51L, 200L)))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.ANSWER_NOT_FOUND.getMessage());

    verify(loadCommentPort).loadCommentForUpdate(51L);
    verify(loadCommentPort, never()).loadComment(51L);
    verify(loadAnswerPort).loadAnswerCommentContextForUpdate(300L);
    verify(loadAnswerPort, never()).loadAnswerCommentContext(300L);
    verify(loadPostPort, never()).loadPostVisibilityContext(any());
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("deleteAnswerComment() rejects soft-deleted answer comment before answer lookup")
  void deleteAnswerComment_deletedComment_throwsBeforeAnswerLookup() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(52L, 300L, 200L, null, "삭제된 댓글입니다.", true, now);
    given(loadCommentPort.loadCommentForUpdate(52L)).willReturn(Optional.of(comment));

    assertThatThrownBy(
            () ->
                commentService.deleteAnswerComment(new DeleteAnswerCommentCommand(300L, 52L, 200L)))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.CANNOT_UPDATE_DELETED_COMMENT.getMessage());

    verify(loadCommentPort).loadCommentForUpdate(52L);
    verify(loadCommentPort, never()).loadComment(52L);
    verify(loadAnswerPort, never()).loadAnswerCommentContextForUpdate(300L);
    verify(loadAnswerPort, never()).loadAnswerCommentContext(300L);
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("updateAnswerComment() rejects when parent question is answer locked")
  void updateAnswerComment_answerLocked_throwsCannotUpdateAnswerOnSolvedPost() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(53L, 300L, 200L, null, "before", false, now);
    given(loadCommentPort.loadCommentForUpdate(53L)).willReturn(Optional.of(comment));
    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L, true)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));

    assertThatThrownBy(
            () ->
                commentService.updateAnswerComment(
                    new UpdateAnswerCommentCommand(300L, 53L, 200L, "after locked")))
        .isInstanceOf(CannotUpdateAnswerOnSolvedPostException.class)
        .hasMessage(ErrorCode.CANNOT_UPDATE_ANSWER_ON_SOLVED_POST.getMessage());

    verify(loadCommentPort).loadCommentForUpdate(53L);
    verify(loadCommentPort, never()).loadComment(53L);
    verify(loadAnswerPort).loadAnswerCommentContextForUpdate(300L);
    verify(loadPostPort).loadPostVisibilityContext(100L);
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("deleteAnswerComment() rejects when parent question is answer locked")
  void deleteAnswerComment_answerLocked_throwsCannotDeleteAnswerOnSolvedPost() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = answerComment(54L, 300L, 200L, null, "comment", false, now);
    given(loadCommentPort.loadCommentForUpdate(54L)).willReturn(Optional.of(comment));
    given(loadAnswerPort.loadAnswerCommentContextForUpdate(300L))
        .willReturn(Optional.of(new LoadAnswerPort.AnswerCommentContext(300L, 100L, true)));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(visiblePostContext(100L)));

    assertThatThrownBy(
            () ->
                commentService.deleteAnswerComment(new DeleteAnswerCommentCommand(300L, 54L, 200L)))
        .isInstanceOf(CannotDeleteAnswerOnSolvedPostException.class)
        .hasMessage(ErrorCode.CANNOT_DELETE_ANSWER_ON_SOLVED_POST.getMessage());

    verify(loadCommentPort).loadCommentForUpdate(54L);
    verify(loadCommentPort, never()).loadComment(54L);
    verify(loadAnswerPort).loadAnswerCommentContextForUpdate(300L);
    verify(loadPostPort).loadPostVisibilityContext(100L);
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  @Test
  @DisplayName("deleteComment() throws when parent post is not publicly writable")
  void deleteComment_hiddenParentPost_throwsBusinessException() {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 10, 0);
    Comment comment = comment(32L, 100L, 200L, null, "comment", false, now);
    given(loadCommentPort.loadCommentForUpdate(32L)).willReturn(Optional.of(comment));
    given(loadPostPort.loadPostVisibilityContext(100L))
        .willReturn(Optional.of(hiddenPostContext(100L, 200L)));

    assertThatThrownBy(() -> commentService.deleteComment(new DeleteCommentCommand(32L, 200L)))
        .isInstanceOf(BusinessException.class);

    verify(loadCommentPort).loadCommentForUpdate(32L);
    verify(loadCommentPort, never()).loadComment(32L);
    verify(saveCommentPort, never()).saveComment(any(Comment.class));
  }

  private void verifyCommentCreatedEventPublished(Long writerId, Long commentId) {
    ArgumentCaptor<CommentCreatedEvent> captor = ArgumentCaptor.forClass(CommentCreatedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().userId()).isEqualTo(writerId);
    assertThat(captor.getValue().commentId()).isEqualTo(commentId);
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
        .postId(100L)
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
