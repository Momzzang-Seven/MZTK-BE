package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

public record CreateLevelUpRewardTransactionIntentCommand(
    Long userId,
    Long referenceId,
    String idempotencyKey,
    EvmAddress fromAddress,
    EvmAddress toAddress,
    BigInteger amountWei) {}
