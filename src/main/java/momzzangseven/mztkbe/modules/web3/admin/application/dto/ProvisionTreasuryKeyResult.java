package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWalletStatus;

public record ProvisionTreasuryKeyResult(
    String walletAlias,
    TreasuryRole role,
    String kmsKeyId,
    String walletAddress,
    TreasuryWalletStatus status,
    TreasuryKeyOrigin keyOrigin,
    LocalDateTime createdAt) {}
