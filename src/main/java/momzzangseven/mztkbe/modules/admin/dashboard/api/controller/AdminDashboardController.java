package momzzangseven.mztkbe.modules.admin.dashboard.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.admin.dashboard.api.dto.AdminBoardStatsResponseDTO;
import momzzangseven.mztkbe.modules.admin.dashboard.api.dto.AdminUserStatsResponseDTO;
import momzzangseven.mztkbe.modules.admin.dashboard.application.dto.AdminBoardStatsResult;
import momzzangseven.mztkbe.modules.admin.dashboard.application.dto.AdminUserStatsResult;
import momzzangseven.mztkbe.modules.admin.dashboard.application.port.in.GetAdminBoardStatsUseCase;
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
  private final GetAdminBoardStatsUseCase getAdminBoardStatsUseCase;

  /** Returns the MOM-239 user statistics card payload. */
  @GetMapping("/user-stats")
  public ResponseEntity<ApiResponse<AdminUserStatsResponseDTO>> getUserStats(
      @AuthenticationPrincipal Long operatorUserId) {
    Long validatedOperatorUserId = requireUserId(operatorUserId);
    AdminUserStatsResult result = getAdminUserStatsUseCase.execute(validatedOperatorUserId);
    return ResponseEntity.ok(ApiResponse.success(AdminUserStatsResponseDTO.from(result)));
  }

  /** Returns the MOM-240 board moderation statistics card payload. */
  @GetMapping("/post-stats")
  public ResponseEntity<ApiResponse<AdminBoardStatsResponseDTO>> getPostStats(
      @AuthenticationPrincipal Long operatorUserId) {
    Long validatedOperatorUserId = requireUserId(operatorUserId);
    AdminBoardStatsResult result = getAdminBoardStatsUseCase.execute(validatedOperatorUserId);
    return ResponseEntity.ok(ApiResponse.success(AdminBoardStatsResponseDTO.from(result)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
