package momzzangseven.mztkbe.modules.web3.wallet.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.web3.wallet.api.dto.RegisterWalletRequestDTO;
import momzzangseven.mztkbe.modules.web3.wallet.api.dto.WalletRegisterResponseDTO;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.DeactivateWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.DeactivateWalletUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RegisterWalletUseCase;
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
  private final DeactivateWalletUseCase deactivateWalletUseCase;

  /**
   * Register wallet
   *
   * <p>POST /web3/wallets
   */
  @PostMapping
  public ResponseEntity<ApiResponse<WalletRegisterResponseDTO>> registerWallet(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody RegisterWalletRequestDTO request) {

    log.info("Wallet registration request: userId={}, address={}", userId, request.walletAddress());

    RegisterWalletCommand command =
        new RegisterWalletCommand(
            userId, request.walletAddress(), request.signature(), request.nonce());

    RegisterWalletResult result = registerWalletUseCase.execute(command);

    WalletRegisterResponseDTO response = WalletRegisterResponseDTO.from(result);

    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  /**
   * Deactivate wallet (soft delete)
   *
   * <p>DELETE /web3/wallets/{walletAddress}
   *
   * @param userId authenticated user ID
   * @param walletAddress Ethereum wallet address (0x-prefixed)
   * @return 200 OK with success response
   */
  @DeleteMapping("/{walletAddress}")
  public ResponseEntity<ApiResponse<Void>> deactivateWallet(
      @AuthenticationPrincipal Long userId, @PathVariable("walletAddress") String walletAddress) {

    log.info("Wallet deactivation request: userId={}, walletAddress={}", userId, walletAddress);

    DeactivateWalletCommand command = new DeactivateWalletCommand(userId, walletAddress);
    deactivateWalletUseCase.execute(command);

    return ResponseEntity.ok(ApiResponse.success(null));
  }
}
