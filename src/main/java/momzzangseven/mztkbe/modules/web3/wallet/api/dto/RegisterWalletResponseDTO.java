package momzzangseven.mztkbe.modules.web3.wallet.api.dto;

import java.time.Instant;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletResult;

public record RegisterWalletResponseDTO(Long id, String walletAddress, Instant registeredAt) {
  public static RegisterWalletResponseDTO from(RegisterWalletResult result) {
    return new RegisterWalletResponseDTO(
        result.walletId(), result.walletAddress(), result.registeredAt());
  }
}
