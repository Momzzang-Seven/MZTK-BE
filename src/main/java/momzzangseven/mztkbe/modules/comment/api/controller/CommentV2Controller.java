package momzzangseven.mztkbe.modules.comment.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.comment.api.dto.GetCommentsCursorRequest;
import momzzangseven.mztkbe.modules.comment.api.dto.GetCommentsResponse;
import momzzangseven.mztkbe.modules.comment.application.dto.GetCommentsCursorResult;
import momzzangseven.mztkbe.modules.comment.application.port.in.GetCommentCursorUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CommentV2Controller {

  private final GetCommentCursorUseCase getCommentCursorUseCase;

  @GetMapping("/v2/posts/{postId}/comments")
  public ResponseEntity<ApiResponse<GetCommentsResponse>> getRootComments(
      @PathVariable Long postId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    GetCommentsCursorRequest request = new GetCommentsCursorRequest(cursor, size);
    GetCommentsCursorResult result =
        getCommentCursorUseCase.getRootCommentsByCursor(request.toRootQuery(postId));
    return ResponseEntity.ok(ApiResponse.success(GetCommentsResponse.from(result)));
  }

  @GetMapping("/v2/comments/{commentId}/replies")
  public ResponseEntity<ApiResponse<GetCommentsResponse>> getReplies(
      @PathVariable Long commentId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    GetCommentsCursorRequest request = new GetCommentsCursorRequest(cursor, size);
    GetCommentsCursorResult result =
        getCommentCursorUseCase.getRepliesByCursor(request.toRepliesQuery(commentId));
    return ResponseEntity.ok(ApiResponse.success(GetCommentsResponse.from(result)));
  }
}
