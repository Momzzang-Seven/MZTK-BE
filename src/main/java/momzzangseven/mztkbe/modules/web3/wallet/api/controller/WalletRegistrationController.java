package momzzangseven.mztkbe.modules.web3.wallet.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.web3.wallet.api.dto.WalletRegistrationResponseDTO;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.GetWalletRegistrationStatusQuery;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RetryWalletRegistrationApprovalCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationStatusResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetWalletRegistrationStatusUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RetryWalletRegistrationApprovalUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Wallet registration progress API. */
@Slf4j
@RestController
@RequestMapping("/web3/wallet-registrations")
@RequiredArgsConstructor
public class WalletRegistrationController {

  private final GetWalletRegistrationStatusUseCase getStatusUseCase;
  private final RetryWalletRegistrationApprovalUseCase retryApprovalUseCase;

  @GetMapping("/{registrationId}")
  public ResponseEntity<ApiResponse<WalletRegistrationResponseDTO>> getStatus(
      @AuthenticationPrincipal Long userId, @PathVariable("registrationId") String registrationId) {
    Long requesterUserId = requireUserId(userId);
    WalletRegistrationStatusResult result =
        getStatusUseCase.execute(
            new GetWalletRegistrationStatusQuery(requesterUserId, registrationId));
    return ResponseEntity.ok(ApiResponse.success(WalletRegistrationResponseDTO.from(result)));
  }

  @PostMapping("/{registrationId}/approval-intent")
  public ResponseEntity<ApiResponse<WalletRegistrationResponseDTO>> retryApproval(
      @AuthenticationPrincipal Long userId, @PathVariable("registrationId") String registrationId) {
    Long requesterUserId = requireUserId(userId);
    log.info(
        "Wallet registration approval retry request: userId={}, registrationId={}",
        requesterUserId,
        registrationId);
    WalletRegistrationStatusResult result =
        retryApprovalUseCase.execute(
            new RetryWalletRegistrationApprovalCommand(requesterUserId, registrationId));
    return ResponseEntity.ok(ApiResponse.success(WalletRegistrationResponseDTO.from(result)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
