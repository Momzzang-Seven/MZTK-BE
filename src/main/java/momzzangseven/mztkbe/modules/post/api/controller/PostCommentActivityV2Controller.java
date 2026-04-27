package momzzangseven.mztkbe.modules.post.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.post.api.dto.GetMyCommentedPostsV2Request;
import momzzangseven.mztkbe.modules.post.api.dto.GetMyCommentedPostsV2Response;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyCommentedPostsCursorResult;
import momzzangseven.mztkbe.modules.post.application.port.in.GetMyCommentedPostsCursorUseCase;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PostCommentActivityV2Controller {

  private final GetMyCommentedPostsCursorUseCase getMyCommentedPostsCursorUseCase;

  @GetMapping("/v2/users/me/commented-posts")
  public ResponseEntity<ApiResponse<GetMyCommentedPostsV2Response>> getMyCommentedPosts(
      @AuthenticationPrincipal Long userId,
      @RequestParam PostType type,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    Long validatedUserId = requireUserId(userId);
    GetMyCommentedPostsCursorResult result =
        getMyCommentedPostsCursorUseCase.execute(
            new GetMyCommentedPostsV2Request(type, cursor, size).toCommand(validatedUserId));
    return ResponseEntity.ok(ApiResponse.success(GetMyCommentedPostsV2Response.from(result)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
