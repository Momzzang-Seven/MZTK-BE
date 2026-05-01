package momzzangseven.mztkbe.modules.post.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.post.api.dto.GetPostsV2Request;
import momzzangseven.mztkbe.modules.post.api.dto.GetPostsV2Response;
import momzzangseven.mztkbe.modules.post.application.dto.PostCursorSearchCondition;
import momzzangseven.mztkbe.modules.post.application.dto.SearchPostsCursorResult;
import momzzangseven.mztkbe.modules.post.application.port.in.SearchPostsCursorUseCase;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PostV2Controller {

  private final SearchPostsCursorUseCase searchPostsCursorUseCase;

  @GetMapping("/v2/posts")
  public ResponseEntity<ApiResponse<GetPostsV2Response>> getPosts(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) PostType type,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    Long validatedUserId = requireUserId(userId);
    PostCursorSearchCondition condition =
        new GetPostsV2Request(type, tag, search, cursor, size).toCommand();
    SearchPostsCursorResult result =
        searchPostsCursorUseCase.searchPostsByCursor(condition, validatedUserId);
    return ResponseEntity.ok(ApiResponse.success(GetPostsV2Response.from(result)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
