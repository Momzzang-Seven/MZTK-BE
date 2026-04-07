package momzzangseven.mztkbe.modules.user.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.user.api.dto.GetMyProfileResponseDTO;
import momzzangseven.mztkbe.modules.user.api.dto.UpdateUserRoleRequestDTO;
import momzzangseven.mztkbe.modules.user.api.dto.UserResponseDTO;
import momzzangseven.mztkbe.modules.user.application.dto.GetMyProfileResult;
import momzzangseven.mztkbe.modules.user.application.dto.UpdateUserRoleCommand;
import momzzangseven.mztkbe.modules.user.application.dto.UpdateUserRoleResult;
import momzzangseven.mztkbe.modules.user.application.port.in.GetMyProfileUseCase;
import momzzangseven.mztkbe.modules.user.application.port.in.UpdateUserRoleUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

  private final GetMyProfileUseCase getMyProfileUseCase;
  private final UpdateUserRoleUseCase updateUserRoleUseCase;

  /** Retrieve the full profile of the currently authenticated user. */
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<GetMyProfileResponseDTO>> getMyProfile(
      @AuthenticationPrincipal Long userId) {
    userId = requireUserId(userId);
    GetMyProfileResult result = getMyProfileUseCase.execute(userId);
    return ResponseEntity.ok(ApiResponse.success(GetMyProfileResponseDTO.from(result)));
  }

  /** Update current user's role. Example: USER -> TRAINER */
  @PatchMapping("/me/role")
  public ResponseEntity<ApiResponse<UserResponseDTO>> updateMyRole(
      @Valid @RequestBody UpdateUserRoleRequestDTO request, @AuthenticationPrincipal Long userId) {

    log.info("Role update request received: newRole={}", request.role());

    userId = requireUserId(userId);

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

  /*
   * (Legacy) Spring Security Authentication에서 userId를 추출하던 유틸 메서드.
   * - 컨트롤러가 Authentication을 직접 받는 방식의 참고용으로만 남겨둡니다.
   * - 현재는 @AuthenticationPrincipal Long userId를 사용해서 보일러플레이트를 줄이고,
   *   컨트롤러 간 유틸 호출(의존) 문제를 피합니다.
   *
   * private Long extractUserIdFromAuthentication(Authentication authentication) {
   *   if (authentication == null || !authentication.isAuthenticated()) {
   *     throw new UserNotAuthenticatedException();
   *   }
   *
   *   Object principal = authentication.getPrincipal();
   *   if (principal instanceof Long) {
   *     return (Long) principal;
   *   }
   *
   *   throw new IllegalStateException("Invalid authentication principal type");
   * }
   */
  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
