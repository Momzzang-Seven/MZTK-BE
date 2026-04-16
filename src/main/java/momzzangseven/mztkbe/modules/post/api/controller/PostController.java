package momzzangseven.mztkbe.modules.post.api.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.post.api.dto.AcceptAnswerResponse;
import momzzangseven.mztkbe.modules.post.api.dto.CreateFreePostRequest;
import momzzangseven.mztkbe.modules.post.api.dto.CreateQuestionPostRequest;
import momzzangseven.mztkbe.modules.post.api.dto.CreateQuestionPostResponse;
import momzzangseven.mztkbe.modules.post.api.dto.GetPostsResponse;
import momzzangseven.mztkbe.modules.post.api.dto.PostDetailResponse;
import momzzangseven.mztkbe.modules.post.api.dto.PostListResponse;
import momzzangseven.mztkbe.modules.post.api.dto.PostMutationResponse;
import momzzangseven.mztkbe.modules.post.api.dto.UpdatePostRequest;
import momzzangseven.mztkbe.modules.post.application.dto.AcceptAnswerCommand;
import momzzangseven.mztkbe.modules.post.application.dto.AcceptAnswerResult;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostResult;
import momzzangseven.mztkbe.modules.post.application.dto.CreateQuestionPostResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostDetailResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostMutationResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;
import momzzangseven.mztkbe.modules.post.application.dto.RecoverQuestionPostEscrowCommand;
import momzzangseven.mztkbe.modules.post.application.dto.SearchPostsResult;
import momzzangseven.mztkbe.modules.post.application.dto.UpdatePostCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.AcceptAnswerUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.CreatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.CreateQuestionPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.DeletePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.RecoverQuestionPostEscrowUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.SearchPostsUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.UpdatePostUseCase;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Post API controller.
 *
 * <p>Question-board creation is handled separately from free-board creation because only question
 * flows can return Web3 escrow write payloads.
 */
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

  private final CreatePostUseCase createPostUseCase;
  private final CreateQuestionPostUseCase createQuestionPostUseCase;
  private final GetPostUseCase getPostUseCase;
  private final UpdatePostUseCase updatePostUseCase;
  private final DeletePostUseCase deletePostUseCase;
  private final RecoverQuestionPostEscrowUseCase recoverQuestionPostEscrowUseCase;
  private final SearchPostsUseCase searchPostsUseCase;
  private final AcceptAnswerUseCase acceptAnswerUseCase;

  /** Creates a question-board post and returns nullable Web3 escrow write material. */
  @PostMapping("/question")
  public ResponseEntity<ApiResponse<CreateQuestionPostResponse>> createQuestionPost(
      @AuthenticationPrincipal Long userId, @RequestBody @Valid CreateQuestionPostRequest request) {

    Long validatedUserId = requireUserId(userId);

    CreatePostCommand command = request.toCommand(validatedUserId);
    CreateQuestionPostResult response = createQuestionPostUseCase.execute(command);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(CreateQuestionPostResponse.from(response)));
  }

  /** Creates a free-board post using the legacy response contract without Web3 fields. */
  @PostMapping("/free")
  public ResponseEntity<ApiResponse<CreatePostResult>> createFreePost(
      @AuthenticationPrincipal Long userId, @RequestBody @Valid CreateFreePostRequest request) {

    Long validatedUserId = requireUserId(userId);

    CreatePostCommand command = request.toCommand(validatedUserId);
    CreatePostResult response = createPostUseCase.execute(command);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  /** Returns public post detail; {@code userId} may be {@code null} for anonymous reads. */
  @GetMapping("/{postId}")
  public ResponseEntity<ApiResponse<PostDetailResponse>> getPost(
      @AuthenticationPrincipal Long userId, @PathVariable Long postId) {
    PostDetailResult result = getPostUseCase.getPost(postId, userId);
    return ResponseEntity.ok(ApiResponse.success(PostDetailResponse.from(result)));
  }

  /** Returns the authenticated caller's post list view. */
  @GetMapping
  public ResponseEntity<ApiResponse<GetPostsResponse>> getPosts(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) PostType type,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false) String search,
      @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Long validatedUserId = requireUserId(userId);
    PostSearchCondition condition =
        PostSearchCondition.of(type, tag, search, pageable.getPageNumber(), pageable.getPageSize());

    SearchPostsResult result = searchPostsUseCase.searchPosts(condition, validatedUserId);
    List<PostListResponse> response = result.posts().stream().map(PostListResponse::from).toList();

    return ResponseEntity.ok(ApiResponse.success(new GetPostsResponse(response, result.hasNext())));
  }

  /**
   * Updates a post and includes question Web3 payload only when a new escrow intent was created.
   */
  @PatchMapping("/{postId}")
  public ResponseEntity<ApiResponse<PostMutationResponse>> updatePost(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long postId,
      @RequestBody @Valid UpdatePostRequest request) {

    Long validatedUserId = requireUserId(userId);

    UpdatePostCommand command =
        UpdatePostCommand.of(
            request.title(), request.content(), request.imageIds(), request.tags());

    PostMutationResult result = updatePostUseCase.updatePost(validatedUserId, postId, command);
    return ResponseEntity.ok(ApiResponse.success(PostMutationResponse.from(result)));
  }

  /** Deletes a post and includes question Web3 payload only for question-board deletes. */
  @DeleteMapping("/{postId}")
  public ResponseEntity<ApiResponse<PostMutationResponse>> deletePost(
      @AuthenticationPrincipal Long userId, @PathVariable Long postId) {

    Long validatedUserId = requireUserId(userId);

    PostMutationResult result = deletePostUseCase.deletePost(validatedUserId, postId);
    return ResponseEntity.ok(ApiResponse.success(PostMutationResponse.from(result)));
  }

  /**
   * Recreates a question-create escrow intent when the local question exists but on-chain
   * projection is still missing after the earlier create flow terminated.
   */
  @PostMapping("/{postId}/web3/recover-create")
  public ResponseEntity<ApiResponse<PostMutationResponse>> recoverQuestionCreate(
      @AuthenticationPrincipal Long userId, @PathVariable Long postId) {

    Long validatedUserId = requireUserId(userId);
    PostMutationResult result =
        recoverQuestionPostEscrowUseCase.recoverQuestionCreate(
            new RecoverQuestionPostEscrowCommand(validatedUserId, postId));
    return ResponseEntity.ok(ApiResponse.success(PostMutationResponse.from(result)));
  }

  /** Accepts an answer and returns nullable question accept escrow write payload. */
  @PostMapping("/{postId}/answers/{answerId}/accept")
  public ResponseEntity<ApiResponse<AcceptAnswerResponse>> acceptAnswer(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long postId,
      @PathVariable Long answerId) {

    Long validatedUserId = requireUserId(userId);

    AcceptAnswerResult result =
        acceptAnswerUseCase.execute(new AcceptAnswerCommand(postId, answerId, validatedUserId));
    return ResponseEntity.ok(ApiResponse.success(AcceptAnswerResponse.from(result)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
