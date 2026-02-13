package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3ErrorCode;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.token.infrastructure.config.RewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.AuditDetailBuilder;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionJpaRepository;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.SubmitTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.SubmitTokenTransferResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.SubmitTokenTransferUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.ReserveNoncePort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.VerifyExecutionSignaturePort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferPrepareStatus;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter.Eip1559TransferSigner;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter.Eip7702AuthorizationHelper;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter.Eip7702BatchCallAbi;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter.Eip7702TransactionEncoder;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config.Eip7702Properties;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3SponsorDailyUsageEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3TransferPrepareEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.Web3SponsorDailyUsageJpaRepository;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.Web3TransferPrepareJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class SubmitTokenTransferService implements SubmitTokenTransferUseCase {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final Web3TransferPrepareJpaRepository prepareRepository;
  private final Web3TransactionJpaRepository transactionRepository;
  private final Web3SponsorDailyUsageJpaRepository sponsorDailyUsageRepository;

  private final UpdateTransactionPort updateTransactionPort;
  private final RecordTransactionAuditPort recordTransactionAuditPort;

  private final Eip7702ChainPort eip7702ChainPort;
  private final LoadTreasuryKeyPort loadTreasuryKeyPort;
  private final ReserveNoncePort reserveNoncePort;
  private final Web3ContractPort web3ContractPort;

  private final Eip7702Properties eip7702Properties;
  private final Web3CoreProperties web3CoreProperties;
  private final RewardTokenProperties rewardTokenProperties;
  private final VerifyExecutionSignaturePort executionSignatureVerifier;
  private Clock kstClock = Clock.system(KST);

  @Override
  @Transactional
  public SubmitTokenTransferResult execute(SubmitTokenTransferCommand command) {
    validate(command);

    Web3TransferPrepareEntity prepare =
        prepareRepository
            .findForUpdateByPrepareId(command.prepareId())
            .orElseThrow(
                () -> new Web3InvalidInputException("prepareId not found: " + command.prepareId()));

    if (!prepare.getFromUserId().equals(command.userId())) {
      throw new Web3InvalidInputException("prepare owner mismatch");
    }

    if (prepare.getStatus() == TransferPrepareStatus.SUBMITTED
        && prepare.getSubmittedTxId() != null) {
      return toResult(loadSubmittedTransaction(prepare.getSubmittedTxId()));
    }

    if (!prepare.getAuthExpiresAt().isAfter(LocalDateTime.now())) {
      prepare.setStatus(TransferPrepareStatus.EXPIRED);
      prepareRepository.save(prepare);
      throw new Web3TransferException(Web3ErrorCode.AUTH_EXPIRED, false);
    }

    Web3TransactionEntity existingByReference =
        transactionRepository
            .findByReferenceTypeAndReferenceId(prepare.getReferenceType(), prepare.getReferenceId())
            .orElse(null);
    if (existingByReference != null) {
      throw new Web3TransferException(Web3ErrorCode.IDEMPOTENCY_CONFLICT, false);
    }

    assertDelegateAllowlisted(prepare);
    assertAuthorityNonceMatches(prepare);

    LoadTreasuryKeyPort.TreasuryKeyMaterial sponsorKey =
        loadTreasuryKeyPort
            .load()
            .orElseThrow(() -> new Web3InvalidInputException("sponsor signer key is missing"));

    String sponsorAddress = EvmAddress.of(sponsorKey.treasuryAddress()).value();

    Eip7702ChainPort.AuthorizationTuple authTuple =
        Eip7702AuthorizationHelper.toAuthorizationTuple(
            web3CoreProperties.getChainId(),
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            command.authorizationSignature());

    String transferData =
        Eip1559TransferSigner.encodeTransferData(prepare.getToAddress(), prepare.getAmountWei());
    List<Eip7702BatchCallAbi.Call> calls =
        List.of(
            new Eip7702BatchCallAbi.Call(
                rewardTokenProperties.getTokenContractAddress(),
                BigInteger.ZERO,
                Numeric.hexStringToByteArray(transferData)));
    String callDataHash = Eip7702BatchCallAbi.hashCalls(calls);

    byte[] executionSignatureBytes = Numeric.hexStringToByteArray(command.executionSignature());

    String executeCalldata = Eip7702BatchCallAbi.encodeExecute(calls, executionSignatureBytes);

    BigInteger estimatedGas =
        eip7702ChainPort.estimateGasWithAuthorization(
            sponsorAddress, prepare.getAuthorityAddress(), executeCalldata, List.of(authTuple));

    if (estimatedGas == null || estimatedGas.signum() <= 0) {
      throw new Web3InvalidInputException("estimatedGas must be > 0");
    }

    if (estimatedGas.compareTo(BigInteger.valueOf(eip7702Properties.getSponsor().getMaxGasLimit()))
        > 0) {
      throw new Web3TransferException(Web3ErrorCode.SPONSOR_GAS_LIMIT_EXCEEDED, false);
    }

    Eip7702ChainPort.FeePlan feePlan = eip7702ChainPort.loadSponsorFeePlan();

    BigInteger estimatedCostWei = estimateCostWei(estimatedGas, feePlan.maxFeePerGas());
    DailyUsageSnapshot dailyUsageSnapshot =
        assertSponsorLimits(prepare.getFromUserId(), prepare.getAmountWei(), estimatedCostWei);

    assertAuthorizationSignature(prepare, command.authorizationSignature());
    assertExecutionSignature(
        prepare, command.executionSignature(), callDataHash);

    long sponsorNonce = reserveNoncePort.reserveNextNonce(sponsorAddress);

    Eip7702TransactionEncoder.SignedPayload signedPayload =
        Eip7702TransactionEncoder.signAndEncode(
            web3CoreProperties.getChainId(),
            BigInteger.valueOf(sponsorNonce),
            feePlan.maxPriorityFeePerGas(),
            feePlan.maxFeePerGas(),
            estimatedGas,
            prepare.getAuthorityAddress(),
            BigInteger.ZERO,
            executeCalldata,
            List.of(authTuple),
            sponsorKey.privateKeyHex());

    Web3TransactionEntity created =
        transactionRepository.saveAndFlush(
            Web3TransactionEntity.builder()
                .idempotencyKey(prepare.getIdempotencyKey())
                .referenceType(prepare.getReferenceType())
                .referenceId(prepare.getReferenceId())
                .fromUserId(prepare.getFromUserId())
                .toUserId(prepare.getToUserId())
                .fromAddress(sponsorAddress)
                .toAddress(prepare.getAuthorityAddress())
                .amountWei(prepare.getAmountWei())
                .status(Web3TxStatus.CREATED)
                .txType(Web3TxType.EIP7702)
                .authorityAddress(prepare.getAuthorityAddress())
                .authorizationNonce(prepare.getAuthorityNonce())
                .delegateTarget(prepare.getDelegateTarget())
                .authorizationExpiresAt(prepare.getAuthExpiresAt())
                .build());

    updateTransactionPort.markSigned(
        created.getId(), sponsorNonce, signedPayload.rawTx(), signedPayload.txHash());

    audit(
        created.getId(),
        Web3TransactionAuditEventType.AUTHORIZATION,
        null,
        AuditDetailBuilder.create()
            .put("prepareId", prepare.getPrepareId())
            .put("payloadHash", prepare.getPayloadHashToSign())
            .put("authorityNonce", prepare.getAuthorityNonce())
            .put("authorizationExpiresAt", prepare.getAuthExpiresAt())
            .build());
    audit(
        created.getId(),
        Web3TransactionAuditEventType.LIMIT_CHECK,
        null,
        AuditDetailBuilder.create()
            .put("estimatedGas", estimatedGas)
            .put("maxFeePerGas", feePlan.maxFeePerGas())
            .put("estimatedCostWei", estimatedCostWei)
            .put("dailyUsedWei", dailyUsageSnapshot.currentUsedWei())
            .put("dailyCapWei", dailyUsageSnapshot.dailyCapWei())
            .build());
    auditStateChange(created.getId(), Web3TxStatus.CREATED, Web3TxStatus.SIGNED);

    Web3ContractPort.BroadcastResult broadcast =
        web3ContractPort.broadcast(new Web3ContractPort.BroadcastCommand(signedPayload.rawTx()));
    audit(
        created.getId(),
        Web3TransactionAuditEventType.BROADCAST,
        broadcast.rpcAlias(),
        AuditDetailBuilder.create()
            .put("success", broadcast.success())
            .put("txHash", broadcast.txHash())
            .put("errorCode", broadcast.failureReason())
            .build());

    prepare.setStatus(TransferPrepareStatus.SUBMITTED);
    prepare.setSubmittedTxId(created.getId());
    prepareRepository.save(prepare);

    if (broadcast.success()) {
      String txHash =
          broadcast.txHash() == null || broadcast.txHash().isBlank()
              ? signedPayload.txHash()
              : broadcast.txHash();
      updateTransactionPort.markPending(created.getId(), txHash);
      auditStateChange(created.getId(), Web3TxStatus.SIGNED, Web3TxStatus.PENDING);
      addDailyUsage(prepare.getFromUserId(), estimatedCostWei);
      return SubmitTokenTransferResult.builder()
          .transactionId(created.getId())
          .status(Web3TxStatus.PENDING.name())
          .txHash(txHash)
          .build();
    }

    String reason =
        broadcast.failureReason() == null || broadcast.failureReason().isBlank()
            ? Web3TxFailureReason.BROADCAST_FAILED.code()
            : broadcast.failureReason();
    updateTransactionPort.scheduleRetry(
        created.getId(),
        reason,
        LocalDateTime.now()
            .plusSeconds(rewardTokenProperties.getWorker().getRetryBackoffSeconds()));

    return SubmitTokenTransferResult.builder()
        .transactionId(created.getId())
        .status(Web3TxStatus.SIGNED.name())
        .txHash(signedPayload.txHash())
        .build();
  }

  private void validate(SubmitTokenTransferCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
  }

  private void assertDelegateAllowlisted(Web3TransferPrepareEntity prepare) {
    String allowlisted =
        EvmAddress.of(eip7702Properties.getDelegation().getBatchImplAddress()).value();
    String current = EvmAddress.of(prepare.getDelegateTarget()).value();
    if (!allowlisted.equals(current)) {
      throw new Web3TransferException(Web3ErrorCode.DELEGATE_NOT_ALLOWLISTED, false);
    }
  }

  private void assertAuthorityNonceMatches(Web3TransferPrepareEntity prepare) {
    BigInteger onchainNonce =
        eip7702ChainPort.loadPendingAccountNonce(prepare.getAuthorityAddress());
    long expectedNonce = prepare.getAuthorityNonce();
    long currentNonce;
    try {
      currentNonce = onchainNonce.longValueExact();
    } catch (ArithmeticException ex) {
      throw new Web3TransferException(
          Web3ErrorCode.AUTH_NONCE_MISMATCH,
          "authority nonce overflow. expected=" + expectedNonce,
          true);
    }

    if (currentNonce != expectedNonce) {
      throw new Web3TransferException(
          Web3ErrorCode.AUTH_NONCE_MISMATCH,
          "authority nonce mismatch. expected=" + expectedNonce + ", actual=" + currentNonce,
          true);
    }
  }

  private void assertAuthorizationSignature(
      Web3TransferPrepareEntity prepare, String authorizationSignature) {
    boolean valid =
        Eip7702AuthorizationHelper.verifySigner(
            web3CoreProperties.getChainId(),
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature,
            prepare.getAuthorityAddress());
    if (!valid) {
      throw new Web3InvalidInputException("authorizationSignature does not match authority");
    }
  }

  private void assertExecutionSignature(
      Web3TransferPrepareEntity prepare,
      String executionSignature,
      String callDataHash) {
    BigInteger deadlineEpochSeconds =
        BigInteger.valueOf(prepare.getAuthExpiresAt().toEpochSecond(ZoneOffset.UTC));
    boolean valid =
        executionSignatureVerifier.verify(
            prepare.getAuthorityAddress(),
            prepare.getPrepareId(),
            callDataHash,
            deadlineEpochSeconds,
            executionSignature);
    if (!valid) {
      throw new Web3InvalidInputException("executionSignature does not match authority");
    }
  }

  private DailyUsageSnapshot assertSponsorLimits(
      Long userId, BigInteger amountWei, BigInteger estimatedCostWei) {
    BigInteger maxAmountWei = BigInteger.valueOf(5_000L).multiply(BigInteger.TEN.pow(18));
    if (amountWei.compareTo(maxAmountWei) > 0) {
      throw new Web3TransferException(Web3ErrorCode.SPONSOR_AMOUNT_LIMIT_EXCEEDED, false);
    }

    BigInteger perTxCapWei = ethToWei(eip7702Properties.getSponsor().getPerTxCapEth());
    if (estimatedCostWei.compareTo(perTxCapWei) > 0) {
      throw new Web3TransferException(Web3ErrorCode.SPONSOR_AMOUNT_LIMIT_EXCEEDED, false);
    }

    LocalDate usageDate = LocalDate.now(kstClock);
    Web3SponsorDailyUsageEntity usage =
        sponsorDailyUsageRepository
            .findForUpdate(userId, usageDate)
            .orElseGet(
                () ->
                    Web3SponsorDailyUsageEntity.builder()
                        .userId(userId)
                        .usageDateKst(usageDate)
                        .estimatedCostWei(BigInteger.ZERO)
                        .build());

    BigInteger dailyCapWei = ethToWei(eip7702Properties.getSponsor().getPerDayUserCapEth());
    BigInteger next = usage.getEstimatedCostWei().add(estimatedCostWei);
    if (next.compareTo(dailyCapWei) > 0) {
      throw new Web3TransferException(Web3ErrorCode.SPONSOR_DAILY_LIMIT_EXCEEDED, true);
    }

    return new DailyUsageSnapshot(usage.getEstimatedCostWei(), dailyCapWei);
  }

  private void addDailyUsage(Long userId, BigInteger estimatedCostWei) {
    LocalDate usageDate = LocalDate.now(kstClock);
    Web3SponsorDailyUsageEntity usage =
        sponsorDailyUsageRepository
            .findForUpdate(userId, usageDate)
            .orElseGet(
                () ->
                    Web3SponsorDailyUsageEntity.builder()
                        .userId(userId)
                        .usageDateKst(usageDate)
                        .estimatedCostWei(BigInteger.ZERO)
                        .build());

    usage.setEstimatedCostWei(usage.getEstimatedCostWei().add(estimatedCostWei));
    sponsorDailyUsageRepository.save(usage);
  }

  private BigInteger estimateCostWei(BigInteger estimatedGas, BigInteger maxFeePerGas) {
    BigInteger base = estimatedGas.multiply(maxFeePerGas);
    return base.multiply(BigInteger.valueOf(12)).add(BigInteger.valueOf(9)).divide(BigInteger.TEN);
  }

  private BigInteger ethToWei(BigDecimal eth) {
    return Convert.toWei(eth, Convert.Unit.ETHER).toBigIntegerExact();
  }

  private Web3TransactionEntity loadSubmittedTransaction(Long txId) {
    return transactionRepository
        .findById(txId)
        .orElseThrow(
            () -> new Web3InvalidInputException("submitted transaction not found: " + txId));
  }

  private SubmitTokenTransferResult toResult(Web3TransactionEntity tx) {
    return SubmitTokenTransferResult.builder()
        .transactionId(tx.getId())
        .status(tx.getStatus().name())
        .txHash(tx.getTxHash())
        .build();
  }

  private void audit(
      Long txId,
      Web3TransactionAuditEventType eventType,
      String rpcAlias,
      Map<String, Object> detail) {
    recordTransactionAuditPort.record(
        new RecordTransactionAuditPort.AuditCommand(txId, eventType, rpcAlias, detail));
  }

  private void auditStateChange(Long txId, Web3TxStatus from, Web3TxStatus to) {
    audit(
        txId,
        Web3TransactionAuditEventType.STATE_CHANGE,
        null,
        AuditDetailBuilder.create().put("from", from).put("to", to).build());
  }

  private record DailyUsageSnapshot(BigInteger currentUsedWei, BigInteger dailyCapWei) {}

  void setKstClock(Clock kstClock) {
    this.kstClock = Objects.requireNonNull(kstClock, "kstClock");
  }
}
