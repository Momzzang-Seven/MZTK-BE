package momzzangseven.mztkbe.modules.comment.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.comment.api.dto.CommentResponse;
import momzzangseven.mztkbe.modules.comment.api.dto.CreateCommentRequest;
import momzzangseven.mztkbe.modules.comment.application.dto.CreateCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.port.in.CreateCommentUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.in.GetCommentUseCase;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CommentController {

  private final CreateCommentUseCase createCommentUseCase;
  private final GetCommentUseCase getCommentUseCase;

  /** Create a new comment (Root or Reply). POST /posts/{postId}/comments */
  @PostMapping("/posts/{postId}/comments")
  public ResponseEntity<ApiResponse<CommentResponse>> createComment(
      @PathVariable Long postId,
      @Valid @RequestBody CreateCommentRequest request,
      @AuthenticationPrincipal Long userId) {

    userId = requireUserId(userId);

    log.info(
        "Create comment request received: postId={}, userId={}, parentId={}",
        postId,
        userId,
        request.parentId());

    CreateCommentCommand command = request.toCommand(postId, userId);
    Comment savedComment = createCommentUseCase.createComment(command);
    CommentResponse response = CommentResponse.from(savedComment);

    log.info("Comment created successfully: commentId={}", response.commentId());

    return ResponseEntity.ok(ApiResponse.success("Comment created successfully", response));
  }

  /** Get root comments of a post. GET /posts/{postId}/comments */
  @GetMapping("/posts/{postId}/comments")
  public ResponseEntity<ApiResponse<Page<CommentResponse>>> getRootComments(
      @PathVariable Long postId, @PageableDefault(size = 20) Pageable pageable) {

    Page<Comment> commentPage = getCommentUseCase.getRootComments(postId, pageable);
    Page<CommentResponse> responsePage = commentPage.map(CommentResponse::from);

    return ResponseEntity.ok(ApiResponse.success(responsePage));
  }

  /** Get replies of a specific comment. GET /comments/{commentId}/replies */
  @GetMapping("/comments/{commentId}/replies")
  public ResponseEntity<ApiResponse<Page<CommentResponse>>> getReplies(
      @PathVariable Long commentId, @PageableDefault(size = 10) Pageable pageable) {

    Page<Comment> replyPage = getCommentUseCase.getReplies(commentId, pageable);
    Page<CommentResponse> responsePage = replyPage.map(CommentResponse::from);

    return ResponseEntity.ok(ApiResponse.success(responsePage));
  }

  // ============================================
  // Utility methods
  // ============================================

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
