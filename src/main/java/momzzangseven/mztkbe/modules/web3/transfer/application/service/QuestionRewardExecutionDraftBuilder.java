package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.wallet.WalletNotConnectedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraft;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferRuntimeConfigPort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferRuntimeConfig;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.utils.Numeric;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class QuestionRewardExecutionDraftBuilder {

  private final LoadWalletPort loadWalletPort;
  private final LoadTransferRuntimeConfigPort loadTransferRuntimeConfigPort;
  private final Eip7702ChainPort eip7702ChainPort;
  private final Eip7702AuthorizationPort eip7702AuthorizationPort;
  private final Eip7702TransactionCodecPort eip7702TransactionCodecPort;
  private final Web3ContractPort web3ContractPort;
  private final Eip1559TransactionCodecPort eip1559TransactionCodecPort;
  private final ObjectMapper objectMapper;

  public ExecutionDraft build(RegisterQuestionRewardIntentCommand command) {
    command.validate();

    TransferRuntimeConfig runtimeConfig = loadTransferRuntimeConfigPort.load();
    String authorityAddress = resolveActiveWalletAddress(command.fromUserId(), "question owner");
    String toAddress = resolveActiveWalletAddress(command.toUserId(), "answer writer");
    long authorityNonce = resolveAuthorityNonce(authorityAddress);
    String delegateTarget = EvmAddress.of(runtimeConfig.delegationBatchImplAddress()).value();
    String authorizationPayloadHash =
        eip7702AuthorizationPort.buildSigningHashHex(
            runtimeConfig.chainId(), delegateTarget, BigInteger.valueOf(authorityNonce));

    String transferData =
        eip7702TransactionCodecPort.encodeTransferData(toAddress, command.amountWei());
    ExecutionDraftCall transferCall =
        new ExecutionDraftCall(runtimeConfig.tokenContractAddress(), BigInteger.ZERO, transferData);

    UnsignedTxSnapshot unsignedTxSnapshot =
        buildUnsignedTxSnapshot(
            runtimeConfig.chainId(),
            runtimeConfig.tokenContractAddress(),
            authorityAddress,
            toAddress,
            command.amountWei(),
            transferData);

    QuestionRewardExecutionPayload payload =
        new QuestionRewardExecutionPayload(
            command.postId(),
            command.acceptedCommentId(),
            command.fromUserId(),
            command.toUserId(),
            authorityAddress,
            toAddress,
            runtimeConfig.tokenContractAddress(),
            command.amountWei());

    return new ExecutionDraft(
        ExecutionResourceType.QUESTION,
        String.valueOf(command.postId()),
        "PENDING_EXECUTION",
        ExecutionActionType.QNA_ANSWER_ACCEPT,
        command.fromUserId(),
        command.toUserId(),
        TokenTransferIdempotencyKeyFactory.create(
            DomainReferenceType.QUESTION_REWARD, command.fromUserId(), command.referenceId()),
        payloadHash(payload),
        writePayload(payload),
        java.util.List.of(transferCall),
        true,
        authorityAddress,
        authorityNonce,
        delegateTarget,
        authorizationPayloadHash,
        unsignedTxSnapshot,
        eip1559TransactionCodecPort.computeFingerprint(unsignedTxSnapshot),
        LocalDateTime.now().plusSeconds(runtimeConfig.authorizationTtlSeconds()));
  }

  private UnsignedTxSnapshot buildUnsignedTxSnapshot(
      long chainId,
      String tokenContractAddress,
      String authorityAddress,
      String toAddress,
      BigInteger amountWei,
      String transferData) {
    Web3ContractPort.PrevalidateResult prevalidateResult =
        web3ContractPort.prevalidate(
            new Web3ContractPort.PrevalidateCommand(authorityAddress, toAddress, amountWei));
    if (!prevalidateResult.ok()) {
      throw new Web3InvalidInputException(
          "failed to prepare question reward EIP-1559 fallback: "
              + (prevalidateResult.failureReason() == null
                  ? "PREVALIDATE_FAILED"
                  : prevalidateResult.failureReason()));
    }

    BigInteger pendingNonce = eip7702ChainPort.loadPendingAccountNonce(authorityAddress);
    return new UnsignedTxSnapshot(
        chainId,
        authorityAddress,
        tokenContractAddress,
        BigInteger.ZERO,
        transferData,
        pendingNonce.longValueExact(),
        prevalidateResult.gasLimit(),
        prevalidateResult.maxPriorityFeePerGas(),
        prevalidateResult.maxFeePerGas());
  }

  private String resolveActiveWalletAddress(Long userId, String role) {
    java.util.List<UserWallet> activeWallets =
        loadWalletPort.findWalletsByUserIdAndStatus(userId, WalletStatus.ACTIVE);
    if (activeWallets.isEmpty()) {
      throw new WalletNotConnectedException(userId);
    }
    return EvmAddress.of(activeWallets.getFirst().getWalletAddress()).value();
  }

  private long resolveAuthorityNonce(String authorityAddress) {
    try {
      return eip7702ChainPort.loadPendingAccountNonce(authorityAddress).longValueExact();
    } catch (ArithmeticException ex) {
      throw new Web3InvalidInputException("authority nonce overflow");
    }
  }

  private String writePayload(QuestionRewardExecutionPayload payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize question reward execution payload", e);
    }
  }

  private String payloadHash(QuestionRewardExecutionPayload payload) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(writePayload(payload).getBytes());
      return Numeric.toHexString(digest);
    } catch (Exception e) {
      throw new IllegalStateException("failed to hash question reward execution payload", e);
    }
  }
}
