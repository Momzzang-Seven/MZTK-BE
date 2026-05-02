package momzzangseven.mztkbe.modules.admin.user.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.admin.user.api.dto.AdminUserListItemResponseDTO;
import momzzangseven.mztkbe.modules.admin.user.api.dto.GetAdminUsersRequestDTO;
import momzzangseven.mztkbe.modules.admin.user.application.dto.AdminUserListItemResult;
import momzzangseven.mztkbe.modules.admin.user.application.dto.GetAdminUsersCommand;
import momzzangseven.mztkbe.modules.admin.user.application.port.in.GetAdminUsersUseCase;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller for admin user management read APIs. */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

  private final GetAdminUsersUseCase getAdminUsersUseCase;

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

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
