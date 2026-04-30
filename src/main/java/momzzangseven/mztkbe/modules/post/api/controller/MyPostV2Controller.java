package momzzangseven.mztkbe.modules.post.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.post.api.dto.GetMyPostsV2Request;
import momzzangseven.mztkbe.modules.post.api.dto.GetMyPostsV2Response;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyPostsCursorCommand;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyPostsCursorResult;
import momzzangseven.mztkbe.modules.post.application.port.in.GetMyPostsCursorUseCase;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MyPostV2Controller {

  private final GetMyPostsCursorUseCase getMyPostsCursorUseCase;

  @GetMapping("/v2/users/me/posts")
  public ResponseEntity<ApiResponse<GetMyPostsV2Response>> getMyPosts(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) PostType type,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    Long validatedUserId = requireUserId(userId);
    GetMyPostsCursorCommand command =
        new GetMyPostsV2Request(type, tag, search, cursor, size).toCommand(validatedUserId);
    GetMyPostsCursorResult result = getMyPostsCursorUseCase.execute(command);
    return ResponseEntity.ok(ApiResponse.success(GetMyPostsV2Response.from(result)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
