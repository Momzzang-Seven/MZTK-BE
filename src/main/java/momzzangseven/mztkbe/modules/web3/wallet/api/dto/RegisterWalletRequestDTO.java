package momzzangseven.mztkbe.modules.web3.wallet.api.dto;

import jakarta.validation.constraints.NotBlank;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletCommand;

/** Request DTO for wallet registration */
public record RegisterWalletRequestDTO(
    @NotBlank(message = "Wallet address is required") String walletAddress,
    @NotBlank(message = "Signature is required") String signature,
    @NotBlank(message = "Nonce is required") String nonce) {

  /** Converts this request into an application command. */
  public RegisterWalletCommand toCommand(Long userId) {
    return new RegisterWalletCommand(userId, walletAddress, signature, nonce);
  }
}
