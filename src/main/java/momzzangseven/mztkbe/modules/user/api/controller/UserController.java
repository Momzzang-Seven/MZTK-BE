package momzzangseven.mztkbe.modules.user.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.user.api.dto.UpdateUserRoleRequestDTO;
import momzzangseven.mztkbe.modules.user.api.dto.UserResponseDTO;
import momzzangseven.mztkbe.modules.user.application.dto.UpdateUserRoleCommand;
import momzzangseven.mztkbe.modules.user.application.dto.UpdateUserRoleResult;
import momzzangseven.mztkbe.modules.user.application.port.in.UpdateUserRoleUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("users/")
@RequiredArgsConstructor
public class UserController {

  private final UpdateUserRoleUseCase updateUserRoleUseCase;

  /** Update current user's role. Example: USER -> TRAINER */
  @PatchMapping("/me/role")
  public ResponseEntity<ApiResponse<UserResponseDTO>> updateMyRole(
      @Valid @RequestBody UpdateUserRoleRequestDTO request, Authentication authentication) {

    log.info("Role update request received: newRole={}", request.role());

    // 1. Extract userId from authentication
    Long userId = extractUserIdFromAuthentication(authentication);

    // 2. Convert API DTO -> Application Command
    UpdateUserRoleCommand command = UpdateUserRoleCommand.of(userId, request.role());

    // 3. Execute UseCase
    UpdateUserRoleResult result = updateUserRoleUseCase.execute(command);

    // 4. Convert to Response DTO
    UserResponseDTO response = UserResponseDTO.from(result);

    log.info("Role updated successfully: userId={}, newRole={}", userId, result.role());
    return ResponseEntity.ok(ApiResponse.success("Role updated successfully", response));
  }

  // ============================================
  // Utility methods (private)
  // ============================================

  /**
   * Extract userId from Spring Security Authentication object. Assumes JwtAuthenticationFilter sets
   * userId as principal.
   */
  private Long extractUserIdFromAuthentication(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new UserNotAuthenticatedException();
    }

    // userId Set from JwtAuthenticationFilter (principal)
    Object principal = authentication.getPrincipal();

    if (principal instanceof Long) {
      return (Long) principal;
    }

    throw new IllegalStateException("Invalid authentication principal type");
  }
}
