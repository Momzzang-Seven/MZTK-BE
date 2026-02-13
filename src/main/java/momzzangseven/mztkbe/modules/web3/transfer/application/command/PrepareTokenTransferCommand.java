package momzzangseven.mztkbe.modules.web3.transfer.application.command;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferReferenceType;

public record PrepareTokenTransferCommand(
    Long userId,
    TokenTransferReferenceType referenceType,
    String referenceId,
    Long toUserId,
    BigInteger amountWei) {}
