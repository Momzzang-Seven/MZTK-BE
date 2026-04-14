package momzzangseven.mztkbe.modules.post.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.post.api.dto.PostLikeResponse;
import momzzangseven.mztkbe.modules.post.application.dto.LikePostCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.LikePostUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PostLikeController {

  private final LikePostUseCase likePostUseCase;

  @PostMapping("/posts/{postId}/likes")
  public ResponseEntity<ApiResponse<PostLikeResponse>> likePost(
      @AuthenticationPrincipal Long userId, @PathVariable Long postId) {
    Long validatedUserId = requireUserId(userId);
    return ResponseEntity.ok(
        ApiResponse.success(
            PostLikeResponse.from(
                likePostUseCase.like(LikePostCommand.forPost(postId, validatedUserId)))));
  }

  @DeleteMapping("/posts/{postId}/likes")
  public ResponseEntity<ApiResponse<PostLikeResponse>> unlikePost(
      @AuthenticationPrincipal Long userId, @PathVariable Long postId) {
    Long validatedUserId = requireUserId(userId);
    return ResponseEntity.ok(
        ApiResponse.success(
            PostLikeResponse.from(
                likePostUseCase.unlike(LikePostCommand.forPost(postId, validatedUserId)))));
  }

  @PostMapping("/questions/{postId}/answers/{answerId}/likes")
  public ResponseEntity<ApiResponse<PostLikeResponse>> likeAnswer(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long postId,
      @PathVariable Long answerId) {
    Long validatedUserId = requireUserId(userId);
    return ResponseEntity.ok(
        ApiResponse.success(
            PostLikeResponse.from(
                likePostUseCase.like(
                    LikePostCommand.forAnswer(postId, answerId, validatedUserId)))));
  }

  @DeleteMapping("/questions/{postId}/answers/{answerId}/likes")
  public ResponseEntity<ApiResponse<PostLikeResponse>> unlikeAnswer(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long postId,
      @PathVariable Long answerId) {
    Long validatedUserId = requireUserId(userId);
    return ResponseEntity.ok(
        ApiResponse.success(
            PostLikeResponse.from(
                likePostUseCase.unlike(
                    LikePostCommand.forAnswer(postId, answerId, validatedUserId)))));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
