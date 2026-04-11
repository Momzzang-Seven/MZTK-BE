package momzzangseven.mztkbe.modules.admin.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.admin.api.dto.RotateAdminPasswordRequestDTO;
import momzzangseven.mztkbe.modules.admin.application.dto.RotateAdminPasswordCommand;
import momzzangseven.mztkbe.modules.admin.application.port.in.RotateAdminPasswordUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller for admin authentication operations (password management). */
@Slf4j
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

  private final RotateAdminPasswordUseCase rotateAdminPasswordUseCase;

  /** Rotate the authenticated admin's own password. */
  @PostMapping("/password")
  public ResponseEntity<ApiResponse<Void>> rotatePassword(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody RotateAdminPasswordRequestDTO request) {
    requireUserId(userId);
    RotateAdminPasswordCommand command = request.toCommand(userId);
    rotateAdminPasswordUseCase.execute(command);
    return ResponseEntity.ok(ApiResponse.success("Password rotated successfully", null));
  }

  private void requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
  }
}
