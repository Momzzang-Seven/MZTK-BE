package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.wallet.WalletNotConnectedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.PrepareTokenTransferExecutionSupportCommand;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.PrepareTokenTransferExecutionSupportResult;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.in.PrepareTokenTransferExecutionSupportUseCase;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.PrepareTokenTransferPrevalidationCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.PrepareTokenTransferPrevalidationResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.PrepareTokenTransferPrevalidationUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CreateTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionDraft;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionPayload;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferUnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.BuildTransferExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferRuntimeConfigPort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferRootIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferRuntimeConfig;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetActiveWalletAddressUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class TransferExecutionDraftBuilderAdapter implements BuildTransferExecutionDraftPort {

  private final GetActiveWalletAddressUseCase getActiveWalletAddressUseCase;
  private final LoadTransferRuntimeConfigPort loadTransferRuntimeConfigPort;
  private final PrepareTokenTransferExecutionSupportUseCase
      prepareTokenTransferExecutionSupportUseCase;
  private final PrepareTokenTransferPrevalidationUseCase prepareTokenTransferPrevalidationUseCase;
  private final ExecutionPayloadSerializer executionPayloadSerializer;
  private final TransferUnsignedTxFingerprintFactory transferUnsignedTxFingerprintFactory;
  private final Clock appClock;

  @Override
  public TransferExecutionDraft build(CreateTransferCommand command) {
    command.validate();

    TransferRuntimeConfig runtimeConfig = loadTransferRuntimeConfigPort.load();
    String authorityAddress = resolveActiveWalletAddress(command.userId(), "requester");
    String toAddress = resolveActiveWalletAddress(command.toUserId(), "recipient");
    String delegateTarget = EvmAddress.of(runtimeConfig.delegationBatchImplAddress()).value();

    PrepareTokenTransferExecutionSupportResult executionSupport =
        prepareTokenTransferExecutionSupportUseCase.execute(
            new PrepareTokenTransferExecutionSupportCommand(
                runtimeConfig.chainId(),
                delegateTarget,
                authorityAddress,
                toAddress,
                command.amountWei()));

    TransferExecutionDraftCall transferCall =
        new TransferExecutionDraftCall(
            runtimeConfig.tokenContractAddress(), BigInteger.ZERO, executionSupport.transferData());

    TransferUnsignedTxSnapshot unsignedTxSnapshot =
        buildUnsignedTxSnapshot(
            runtimeConfig.chainId(),
            runtimeConfig.tokenContractAddress(),
            authorityAddress,
            toAddress,
            command.amountWei(),
            executionSupport.transferData(),
            executionSupport.authorityNonce());

    TransferExecutionPayload payload =
        new TransferExecutionPayload(
            command.clientRequestId(),
            command.userId(),
            command.toUserId(),
            authorityAddress,
            toAddress,
            runtimeConfig.tokenContractAddress(),
            command.amountWei(),
            executionSupport.transferData());

    String resourceId =
        TransferRootIdempotencyKeyFactory.create(command.userId(), command.clientRequestId());

    return new TransferExecutionDraft(
        "TRANSFER",
        resourceId,
        "PENDING_EXECUTION",
        "TRANSFER_SEND",
        command.userId(),
        command.toUserId(),
        resourceId,
        executionPayloadSerializer.hashHex(payload),
        executionPayloadSerializer.serialize(payload),
        java.util.List.of(transferCall),
        true,
        authorityAddress,
        executionSupport.authorityNonce(),
        delegateTarget,
        executionSupport.authorizationPayloadHash(),
        unsignedTxSnapshot,
        transferUnsignedTxFingerprintFactory.compute(unsignedTxSnapshot),
        LocalDateTime.now(appClock).plusSeconds(runtimeConfig.authorizationTtlSeconds()));
  }

  private TransferUnsignedTxSnapshot buildUnsignedTxSnapshot(
      long chainId,
      String tokenContractAddress,
      String authorityAddress,
      String toAddress,
      BigInteger amountWei,
      String transferData,
      long expectedNonce) {
    PrepareTokenTransferPrevalidationResult prevalidation =
        prepareTokenTransferPrevalidationUseCase.execute(
            new PrepareTokenTransferPrevalidationCommand(authorityAddress, toAddress, amountWei));
    if (!prevalidation.ok()) {
      throw new Web3InvalidInputException(
          "failed to prepare EIP-1559 fallback: "
              + (prevalidation.failureReason() == null
                  ? "PREVALIDATE_FAILED"
                  : prevalidation.failureReason()));
    }

    return new TransferUnsignedTxSnapshot(
        chainId,
        authorityAddress,
        tokenContractAddress,
        BigInteger.ZERO,
        transferData,
        expectedNonce,
        prevalidation.gasLimit(),
        prevalidation.maxPriorityFeePerGas(),
        prevalidation.maxFeePerGas());
  }

  private String resolveActiveWalletAddress(Long userId, String role) {
    return getActiveWalletAddressUseCase
        .execute(userId)
        .map(address -> EvmAddress.of(address).value())
        .orElseThrow(
            () -> {
              if ("requester".equals(role)) {
                return new WalletNotConnectedException(userId);
              }
              return new Web3InvalidInputException(role + " user has no ACTIVE wallet");
            });
  }
}
