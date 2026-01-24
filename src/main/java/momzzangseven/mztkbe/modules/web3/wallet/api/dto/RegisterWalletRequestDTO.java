package momzzangseven.mztkbe.modules.web3.wallet.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Request DTO for wallet registration */
public record RegisterWalletRequestDTO(
    @NotBlank(message = "Wallet address is required") String walletAddress,
    @NotBlank(message = "Signature is required") String signature,
    @NotBlank(message = "Nonce is required") String nonce) {}
