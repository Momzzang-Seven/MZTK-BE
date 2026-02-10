package momzzangseven.mztkbe.modules.post.api.controller;

import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.post.api.dto.CreateFreePostRequest;
import momzzangseven.mztkbe.modules.post.api.dto.PostResponse;
import momzzangseven.mztkbe.modules.post.api.dto.UpdatePostRequest;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostResult;
import momzzangseven.mztkbe.modules.post.application.dto.UpdatePostCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.CreatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.DeletePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.UpdatePostUseCase;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

  private final CreatePostUseCase createPostUseCase;
  private final GetPostUseCase getPostUseCase;
  private final UpdatePostUseCase updatePostUseCase;
  private final DeletePostUseCase deletePostUseCase;

  // [Create] 자유게시글 작성
  @PostMapping("/free")
  public ResponseEntity<ApiResponse<CreatePostResult>> createFreePost(
      @AuthenticationPrincipal Long userId, @RequestBody @Valid CreateFreePostRequest request) {
    CreatePostCommand command =
        CreatePostCommand.of(
            userId, request.title(), request.content(), PostType.FREE, null, request.imageUrls());

    CreatePostResult response = createPostUseCase.createPost(command);

    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  // [Read] 게시글 상세 조회
  @GetMapping("/{postId}")
  public ResponseEntity<ApiResponse<PostResponse>> getPost(@PathVariable Long postId) {
    PostResult result = getPostUseCase.getPost(postId);
    PostResponse response = PostResponse.from(result);

    return ResponseEntity.ok(ApiResponse.success(response));
  }

  // [Update] 게시글 수정
  @PutMapping("/{postId}")
  public ResponseEntity<ApiResponse<Map<String, Long>>> updatePost(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long postId,
      @RequestBody @Valid UpdatePostRequest request) {
    UpdatePostCommand command =
        UpdatePostCommand.of(request.title(), request.content(), request.imageUrls());
    updatePostUseCase.updatePost(userId, postId, command);

    return ResponseEntity.ok(ApiResponse.success(Map.of("postId", postId)));
  }

  // [Delete] 게시글 삭제
  @DeleteMapping("/{postId}")
  public ResponseEntity<ApiResponse<Map<String, Long>>> deletePost(
      @AuthenticationPrincipal Long userId, @PathVariable Long postId) {
    deletePostUseCase.deletePost(userId, postId);

    return ResponseEntity.ok(ApiResponse.success(Map.of("postId", postId)));
  }
}
