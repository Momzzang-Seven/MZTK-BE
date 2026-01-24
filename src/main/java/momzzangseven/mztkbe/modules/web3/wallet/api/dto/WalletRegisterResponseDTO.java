package momzzangseven.mztkbe.modules.web3.wallet.api.dto;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletResult;

public record WalletRegisterResponseDTO(
        Long id,
        String walletAddress,
        String registeredAt
) {
    public static WalletRegisterResponseDTO from(RegisterWalletResult result) {
        return new WalletRegisterResponseDTO(
                result.walletId(),
                result.walletAddress(),
                result.registeredAt().toString()
        );
    }
}
