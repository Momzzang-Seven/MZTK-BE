package momzzangseven.mztkbe.modules.admin.dashboard.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.admin.dashboard.api.dto.AdminUserStatsResponseDTO;
import momzzangseven.mztkbe.modules.admin.dashboard.application.dto.AdminUserStatsResult;
import momzzangseven.mztkbe.modules.admin.dashboard.application.port.in.GetAdminUserStatsUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller for admin dashboard read APIs. */
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

  private final GetAdminUserStatsUseCase getAdminUserStatsUseCase;

  /** Returns the MOM-239 user statistics card payload. */
  @GetMapping("/user-stats")
  public ResponseEntity<ApiResponse<AdminUserStatsResponseDTO>> getUserStats(
      @AuthenticationPrincipal Long operatorUserId) {
    Long validatedOperatorUserId = requireUserId(operatorUserId);
    AdminUserStatsResult result = getAdminUserStatsUseCase.execute(validatedOperatorUserId);
    return ResponseEntity.ok(ApiResponse.success(AdminUserStatsResponseDTO.from(result)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
