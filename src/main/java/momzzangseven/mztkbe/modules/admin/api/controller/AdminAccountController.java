package momzzangseven.mztkbe.modules.admin.api.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.admin.api.dto.CreateAdminAccountResponseDTO;
import momzzangseven.mztkbe.modules.admin.api.dto.ListAdminAccountsResponseDTO;
import momzzangseven.mztkbe.modules.admin.api.dto.ResetPeerAdminPasswordResponseDTO;
import momzzangseven.mztkbe.modules.admin.application.dto.AdminAccountSummary;
import momzzangseven.mztkbe.modules.admin.application.dto.CreateAdminAccountResult;
import momzzangseven.mztkbe.modules.admin.application.dto.ResetPeerAdminPasswordCommand;
import momzzangseven.mztkbe.modules.admin.application.dto.ResetPeerAdminPasswordResult;
import momzzangseven.mztkbe.modules.admin.application.port.in.CreateAdminAccountUseCase;
import momzzangseven.mztkbe.modules.admin.application.port.in.ListAdminAccountsUseCase;
import momzzangseven.mztkbe.modules.admin.application.port.in.ResetPeerAdminPasswordUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller for admin account management operations. */
@Slf4j
@RestController
@RequestMapping("/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

  private final CreateAdminAccountUseCase createAdminAccountUseCase;
  private final ListAdminAccountsUseCase listAdminAccountsUseCase;
  private final ResetPeerAdminPasswordUseCase resetPeerAdminPasswordUseCase;

  /** Create a new admin account. */
  @PostMapping
  public ResponseEntity<ApiResponse<CreateAdminAccountResponseDTO>> createAdminAccount(
      @AuthenticationPrincipal Long operatorUserId) {
    requireUserId(operatorUserId);
    CreateAdminAccountResult result = createAdminAccountUseCase.execute(operatorUserId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(CreateAdminAccountResponseDTO.from(result)));
  }

  /** List all active admin accounts. */
  @GetMapping
  public ResponseEntity<ApiResponse<ListAdminAccountsResponseDTO>> listAdminAccounts(
      @AuthenticationPrincipal Long operatorUserId) {
    requireUserId(operatorUserId);
    List<AdminAccountSummary> summaries = listAdminAccountsUseCase.execute(operatorUserId);
    return ResponseEntity.ok(ApiResponse.success(ListAdminAccountsResponseDTO.from(summaries)));
  }

  /** Reset another admin's password (peer-reset). */
  @PostMapping("/{userId}/password/reset")
  public ResponseEntity<ApiResponse<ResetPeerAdminPasswordResponseDTO>> resetPeerPassword(
      @AuthenticationPrincipal Long operatorUserId, @PathVariable Long userId) {
    requireUserId(operatorUserId);
    ResetPeerAdminPasswordCommand command =
        new ResetPeerAdminPasswordCommand(operatorUserId, userId);
    ResetPeerAdminPasswordResult result = resetPeerAdminPasswordUseCase.execute(command);
    return ResponseEntity.ok(ApiResponse.success(ResetPeerAdminPasswordResponseDTO.from(result)));
  }

  private void requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
  }
}
