package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.ReserveNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.TransferTransactionPersistencePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.TransferTransaction;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.SubmitTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.SubmitTokenTransferResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.SubmitTokenTransferUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702AuthorizationPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferRuntimeConfigPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.TransferPreparePersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.VerifyExecutionSignaturePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.SponsorDailyUsage;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferPrepare;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferRuntimeConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class SubmitTokenTransferService implements SubmitTokenTransferUseCase {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final TransferPreparePersistencePort transferPreparePersistencePort;
  private final TransferTransactionPersistencePort transferTransactionPersistencePort;
  private final SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  private final QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort;

  private final UpdateTransactionPort updateTransactionPort;
  private final RecordTransactionAuditPort recordTransactionAuditPort;

  private final Eip7702ChainPort eip7702ChainPort;
  private final LoadTreasuryKeyPort loadTreasuryKeyPort;
  private final ReserveNoncePort reserveNoncePort;
  private final Web3ContractPort web3ContractPort;

  private final LoadTransferRuntimeConfigPort loadTransferRuntimeConfigPort;
  private final Eip7702AuthorizationPort eip7702AuthorizationPort;
  private final Eip7702TransactionCodecPort eip7702TransactionCodecPort;
  private final VerifyExecutionSignaturePort executionSignatureVerifier;

  private Clock kstClock = Clock.system(KST);

  @Override
  @Transactional
  public SubmitTokenTransferResult execute(SubmitTokenTransferCommand command) {
    validate(command);
    TransferRuntimeConfig runtimeConfig = loadTransferRuntimeConfigPort.load();

    TransferPrepare prepare =
        transferPreparePersistencePort
            .findForUpdateByPrepareId(command.prepareId())
            .orElseThrow(
                () -> new Web3InvalidInputException("prepareId not found: " + command.prepareId()));

    if (!prepare.getFromUserId().equals(command.userId())) {
      throw new Web3InvalidInputException("prepare owner mismatch");
    }

    if (prepare.isSubmittedWithTransaction()) {
      return toResult(loadSubmittedTransaction(prepare.getSubmittedTxId()));
    }

    if (!prepare.isActiveAt(LocalDateTime.now())) {
      transferPreparePersistencePort.update(prepare.expire());
      throw new Web3TransferException(ErrorCode.AUTH_EXPIRED, false);
    }

    assertQuestionRewardIntentSubmittable(prepare);

    TransferTransaction existingByIdempotency =
        transferTransactionPersistencePort
            .findByIdempotencyKey(prepare.getIdempotencyKey())
            .orElse(null);
    if (existingByIdempotency != null) {
      transferPreparePersistencePort.update(prepare.submit(existingByIdempotency.getId()));
      markQuestionRewardIntentSubmittedIfNeeded(prepare);
      return toResult(existingByIdempotency);
    }

    assertDelegateAllowlisted(prepare, runtimeConfig);
    assertAuthorityNonceMatches(prepare);

    LoadTreasuryKeyPort.TreasuryKeyMaterial sponsorKey =
        loadTreasuryKeyPort
            .loadByAlias(
                runtimeConfig.sponsorWalletAlias(), runtimeConfig.sponsorKeyEncryptionKeyB64())
            .orElseThrow(() -> new Web3InvalidInputException("sponsor signer key is missing"));

    String sponsorAddress = EvmAddress.of(sponsorKey.treasuryAddress()).value();

    Eip7702ChainPort.AuthorizationTuple authTuple =
        eip7702AuthorizationPort.toAuthorizationTuple(
            runtimeConfig.chainId(),
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            command.authorizationSignature());

    String transferData =
        eip7702TransactionCodecPort.encodeTransferData(
            prepare.getToAddress(), prepare.getAmountWei());
    List<Eip7702TransactionCodecPort.BatchCall> calls =
        List.of(
            new Eip7702TransactionCodecPort.BatchCall(
                runtimeConfig.tokenContractAddress(),
                BigInteger.ZERO,
                Numeric.hexStringToByteArray(transferData)));
    String callDataHash = eip7702TransactionCodecPort.hashCalls(calls);

    byte[] executionSignatureBytes = Numeric.hexStringToByteArray(command.executionSignature());
    String executeCalldata =
        eip7702TransactionCodecPort.encodeExecute(calls, executionSignatureBytes);

    BigInteger estimatedGas =
        eip7702ChainPort.estimateGasWithAuthorization(
            sponsorAddress, prepare.getAuthorityAddress(), executeCalldata, List.of(authTuple));

    if (estimatedGas == null || estimatedGas.signum() <= 0) {
      throw new Web3InvalidInputException("estimatedGas must be > 0");
    }

    if (estimatedGas.compareTo(BigInteger.valueOf(runtimeConfig.sponsorMaxGasLimit())) > 0) {
      throw new Web3TransferException(ErrorCode.SPONSOR_GAS_LIMIT_EXCEEDED, false);
    }

    Eip7702ChainPort.FeePlan feePlan = eip7702ChainPort.loadSponsorFeePlan();

    BigInteger estimatedCostWei = estimateCostWei(estimatedGas, feePlan.maxFeePerGas());
    DailyUsageSnapshot dailyUsageSnapshot =
        assertSponsorLimits(
            prepare.getFromUserId(), prepare.getAmountWei(), estimatedCostWei, runtimeConfig);

    assertAuthorizationSignature(
        prepare, command.authorizationSignature(), runtimeConfig.chainId());
    assertExecutionSignature(prepare, command.executionSignature(), callDataHash);

    long sponsorNonce = reserveNoncePort.reserveNextNonce(sponsorAddress);

    Eip7702TransactionCodecPort.SignedPayload signedPayload =
        eip7702TransactionCodecPort.signAndEncode(
            new Eip7702TransactionCodecPort.SignCommand(
                runtimeConfig.chainId(),
                BigInteger.valueOf(sponsorNonce),
                feePlan.maxPriorityFeePerGas(),
                feePlan.maxFeePerGas(),
                estimatedGas,
                prepare.getAuthorityAddress(),
                BigInteger.ZERO,
                executeCalldata,
                List.of(authTuple),
                sponsorKey.privateKeyHex()));

    TransferTransaction created =
        transferTransactionPersistencePort.createAndFlush(
            TransferTransaction.builder()
                .idempotencyKey(prepare.getIdempotencyKey())
                .referenceType(toWeb3ReferenceType(prepare.getReferenceType()))
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
        authorizationAuditDetail(prepare));
    audit(
        created.getId(),
        Web3TransactionAuditEventType.LIMIT_CHECK,
        null,
        limitCheckAuditDetail(
            estimatedGas,
            feePlan.maxFeePerGas(),
            estimatedCostWei,
            dailyUsageSnapshot.currentUsedWei(),
            dailyUsageSnapshot.dailyCapWei()));
    auditStateChange(created.getId(), Web3TxStatus.CREATED, Web3TxStatus.SIGNED);

    Web3ContractPort.BroadcastResult broadcast =
        web3ContractPort.broadcast(new Web3ContractPort.BroadcastCommand(signedPayload.rawTx()));
    audit(
        created.getId(),
        Web3TransactionAuditEventType.BROADCAST,
        broadcast.rpcAlias(),
        broadcastAuditDetail(broadcast));

    transferPreparePersistencePort.update(prepare.submit(created.getId()));
    markQuestionRewardIntentSubmittedIfNeeded(prepare);

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
    alertSponsorEthLowIfNeeded(
        created.getId(),
        sponsorAddress,
        reason,
        estimatedGas,
        feePlan.maxFeePerGas(),
        estimatedCostWei);
    updateTransactionPort.scheduleRetry(
        created.getId(),
        reason,
        LocalDateTime.now().plusSeconds(runtimeConfig.retryBackoffSeconds()));

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

  private void assertQuestionRewardIntentSubmittable(TransferPrepare prepare) {
    DomainReferenceType domainType =
        TokenTransferIdempotencyKeyFactory.parseDomainType(prepare.getIdempotencyKey());
    if (domainType != DomainReferenceType.QUESTION_REWARD) {
      return;
    }

    Long postId = parseLongOrNull(prepare.getReferenceId());
    if (postId == null) {
      throw new Web3InvalidInputException("invalid question reward referenceId in prepare");
    }

    QuestionRewardIntent intent =
        questionRewardIntentPersistencePort
            .findForUpdateByPostId(postId)
            .orElseThrow(
                () ->
                    new Web3InvalidInputException(
                        "question reward intent not found for post: " + postId));

    intent.assertSubmittableByPrepare(prepare);
  }

  private void markQuestionRewardIntentSubmittedIfNeeded(TransferPrepare prepare) {
    DomainReferenceType domainType =
        TokenTransferIdempotencyKeyFactory.parseDomainType(prepare.getIdempotencyKey());
    if (domainType != DomainReferenceType.QUESTION_REWARD) {
      return;
    }

    Long postId = parseLongOrNull(prepare.getReferenceId());
    if (postId == null) {
      return;
    }

    questionRewardIntentPersistencePort.updateStatusIfCurrentIn(
        postId,
        QuestionRewardIntentStatus.SUBMITTED,
        EnumSet.of(
            QuestionRewardIntentStatus.PREPARE_REQUIRED,
            QuestionRewardIntentStatus.FAILED_ONCHAIN));
  }

  private Long parseLongOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private Web3ReferenceType toWeb3ReferenceType(TokenTransferReferenceType referenceType) {
    return Web3ReferenceType.valueOf(referenceType.name());
  }

  private void assertDelegateAllowlisted(
      TransferPrepare prepare, TransferRuntimeConfig runtimeConfig) {
    String allowlisted = EvmAddress.of(runtimeConfig.delegationBatchImplAddress()).value();
    String current = EvmAddress.of(prepare.getDelegateTarget()).value();
    if (!allowlisted.equals(current)) {
      throw new Web3TransferException(ErrorCode.DELEGATE_NOT_ALLOWLISTED, false);
    }
  }

  private void assertAuthorityNonceMatches(TransferPrepare prepare) {
    BigInteger onchainNonce =
        eip7702ChainPort.loadPendingAccountNonce(prepare.getAuthorityAddress());
    long expectedNonce = prepare.getAuthorityNonce();
    long currentNonce;
    try {
      currentNonce = onchainNonce.longValueExact();
    } catch (ArithmeticException ex) {
      throw new Web3TransferException(
          ErrorCode.AUTH_NONCE_MISMATCH,
          "authority nonce overflow. expected=" + expectedNonce,
          true);
    }

    if (currentNonce != expectedNonce) {
      throw new Web3TransferException(
          ErrorCode.AUTH_NONCE_MISMATCH,
          "authority nonce mismatch. expected=" + expectedNonce + ", actual=" + currentNonce,
          true);
    }
  }

  private void assertAuthorizationSignature(
      TransferPrepare prepare, String authorizationSignature, long chainId) {
    boolean valid =
        eip7702AuthorizationPort.verifySigner(
            chainId,
            prepare.getDelegateTarget(),
            BigInteger.valueOf(prepare.getAuthorityNonce()),
            authorizationSignature,
            prepare.getAuthorityAddress());
    if (!valid) {
      throw new Web3InvalidInputException("authorizationSignature does not match authority");
    }
  }

  private void assertExecutionSignature(
      TransferPrepare prepare, String executionSignature, String callDataHash) {
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
      Long userId,
      BigInteger amountWei,
      BigInteger estimatedCostWei,
      TransferRuntimeConfig runtimeConfig) {
    BigInteger maxAmountWei = ethToWei(runtimeConfig.sponsorMaxTransferAmountEth());
    if (amountWei.compareTo(maxAmountWei) > 0) {
      throw new Web3TransferException(ErrorCode.SPONSOR_AMOUNT_LIMIT_EXCEEDED, false);
    }

    BigInteger perTxCapWei = ethToWei(runtimeConfig.sponsorPerTxCapEth());
    if (estimatedCostWei.compareTo(perTxCapWei) > 0) {
      throw new Web3TransferException(ErrorCode.SPONSOR_AMOUNT_LIMIT_EXCEEDED, false);
    }

    LocalDate usageDate = LocalDate.now(kstClock);
    SponsorDailyUsage usage =
        sponsorDailyUsagePersistencePort
            .findForUpdate(userId, usageDate)
            .orElseGet(
                () ->
                    SponsorDailyUsage.builder()
                        .userId(userId)
                        .usageDateKst(usageDate)
                        .estimatedCostWei(BigInteger.ZERO)
                        .build());

    BigInteger dailyCapWei = ethToWei(runtimeConfig.sponsorPerDayUserCapEth());
    BigInteger next = usage.getEstimatedCostWei().add(estimatedCostWei);
    if (next.compareTo(dailyCapWei) > 0) {
      throw new Web3TransferException(ErrorCode.SPONSOR_DAILY_LIMIT_EXCEEDED, true);
    }

    return new DailyUsageSnapshot(usage.getEstimatedCostWei(), dailyCapWei);
  }

  private void addDailyUsage(Long userId, BigInteger estimatedCostWei) {
    LocalDate usageDate = LocalDate.now(kstClock);
    SponsorDailyUsage usage =
        sponsorDailyUsagePersistencePort
            .findForUpdate(userId, usageDate)
            .orElseGet(
                () ->
                    sponsorDailyUsagePersistencePort.create(
                        SponsorDailyUsage.builder()
                            .userId(userId)
                            .usageDateKst(usageDate)
                            .estimatedCostWei(BigInteger.ZERO)
                            .build()));

    sponsorDailyUsagePersistencePort.update(usage.addEstimatedCost(estimatedCostWei));
  }

  private BigInteger estimateCostWei(BigInteger estimatedGas, BigInteger maxFeePerGas) {
    BigInteger base = estimatedGas.multiply(maxFeePerGas);
    return base.multiply(BigInteger.valueOf(12)).add(BigInteger.valueOf(9)).divide(BigInteger.TEN);
  }

  private BigInteger ethToWei(BigDecimal eth) {
    return Convert.toWei(eth, Convert.Unit.ETHER).toBigIntegerExact();
  }

  private void alertSponsorEthLowIfNeeded(
      Long txId,
      String sponsorAddress,
      String failureReason,
      BigInteger estimatedGas,
      BigInteger maxFeePerGas,
      BigInteger estimatedCostWei) {
    if (!Web3TxFailureReason.TREASURY_ETH_BELOW_CRITICAL.code().equals(failureReason)) {
      return;
    }

    log.error(
        "WEB3_SPONSOR_ETH_ALERT txId={}, sponsorAddress={}, failureReason={}, estimatedGas={}, maxFeePerGas={}, estimatedCostWei={}, action=retry_scheduled",
        txId,
        sponsorAddress,
        failureReason,
        estimatedGas,
        maxFeePerGas,
        estimatedCostWei);
  }

  private TransferTransaction loadSubmittedTransaction(Long txId) {
    return transferTransactionPersistencePort
        .findById(txId)
        .orElseThrow(
            () -> new Web3InvalidInputException("submitted transaction not found: " + txId));
  }

  private SubmitTokenTransferResult toResult(TransferTransaction tx) {
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
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("from", from.name());
    detail.put("to", to.name());
    audit(txId, Web3TransactionAuditEventType.STATE_CHANGE, null, detail);
  }

  private Map<String, Object> authorizationAuditDetail(TransferPrepare prepare) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("prepareId", prepare.getPrepareId());
    detail.put("payloadHash", prepare.getPayloadHashToSign());
    detail.put("authorityNonce", prepare.getAuthorityNonce());
    detail.put("authorizationExpiresAt", prepare.getAuthExpiresAt());
    return detail;
  }

  private Map<String, Object> limitCheckAuditDetail(
      BigInteger estimatedGas,
      BigInteger maxFeePerGas,
      BigInteger estimatedCostWei,
      BigInteger dailyUsedWei,
      BigInteger dailyCapWei) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("estimatedGas", estimatedGas.toString());
    detail.put("maxFeePerGas", maxFeePerGas.toString());
    detail.put("estimatedCostWei", estimatedCostWei.toString());
    detail.put("dailyUsedWei", dailyUsedWei.toString());
    detail.put("dailyCapWei", dailyCapWei.toString());
    return detail;
  }

  private Map<String, Object> broadcastAuditDetail(Web3ContractPort.BroadcastResult broadcast) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("success", broadcast.success());
    detail.put("txHash", broadcast.txHash());
    detail.put("errorCode", broadcast.failureReason());
    return detail;
  }

  private record DailyUsageSnapshot(BigInteger currentUsedWei, BigInteger dailyCapWei) {}

  void setKstClock(Clock kstClock) {
    this.kstClock = Objects.requireNonNull(kstClock, "kstClock");
  }
}
