package momzzangseven.mztkbe.modules.post.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.post.api.dto.GetMyLikedPostsV2Request;
import momzzangseven.mztkbe.modules.post.api.dto.GetMyLikedPostsV2Response;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyLikedPostsCursorCommand;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyLikedPostsCursorResult;
import momzzangseven.mztkbe.modules.post.application.port.in.GetMyLikedPostsCursorUseCase;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PostLikeV2Controller {

  private final GetMyLikedPostsCursorUseCase getMyLikedPostsCursorUseCase;

  @GetMapping("/v2/users/me/liked-posts")
  public ResponseEntity<ApiResponse<GetMyLikedPostsV2Response>> getMyLikedPosts(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) PostType type,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    Long validatedUserId = requireUserId(userId);
    GetMyLikedPostsCursorCommand command =
        new GetMyLikedPostsV2Request(type, cursor, size).toCommand(validatedUserId);
    GetMyLikedPostsCursorResult result = getMyLikedPostsCursorUseCase.execute(command);
    return ResponseEntity.ok(ApiResponse.success(GetMyLikedPostsV2Response.from(result)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
