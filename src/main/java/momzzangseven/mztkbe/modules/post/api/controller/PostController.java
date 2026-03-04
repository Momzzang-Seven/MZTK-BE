package momzzangseven.mztkbe.modules.post.api.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.post.api.dto.CreateFreePostRequest;
import momzzangseven.mztkbe.modules.post.api.dto.PostResponse;
import momzzangseven.mztkbe.modules.post.api.dto.UpdatePostRequest;
import momzzangseven.mztkbe.modules.post.application.dto.*;
import momzzangseven.mztkbe.modules.post.application.port.in.*;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

  private final CreatePostUseCase createPostUseCase;
  private final GetPostUseCase getPostUseCase;
  private final UpdatePostUseCase updatePostUseCase;
  private final DeletePostUseCase deletePostUseCase;
  private final SearchPostsUseCase searchPostsUseCase;

  // [Create] 자유게시글 작성
  @PostMapping("/free")
  public ResponseEntity<ApiResponse<CreatePostResult>> createFreePost(
      @AuthenticationPrincipal Long userId, @RequestBody @Valid CreateFreePostRequest request) {

<<<<<<< chore/MOM-259-test-integration
    Long validatedUserId = requireUserId(userId);

    CreatePostCommand command =
        CreatePostCommand.of(
            validatedUserId,
            request.title(),
            request.content(),
            PostType.FREE,
            0L,
            request.imageUrls(),
            request.tags());

=======
    CreatePostCommand command = request.toCommand(userId);
>>>>>>> dev
    CreatePostResult response = createPostUseCase.execute(command);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  // [Read] 게시글 상세 조회
  @GetMapping("/{postId}")
  public ResponseEntity<ApiResponse<PostResponse>> getPost(@PathVariable Long postId) {
    PostResult result = getPostUseCase.getPost(postId);
    return ResponseEntity.ok(ApiResponse.success(PostResponse.from(result)));
  }

  // [Read] 게시글 목록 조회
  @GetMapping
  public ResponseEntity<ApiResponse<List<PostResponse>>> getPosts(
      @RequestParam(required = false) PostType type,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false) String search,
      @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    PostSearchCondition condition =
        PostSearchCondition.of(type, tag, search, pageable.getPageNumber(), pageable.getPageSize());

    List<Post> posts = searchPostsUseCase.searchPosts(condition);
    List<PostResponse> response = posts.stream().map(PostResponse::from).toList();

    return ResponseEntity.ok(ApiResponse.success(response));
  }

  // [Update] 게시글 수정
  @PatchMapping("/{postId}")
  public ResponseEntity<ApiResponse<Map<String, Long>>> updatePost(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long postId,
      @RequestBody @Valid UpdatePostRequest request) {

    Long validatedUserId = requireUserId(userId);

    UpdatePostCommand command =
        UpdatePostCommand.of(
            request.title(), request.content(), request.imageUrls(), request.tags());

    updatePostUseCase.updatePost(validatedUserId, postId, command);
    return ResponseEntity.ok(ApiResponse.success(Map.of("postId", postId)));
  }

  // [Delete] 게시글 삭제
  @DeleteMapping("/{postId}")
  public ResponseEntity<ApiResponse<Map<String, Long>>> deletePost(
      @AuthenticationPrincipal Long userId, @PathVariable Long postId) {

    Long validatedUserId = requireUserId(userId);
    deletePostUseCase.deletePost(validatedUserId, postId);
    return ResponseEntity.ok(ApiResponse.success(Map.of("postId", postId)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
