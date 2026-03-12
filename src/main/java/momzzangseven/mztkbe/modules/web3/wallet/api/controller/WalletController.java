package momzzangseven.mztkbe.modules.web3.wallet.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.web3.wallet.api.dto.RegisterWalletRequestDTO;
import momzzangseven.mztkbe.modules.web3.wallet.api.dto.RegisterWalletResponseDTO;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.UnlinkWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RegisterWalletUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.UnlinkWalletUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Wallet API Controller
 *
 * <p>Endpoints for wallet registration and management.
 */
@Slf4j
@RestController
@RequestMapping("/web3/wallets")
@RequiredArgsConstructor
public class WalletController {

  private final RegisterWalletUseCase registerWalletUseCase;
  private final UnlinkWalletUseCase unlinkWalletUseCase;

  /**
   * Register wallet
   *
   * <p>POST /web3/wallets
   */
  @PostMapping
  public ResponseEntity<ApiResponse<RegisterWalletResponseDTO>> registerWallet(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody RegisterWalletRequestDTO request) {

    Long validatedUserId = requireUserId(userId);
    log.info(
        "Wallet registration request: userId={}, address={}",
        validatedUserId,
        request.walletAddress());

    RegisterWalletCommand command =
        new RegisterWalletCommand(
            validatedUserId, request.walletAddress(), request.signature(), request.nonce());

    RegisterWalletResult result = registerWalletUseCase.execute(command);

    RegisterWalletResponseDTO response = RegisterWalletResponseDTO.from(result);

    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  /**
   * Unlink wallet
   *
   * <p>DELETE /web3/wallets/{walletAddress}
   *
   * @param userId authenticated user ID
   * @param walletAddress Ethereum wallet address (0x-prefixed)
   * @return 200 OK with success response
   */
  @DeleteMapping("/{walletAddress}")
  public ResponseEntity<ApiResponse<Void>> unlinkWallet(
      @AuthenticationPrincipal Long userId, @PathVariable("walletAddress") String walletAddress) {

    Long validatedUserId = requireUserId(userId);
    log.info(
        "Wallet deactivation request: userId={}, walletAddress={}", validatedUserId, walletAddress);

    UnlinkWalletCommand command = new UnlinkWalletCommand(validatedUserId, walletAddress);
    unlinkWalletUseCase.execute(command);

    return ResponseEntity.ok(ApiResponse.success(null));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
