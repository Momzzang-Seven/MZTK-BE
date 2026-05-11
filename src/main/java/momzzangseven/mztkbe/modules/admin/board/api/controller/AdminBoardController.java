package momzzangseven.mztkbe.modules.admin.board.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.admin.board.api.dto.AdminBoardCommentResponseDTO;
import momzzangseven.mztkbe.modules.admin.board.api.dto.AdminBoardCommentSearchResponseDTO;
import momzzangseven.mztkbe.modules.admin.board.api.dto.AdminBoardModerationReasonRequestDTO;
import momzzangseven.mztkbe.modules.admin.board.api.dto.AdminBoardModerationResponseDTO;
import momzzangseven.mztkbe.modules.admin.board.api.dto.AdminBoardPostResponseDTO;
import momzzangseven.mztkbe.modules.admin.board.api.dto.GetAdminBoardCommentsRequestDTO;
import momzzangseven.mztkbe.modules.admin.board.api.dto.GetAdminBoardPostCommentsRequestDTO;
import momzzangseven.mztkbe.modules.admin.board.api.dto.GetAdminBoardPostsRequestDTO;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardCommentResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardCommentSearchResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardModerationResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardPostResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.BanAdminBoardCommentCommand;
import momzzangseven.mztkbe.modules.admin.board.application.dto.BanAdminBoardPostCommand;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardCommentsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostCommentsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.dto.UnblockAdminBoardPostCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.BanAdminBoardCommentUseCase;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.BanAdminBoardPostUseCase;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.GetAdminBoardCommentsUseCase;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.GetAdminBoardPostCommentsUseCase;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.GetAdminBoardPostsUseCase;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.UnblockAdminBoardPostUseCase;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller for admin board read APIs. */
@RestController
@RequestMapping("/admin/boards")
@RequiredArgsConstructor
public class AdminBoardController {

  private final GetAdminBoardPostsUseCase getAdminBoardPostsUseCase;
  private final GetAdminBoardCommentsUseCase getAdminBoardCommentsUseCase;
  private final GetAdminBoardPostCommentsUseCase getAdminBoardPostCommentsUseCase;
  private final BanAdminBoardPostUseCase banAdminBoardPostUseCase;
  private final UnblockAdminBoardPostUseCase unblockAdminBoardPostUseCase;
  private final BanAdminBoardCommentUseCase banAdminBoardCommentUseCase;

  /** Returns admin board post rows. */
  @GetMapping("/posts")
  public ResponseEntity<ApiResponse<Page<AdminBoardPostResponseDTO>>> getPosts(
      @AuthenticationPrincipal Long operatorUserId,
      @ModelAttribute GetAdminBoardPostsRequestDTO request) {
    Long validatedOperatorUserId = requireUserId(operatorUserId);
    GetAdminBoardPostsCommand command = request.toCommand(validatedOperatorUserId);
    Page<AdminBoardPostResult> result = getAdminBoardPostsUseCase.execute(command);
    return ResponseEntity.ok(ApiResponse.success(result.map(AdminBoardPostResponseDTO::from)));
  }

  /** Returns admin board global comment search rows. */
  @GetMapping("/comments")
  public ResponseEntity<ApiResponse<Page<AdminBoardCommentSearchResponseDTO>>> getAllComments(
      @AuthenticationPrincipal Long operatorUserId,
      @ModelAttribute GetAdminBoardCommentsRequestDTO request) {
    Long validatedOperatorUserId = requireUserId(operatorUserId);
    GetAdminBoardCommentsCommand command = request.toCommand(validatedOperatorUserId);
    Page<AdminBoardCommentSearchResult> result = getAdminBoardCommentsUseCase.execute(command);
    return ResponseEntity.ok(
        ApiResponse.success(result.map(AdminBoardCommentSearchResponseDTO::from)));
  }

  /** Returns comments for one post in the admin board view. */
  @GetMapping("/posts/{postId}/comments")
  public ResponseEntity<ApiResponse<Page<AdminBoardCommentResponseDTO>>> getComments(
      @AuthenticationPrincipal Long operatorUserId,
      @PathVariable Long postId,
      @ModelAttribute GetAdminBoardPostCommentsRequestDTO request) {
    Long validatedOperatorUserId = requireUserId(operatorUserId);
    GetAdminBoardPostCommentsCommand command = request.toCommand(validatedOperatorUserId, postId);
    Page<AdminBoardCommentResult> result = getAdminBoardPostCommentsUseCase.execute(command);
    return ResponseEntity.ok(ApiResponse.success(result.map(AdminBoardCommentResponseDTO::from)));
  }

  /** Blocks a post from public visibility by changing moderation status only. */
  @PostMapping("/posts/{postId}/ban")
  public ResponseEntity<ApiResponse<AdminBoardModerationResponseDTO>> banPost(
      @AuthenticationPrincipal Long operatorUserId,
      @PathVariable Long postId,
      @Valid @RequestBody AdminBoardModerationReasonRequestDTO request) {
    Long validatedOperatorUserId = requireUserId(operatorUserId);
    BanAdminBoardPostCommand command = request.toBanPostCommand(validatedOperatorUserId, postId);
    AdminBoardModerationResult result = banAdminBoardPostUseCase.execute(command);
    return ResponseEntity.ok(ApiResponse.success(AdminBoardModerationResponseDTO.from(result)));
  }

  /** Restores a post's moderation status without changing publication status. */
  @PostMapping("/posts/{postId}/unblock")
  public ResponseEntity<ApiResponse<AdminBoardModerationResponseDTO>> unblockPost(
      @AuthenticationPrincipal Long operatorUserId,
      @PathVariable Long postId,
      @Valid @RequestBody AdminBoardModerationReasonRequestDTO request) {
    Long validatedOperatorUserId = requireUserId(operatorUserId);
    UnblockAdminBoardPostCommand command =
        request.toUnblockPostCommand(validatedOperatorUserId, postId);
    AdminBoardModerationResult result = unblockAdminBoardPostUseCase.execute(command);
    return ResponseEntity.ok(ApiResponse.success(AdminBoardModerationResponseDTO.from(result)));
  }

  /** Soft-deletes a comment and records the successful moderation action. */
  @PostMapping("/comments/{commentId}/ban")
  public ResponseEntity<ApiResponse<AdminBoardModerationResponseDTO>> banComment(
      @AuthenticationPrincipal Long operatorUserId,
      @PathVariable Long commentId,
      @Valid @RequestBody AdminBoardModerationReasonRequestDTO request) {
    Long validatedOperatorUserId = requireUserId(operatorUserId);
    BanAdminBoardCommentCommand command =
        request.toBanCommentCommand(validatedOperatorUserId, commentId);
    AdminBoardModerationResult result = banAdminBoardCommentUseCase.execute(command);
    return ResponseEntity.ok(ApiResponse.success(AdminBoardModerationResponseDTO.from(result)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
