package momzzangseven.mztkbe.modules.comment.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.comment.api.dto.CommentMutationResponse;
import momzzangseven.mztkbe.modules.comment.api.dto.CommentResponse;
import momzzangseven.mztkbe.modules.comment.api.dto.CreateCommentRequest;
import momzzangseven.mztkbe.modules.comment.api.dto.UpdateCommentRequest;
import momzzangseven.mztkbe.modules.comment.application.dto.*;
import momzzangseven.mztkbe.modules.comment.application.port.in.*;
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
  private final UpdateCommentUseCase updateCommentUseCase;
  private final DeleteCommentUseCase deleteCommentUseCase;

  /** Create a new comment */
  @PostMapping("/posts/{postId}/comments")
  public ResponseEntity<ApiResponse<CommentMutationResponse>> createComment(
      @PathVariable Long postId,
      @Valid @RequestBody CreateCommentRequest request,
      @AuthenticationPrincipal Long userId) {

    userId = requireUserId(userId);

    // Command 생성
    CreateCommentCommand command = request.toCommand(postId, userId);

    CommentMutationResult result = createCommentUseCase.createComment(command);
    CommentMutationResponse response = CommentMutationResponse.from(result);

    return ResponseEntity.ok(ApiResponse.success("Comment created successfully", response));
  }

  /** Update comment */
  @PutMapping("/comments/{commentId}")
  public ResponseEntity<ApiResponse<CommentMutationResponse>> updateComment(
      @PathVariable Long commentId,
      @Valid @RequestBody UpdateCommentRequest request,
      @AuthenticationPrincipal Long userId) {

    userId = requireUserId(userId);

    // Command 생성 (UpdateCommentCommand)
    UpdateCommentCommand command = new UpdateCommentCommand(commentId, userId, request.content());

    CommentMutationResult result = updateCommentUseCase.updateComment(command);
    CommentMutationResponse response = CommentMutationResponse.from(result);

    return ResponseEntity.ok(ApiResponse.success("Comment updated successfully", response));
  }

  /** Delete comment */
  @DeleteMapping("/comments/{commentId}")
  public ResponseEntity<ApiResponse<String>> deleteComment(
      @PathVariable Long commentId, @AuthenticationPrincipal Long userId) {

    userId = requireUserId(userId);

    DeleteCommentCommand command = new DeleteCommentCommand(commentId, userId);
    deleteCommentUseCase.deleteComment(command);

    return ResponseEntity.ok(ApiResponse.success("Comment deleted successfully"));
  }

  /** Get root comments (Query 객체 사용) */
  @GetMapping("/posts/{postId}/comments")
  public ResponseEntity<ApiResponse<Page<CommentResponse>>> getRootComments(
      @PathVariable Long postId, @PageableDefault(size = 20) Pageable pageable) {

    GetRootCommentsQuery query = new GetRootCommentsQuery(postId, pageable);

    Page<CommentResult> resultPage = getCommentUseCase.getRootComments(query);
    Page<CommentResponse> responsePage = resultPage.map(CommentResponse::from);

    return ResponseEntity.ok(ApiResponse.success(responsePage));
  }

  /** Get replies (Query 객체 사용) */
  @GetMapping("/comments/{commentId}/replies")
  public ResponseEntity<ApiResponse<Page<CommentResponse>>> getReplies(
      @PathVariable Long commentId, @PageableDefault(size = 10) Pageable pageable) {

    GetRepliesQuery query = new GetRepliesQuery(commentId, pageable);

    Page<CommentResult> resultPage = getCommentUseCase.getReplies(query);
    Page<CommentResponse> responsePage = resultPage.map(CommentResponse::from);

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
