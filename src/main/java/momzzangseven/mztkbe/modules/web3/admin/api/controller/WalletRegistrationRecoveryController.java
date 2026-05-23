package momzzangseven.mztkbe.modules.web3.admin.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.ReplayWalletRegistrationApprovalRequestDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.ReplayWalletRegistrationApprovalResponseDTO;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ReplayWalletRegistrationApprovalResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ReplayWalletRegistrationApprovalUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/web3/wallet-registrations")
public class WalletRegistrationRecoveryController {

  private final ReplayWalletRegistrationApprovalUseCase replayWalletRegistrationApprovalUseCase;

  @PostMapping("/replay-confirmed-approval")
  public ResponseEntity<ApiResponse<ReplayWalletRegistrationApprovalResponseDTO>>
      replayConfirmedApproval(
          @AuthenticationPrincipal Long operatorId,
          @Valid @RequestBody ReplayWalletRegistrationApprovalRequestDTO request) {
    ReplayWalletRegistrationApprovalResult result =
        replayWalletRegistrationApprovalUseCase.execute(
            request.toCommand(requireOperatorId(operatorId)));
    return ResponseEntity.ok(
        ApiResponse.success(ReplayWalletRegistrationApprovalResponseDTO.from(result)));
  }

  private Long requireOperatorId(Long operatorId) {
    if (operatorId == null) {
      throw new UserNotAuthenticatedException();
    }
    return operatorId;
  }
}
