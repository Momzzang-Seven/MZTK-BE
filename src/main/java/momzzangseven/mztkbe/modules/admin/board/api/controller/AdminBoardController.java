package momzzangseven.mztkbe.modules.admin.board.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.admin.board.api.dto.AdminBoardCommentResponseDTO;
import momzzangseven.mztkbe.modules.admin.board.api.dto.AdminBoardPostResponseDTO;
import momzzangseven.mztkbe.modules.admin.board.api.dto.GetAdminBoardPostCommentsRequestDTO;
import momzzangseven.mztkbe.modules.admin.board.api.dto.GetAdminBoardPostsRequestDTO;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardCommentResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardPostResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostCommentsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.GetAdminBoardPostCommentsUseCase;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.GetAdminBoardPostsUseCase;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller for admin board read APIs. */
@RestController
@RequestMapping("/admin/boards")
@RequiredArgsConstructor
public class AdminBoardController {

  private final GetAdminBoardPostsUseCase getAdminBoardPostsUseCase;
  private final GetAdminBoardPostCommentsUseCase getAdminBoardPostCommentsUseCase;

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

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
