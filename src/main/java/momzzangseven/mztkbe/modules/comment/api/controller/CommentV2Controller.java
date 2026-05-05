package momzzangseven.mztkbe.modules.comment.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.comment.api.dto.CommentMutationResponse;
import momzzangseven.mztkbe.modules.comment.api.dto.CreateCommentRequest;
import momzzangseven.mztkbe.modules.comment.api.dto.GetCommentsCursorRequest;
import momzzangseven.mztkbe.modules.comment.api.dto.GetCommentsResponse;
import momzzangseven.mztkbe.modules.comment.application.dto.CommentMutationResult;
import momzzangseven.mztkbe.modules.comment.application.dto.CreateCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.dto.GetCommentsCursorResult;
import momzzangseven.mztkbe.modules.comment.application.port.in.CreateCommentUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.in.GetCommentCursorUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CommentV2Controller {

  private final CreateCommentUseCase createCommentUseCase;
  private final GetCommentCursorUseCase getCommentCursorUseCase;

  @GetMapping("/v2/posts/{postId}/comments")
  public ResponseEntity<ApiResponse<GetCommentsResponse>> getRootComments(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long postId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    GetCommentsCursorRequest request = new GetCommentsCursorRequest(cursor, size);
    GetCommentsCursorResult result =
        getCommentCursorUseCase.getRootCommentsByCursor(request.toRootQuery(postId, userId));
    return ResponseEntity.ok(ApiResponse.success(GetCommentsResponse.from(result)));
  }

  @GetMapping("/v2/answers/{answerId}/comments")
  public ResponseEntity<ApiResponse<GetCommentsResponse>> getAnswerRootComments(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long answerId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    GetCommentsCursorRequest request = new GetCommentsCursorRequest(cursor, size);
    GetCommentsCursorResult result =
        getCommentCursorUseCase.getAnswerRootCommentsByCursor(
            request.toAnswerRootQuery(answerId, userId));
    return ResponseEntity.ok(ApiResponse.success(GetCommentsResponse.from(result)));
  }

  @PostMapping("/v2/answers/{answerId}/comments")
  public ResponseEntity<ApiResponse<CommentMutationResponse>> createAnswerComment(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long answerId,
      @RequestBody @Valid CreateCommentRequest request) {
    CreateCommentCommand command = request.toAnswerCommand(answerId, requireUserId(userId));
    CommentMutationResult result = createCommentUseCase.createComment(command);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(CommentMutationResponse.from(result)));
  }

  @GetMapping("/v2/comments/{commentId}/replies")
  public ResponseEntity<ApiResponse<GetCommentsResponse>> getReplies(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long commentId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    GetCommentsCursorRequest request = new GetCommentsCursorRequest(cursor, size);
    GetCommentsCursorResult result =
        getCommentCursorUseCase.getRepliesByCursor(request.toRepliesQuery(commentId, userId));
    return ResponseEntity.ok(ApiResponse.success(GetCommentsResponse.from(result)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
