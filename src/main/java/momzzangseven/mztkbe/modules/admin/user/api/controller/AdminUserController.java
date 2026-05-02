package momzzangseven.mztkbe.modules.admin.user.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.admin.user.api.dto.AdminUserListItemResponseDTO;
import momzzangseven.mztkbe.modules.admin.user.api.dto.AdminUserStatusChangeRequestDTO;
import momzzangseven.mztkbe.modules.admin.user.api.dto.AdminUserStatusChangeResponseDTO;
import momzzangseven.mztkbe.modules.admin.user.api.dto.GetAdminUsersRequestDTO;
import momzzangseven.mztkbe.modules.admin.user.application.dto.AdminUserListItemResult;
import momzzangseven.mztkbe.modules.admin.user.application.dto.ChangeAdminUserStatusCommand;
import momzzangseven.mztkbe.modules.admin.user.application.dto.ChangeAdminUserStatusResult;
import momzzangseven.mztkbe.modules.admin.user.application.dto.GetAdminUsersCommand;
import momzzangseven.mztkbe.modules.admin.user.application.port.in.ChangeAdminUserStatusUseCase;
import momzzangseven.mztkbe.modules.admin.user.application.port.in.GetAdminUsersUseCase;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller for admin user management read APIs. */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

  private final GetAdminUsersUseCase getAdminUsersUseCase;
  private final ChangeAdminUserStatusUseCase changeAdminUserStatusUseCase;

  /** Returns a paged user-management list for admins. */
  @GetMapping
  public ResponseEntity<ApiResponse<Page<AdminUserListItemResponseDTO>>> getUsers(
      @AuthenticationPrincipal Long operatorUserId,
      @ModelAttribute GetAdminUsersRequestDTO request) {
    Long validatedOperatorUserId = requireUserId(operatorUserId);
    GetAdminUsersCommand command = request.toCommand(validatedOperatorUserId);
    Page<AdminUserListItemResult> resultPage = getAdminUsersUseCase.execute(command);
    Page<AdminUserListItemResponseDTO> responsePage =
        resultPage.map(AdminUserListItemResponseDTO::from);
    return ResponseEntity.ok(ApiResponse.success(responsePage));
  }

  /** Changes a managed user's status between ACTIVE and BLOCKED. */
  @PatchMapping("/{userId}/status")
  public ResponseEntity<ApiResponse<AdminUserStatusChangeResponseDTO>> changeStatus(
      @AuthenticationPrincipal Long operatorUserId,
      @PathVariable Long userId,
      @RequestBody AdminUserStatusChangeRequestDTO request) {
    Long validatedOperatorUserId = requireUserId(operatorUserId);
    ChangeAdminUserStatusCommand command = request.toCommand(validatedOperatorUserId, userId);
    ChangeAdminUserStatusResult result = changeAdminUserStatusUseCase.execute(command);
    return ResponseEntity.ok(ApiResponse.success(AdminUserStatusChangeResponseDTO.from(result)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
