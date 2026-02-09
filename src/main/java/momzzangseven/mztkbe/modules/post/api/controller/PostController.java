package momzzangseven.mztkbe.modules.post.api.controller;

import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.api.dto.CreateFreePostRequest;
import momzzangseven.mztkbe.modules.post.api.dto.CreatePostResponse;
import momzzangseven.mztkbe.modules.post.api.dto.PostResponse;
import momzzangseven.mztkbe.modules.post.api.dto.UpdatePostRequest;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.CreatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.DeletePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.UpdatePostUseCase;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
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

  // [Create] 1-A. 자유게시글 작성
  @PostMapping("/free")
  public ResponseEntity<?> createFreePost(
      @AuthenticationPrincipal Long userId, @RequestBody @Valid CreateFreePostRequest request) {

    // 1. 요청 DTO -> Command 변환
    CreatePostCommand command =
        CreatePostCommand.of(
            userId, request.title(), request.content(), PostType.FREE, null, request.imageUrls());

    // 2. 서비스 호출
    Long savedPostId = createPostUseCase.createPost(command);

    // 3. 응답 생성
    CreatePostResponse responseData = new CreatePostResponse(savedPostId);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("code", 201, "message", "CREATED", "data", responseData));
  }

  // [Read] 2. 게시글 상세 조회
  @GetMapping("/{postId}")
  public ResponseEntity<?> getPost(@PathVariable Long postId) {
    Post post = getPostUseCase.getPost(postId);
    PostResponse response = PostResponse.from(post);

    return ResponseEntity.ok(Map.of("code", 200, "message", "SUCCESS", "data", response));
  }

  // [Update] 3. 게시글 수정
  @PutMapping("/{postId}")
  public ResponseEntity<?> updatePost(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long postId,
      @RequestBody @Valid UpdatePostRequest request) {
    updatePostUseCase.updatePost(userId, postId, request);

    return ResponseEntity.ok(
        Map.of("code", 200, "message", "UPDATED", "data", Map.of("postId", postId)));
  }

  // [Delete] 4. 게시글 삭제
  @DeleteMapping("/{postId}")
  public ResponseEntity<?> deletePost(
      @AuthenticationPrincipal Long userId, @PathVariable Long postId) {
    deletePostUseCase.deletePost(userId, postId);

    return ResponseEntity.ok(
        Map.of("code", 200, "message", "DELETED", "data", Map.of("postId", postId)));
  }
}
