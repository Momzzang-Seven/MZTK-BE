package momzzangseven.mztkbe.modules.web3.execution.application.util;

import java.util.EnumMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.InternalExecutionSignerGates;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionSignerWalletPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.VerifyTreasuryWalletForSignPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

/**
 * Internal issuer signer preflight shared by sponsor-backed and marketplace-admin EIP-1559 paths.
 *
 * <p>Runs before the transactional intent claim. The role choice is supplied by {@link
 * LoadInternalExecutionSignerWalletPort}, so action-specific signer policy remains outside the
 * issuer transaction.
 */
@RequiredArgsConstructor
public class InternalExecutionSignerPreflight {

  private static final String SIGNER_WALLET_MISSING = "internal execution signer key is missing";

  private final LoadInternalExecutionSignerWalletPort loadInternalExecutionSignerWalletPort;
  private final VerifyTreasuryWalletForSignPort verifyTreasuryWalletForSignPort;

  public InternalExecutionSignerGates preflight(List<ExecutionActionType> actionTypes) {
    if (actionTypes == null || actionTypes.isEmpty()) {
      throw new Web3InvalidInputException("internal execution action types required");
    }
    EnumMap<ExecutionActionType, SponsorWalletGate> gates =
        new EnumMap<>(ExecutionActionType.class);
    for (ExecutionActionType actionType : actionTypes) {
      if (actionType == null) {
        throw new Web3InvalidInputException("internal execution action type required");
      }
      gates.computeIfAbsent(actionType, this::preflightAction);
    }
    return new InternalExecutionSignerGates(gates);
  }

  private SponsorWalletGate preflightAction(ExecutionActionType actionType) {
    TreasuryWalletInfo walletInfo =
        loadInternalExecutionSignerWalletPort
            .load(actionType)
            .orElseThrow(() -> new Web3InvalidInputException(SIGNER_WALLET_MISSING));
    if (!walletInfo.active()) {
      throw new Web3InvalidInputException(SIGNER_WALLET_MISSING);
    }
    if (walletInfo.kmsKeyId() == null || walletInfo.kmsKeyId().isBlank()) {
      throw new Web3InvalidInputException(SIGNER_WALLET_MISSING);
    }
    if (walletInfo.walletAddress() == null || walletInfo.walletAddress().isBlank()) {
      throw new Web3InvalidInputException(SIGNER_WALLET_MISSING);
    }
    EvmAddress.of(walletInfo.walletAddress());
    verifyTreasuryWalletForSignPort.verify(walletInfo.walletAlias());
    TreasurySigner signer =
        new TreasurySigner(
            walletInfo.walletAlias(), walletInfo.kmsKeyId(), walletInfo.walletAddress());
    return new SponsorWalletGate(walletInfo, signer);
  }
}
