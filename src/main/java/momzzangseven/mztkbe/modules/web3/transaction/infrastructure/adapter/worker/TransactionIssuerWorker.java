package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.error.web3.KmsKeyDescribeFailedException;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import momzzangseven.mztkbe.global.error.web3.SignatureRecoveryException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadRewardTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionWorkPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RecordTransactionAuditPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.ReserveNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.VerifyTreasuryWalletForSignPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.BroadcastAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.PrevalidateAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.SignAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail.StateChangeAuditDetail;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.strategy.KmsClientErrorClassifier;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.strategy.RetryStrategy;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.TransactionRewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class TransactionIssuerWorker extends AbstractWeb3Worker {

  private final LoadRewardTreasuryWalletPort loadRewardTreasuryWalletPort;
  private final VerifyTreasuryWalletForSignPort verifyTreasuryWalletForSignPort;
  private final ReserveNoncePort reserveNoncePort;
  private final Web3ContractPort web3ContractPort;
  private final Web3CoreProperties web3CoreProperties;

  private final String workerId = "issuer-" + UUID.randomUUID().toString().substring(0, 8);

  public TransactionIssuerWorker(
      LoadTransactionWorkPort loadTransactionWorkPort,
      UpdateTransactionPort updateTransactionPort,
      RecordTransactionAuditPort recordTransactionAuditPort,
      LoadRewardTreasuryWalletPort loadRewardTreasuryWalletPort,
      VerifyTreasuryWalletForSignPort verifyTreasuryWalletForSignPort,
      ReserveNoncePort reserveNoncePort,
      Web3ContractPort web3ContractPort,
      TransactionRewardTokenProperties rewardTokenProperties,
      RetryStrategy retryStrategy,
      Web3CoreProperties web3CoreProperties) {
    super(
        loadTransactionWorkPort,
        updateTransactionPort,
        recordTransactionAuditPort,
        rewardTokenProperties,
        retryStrategy);
    this.loadRewardTreasuryWalletPort = loadRewardTreasuryWalletPort;
    this.verifyTreasuryWalletForSignPort = verifyTreasuryWalletForSignPort;
    this.reserveNoncePort = reserveNoncePort;
    this.web3ContractPort = web3ContractPort;
    this.web3CoreProperties = web3CoreProperties;
  }

  @Scheduled(fixedDelay = 1000L)
  public void run() {
    processBatch(20);
  }

  void processBatch(int limit) {
    processBatchByStatus(
        Web3TxStatus.CREATED,
        limit,
        workerId,
        claimTtlSeconds(),
        Web3TxFailureReason.RPC_UNAVAILABLE.code(),
        this::processBatchItems);
  }

  void processBatchItems(List<LoadTransactionWorkPort.TransactionWorkItem> items) {
    Optional<TreasuryWalletInfo> walletOpt = loadRewardTreasuryWalletPort.load();
    if (walletOpt.isEmpty()) {
      failBatch(items, Web3TxFailureReason.TREASURY_KEY_MISSING);
      return;
    }
    TreasuryWalletInfo walletInfo = walletOpt.get();

    if (!walletInfo.active()) {
      failBatch(items, Web3TxFailureReason.TREASURY_WALLET_INACTIVE);
      return;
    }
    // Structural guards (cheap, local) precede the remote verify call so that incomplete wallet
    // rows fail fast with TREASURY_KEY_MISSING instead of leaking into TreasurySigner's compact
    // constructor (which would otherwise raise Web3InvalidInputException and abort the entire
    // batch with no per-item audit trail). The address is checked for both blank AND format —
    // a malformed-but-non-blank address would otherwise pass these guards and reach
    // EvmAddress.of() inside TreasurySigner, propagating outside forEachItem's per-item catch.
    if (walletInfo.kmsKeyId() == null || walletInfo.kmsKeyId().isBlank()) {
      failBatch(items, Web3TxFailureReason.TREASURY_KEY_MISSING);
      return;
    }
    if (walletInfo.walletAddress() == null || walletInfo.walletAddress().isBlank()) {
      failBatch(items, Web3TxFailureReason.TREASURY_KEY_MISSING);
      return;
    }
    try {
      EvmAddress.of(walletInfo.walletAddress());
    } catch (Web3InvalidInputException e) {
      log.warn(
          "Treasury wallet '{}' has malformed walletAddress: {}",
          walletInfo.walletAlias(),
          e.getMessage());
      failBatch(items, Web3TxFailureReason.TREASURY_KEY_MISSING);
      return;
    }

    try {
      verifyTreasuryWalletForSignPort.verify(walletInfo.walletAlias());
    } catch (TreasuryWalletStateException | KmsKeyDescribeFailedException e) {
      log.warn(
          "Treasury wallet '{}' verify-for-sign failed: {}",
          walletInfo.walletAlias(),
          e.getMessage());
      failBatch(items, Web3TxFailureReason.KMS_KEY_NOT_ENABLED);
      return;
    }

    TreasurySigner signer =
        new TreasurySigner(
            walletInfo.walletAlias(), walletInfo.kmsKeyId(), walletInfo.walletAddress());

    forEachItem(
        items, item -> processItem(item, signer), Web3TxFailureReason.RPC_UNAVAILABLE.code());
  }

  // Routes batch-wide failures through retry/terminal based on the failure reason's retryable flag.
  // Critical for retryable reasons (e.g. KMS_KEY_NOT_ENABLED) — a terminal write would leave
  // processing_until=null while the SQL claim filter only excludes non-retryable reasons,
  // letting the same rows be re-claimed every worker tick and hammer KMS until manual intervention.
  private void failBatch(
      List<LoadTransactionWorkPort.TransactionWorkItem> items, Web3TxFailureReason reason) {
    if (reason.isRetryable()) {
      items.forEach(item -> retry(item.transactionId(), reason.code(), item));
    } else {
      items.forEach(item -> failPrevalidate(item.transactionId(), reason.code(), false));
    }
  }

  private void processItem(
      LoadTransactionWorkPort.TransactionWorkItem item, TreasurySigner signer) {
    // Treasury rotation guard: a CREATED row carries the from_address it was minted with.
    // After a treasury rotation, the active signer can be a different wallet — proceeding would
    // sign with a wallet that does not match item.fromAddress() and the broadcast would silently
    // fail on-chain (or worse, drift state). Cheaper than prevalidate's RPC round-trip, so this
    // guard runs first.
    if (!isFromAddressMatch(item.fromAddress(), signer.walletAddress())) {
      log.warn(
          "fromAddress mismatch: txId={}, expected={}, signer={}",
          item.transactionId(),
          item.fromAddress(),
          signer.walletAddress());
      failPrevalidate(
          item.transactionId(), Web3TxFailureReason.FROM_ADDRESS_MISMATCH.code(), false);
      return;
    }

    Web3ContractPort.PrevalidateResult prevalidateResult =
        web3ContractPort.prevalidate(
            new Web3ContractPort.PrevalidateCommand(
                signer.walletAddress(), item.toAddress(), item.amountWei()));

    Map<String, Object> prevalidateDetail =
        new PrevalidateAuditDetail(
                prevalidateResult.detail(),
                prevalidateResult.ok(),
                prevalidateResult.failureReason())
            .toMap();
    audit(item.transactionId(), Web3TransactionAuditEventType.PREVALIDATE, null, prevalidateDetail);

    if (!prevalidateResult.ok()) {
      failPrevalidate(
          item.transactionId(), prevalidateResult.failureReason(), prevalidateResult.retryable());
      return;
    }

    long nonce = resolveNonce(item, signer.walletAddress());
    Web3ContractPort.SignedTransaction signed;
    try {
      signed =
          web3ContractPort.signTransfer(
              new Web3ContractPort.SignTransferCommand(
                  signer,
                  rewardTokenProperties.getTokenContractAddress(),
                  item.toAddress(),
                  item.amountWei(),
                  nonce,
                  web3CoreProperties.getChainId(),
                  prevalidateResult.gasLimit(),
                  prevalidateResult.maxPriorityFeePerGas(),
                  prevalidateResult.maxFeePerGas()));
    } catch (KmsSignFailedException e) {
      log.warn("KMS sign failed for txId={}: {}", item.transactionId(), e.getMessage());
      if (KmsClientErrorClassifier.isTerminal(e)) {
        // unrecoverable AWS condition (IAM deny, disabled key, ...) — release the reserved nonce
        // and terminal-fail. Without the release, the gap reproduces #2 even though the worker
        // never broadcast.
        releaseReservedNonceQuietly(item, signer.walletAddress(), nonce);
        failPrevalidate(
            item.transactionId(), Web3TxFailureReason.KMS_SIGN_FAILED_TERMINAL.code(), false);
      } else {
        retry(item.transactionId(), Web3TxFailureReason.KMS_SIGN_FAILED.code(), item);
      }
      return;
    } catch (SignatureRecoveryException e) {
      log.warn("Signature recovery failed for txId={}: {}", item.transactionId(), e.getMessage());
      releaseReservedNonceQuietly(item, signer.walletAddress(), nonce);
      failPrevalidate(item.transactionId(), Web3TxFailureReason.SIGNATURE_INVALID.code(), false);
      return;
    }

    updateTransactionPort.markSigned(item.transactionId(), nonce, signed.rawTx(), signed.txHash());
    Map<String, Object> signDetail = new SignAuditDetail(nonce, signed.txHash()).toMap();
    audit(item.transactionId(), Web3TransactionAuditEventType.SIGN, null, signDetail);
    auditStateChange(item.transactionId(), Web3TxStatus.CREATED, Web3TxStatus.SIGNED);

    Web3ContractPort.BroadcastResult broadcast =
        web3ContractPort.broadcast(new Web3ContractPort.BroadcastCommand(signed.rawTx()));
    Map<String, Object> broadcastDetail =
        new BroadcastAuditDetail(broadcast.success(), broadcast.txHash(), broadcast.failureReason())
            .toMap();
    audit(
        item.transactionId(),
        Web3TransactionAuditEventType.BROADCAST,
        broadcast.rpcAlias(),
        broadcastDetail);

    if (broadcast.success()) {
      String txHash =
          (broadcast.txHash() == null || broadcast.txHash().isBlank())
              ? signed.txHash()
              : broadcast.txHash();
      updateTransactionPort.markPending(item.transactionId(), txHash);
      auditStateChange(item.transactionId(), Web3TxStatus.SIGNED, Web3TxStatus.PENDING);
      return;
    }

    retry(
        item.transactionId(),
        broadcast.failureReason() != null
            ? broadcast.failureReason()
            : Web3TxFailureReason.BROADCAST_FAILED.code());
  }

  private void failPrevalidate(Long transactionId, String failureReason, boolean retryable) {
    if (retryable) {
      retry(transactionId, failureReason);
      return;
    }
    updateTransactionPort.scheduleRetry(transactionId, failureReason, null);
  }

  private void auditStateChange(Long transactionId, Web3TxStatus from, Web3TxStatus to) {
    audit(
        transactionId,
        Web3TransactionAuditEventType.STATE_CHANGE,
        null,
        new StateChangeAuditDetail(from, to).toMap());
  }

  private static boolean isFromAddressMatch(String itemFromAddress, String signerAddress) {
    if (itemFromAddress == null || signerAddress == null) {
      return false;
    }
    return itemFromAddress.trim().toLowerCase().equals(signerAddress.trim().toLowerCase());
  }

  private long resolveNonce(
      LoadTransactionWorkPort.TransactionWorkItem item, String treasuryAddress) {
    if (item.nonce() != null) {
      return item.nonce();
    }

    long reservedNonce = reserveNoncePort.reserveNextNonce(treasuryAddress);
    updateTransactionPort.assignNonce(item.transactionId(), reservedNonce);
    return reservedNonce;
  }

  // Best-effort CAS rollback of a nonce that this worker just reserved but cannot broadcast.
  // Skipped on re-entry (item.nonce() != null) because resolveNonce did not advance the cursor.
  // A failed CAS means another reserver advanced past nonce+1 — the gap survives, so we surface
  // it as ERROR for ops triage instead of trying further compensations that could double-release.
  private void releaseReservedNonceQuietly(
      LoadTransactionWorkPort.TransactionWorkItem item, String fromAddress, long nonce) {
    if (item.nonce() != null) {
      return;
    }
    try {
      boolean released = reserveNoncePort.releaseNonce(fromAddress, nonce);
      if (!released) {
        log.error(
            "NONCE_GAP_DETECTED: txId={}, fromAddress={}, abandonedNonce={} "
                + "— another reservation advanced the cursor before release",
            item.transactionId(),
            fromAddress,
            nonce);
      }
    } catch (Exception ex) {
      log.error(
          "Nonce release failed (best-effort): txId={}, nonce={}", item.transactionId(), nonce, ex);
    }
  }

  @Override
  protected List<Class<? extends Throwable>> nonRetryableExceptions() {
    return List.of(Web3InvalidInputException.class, Web3TransactionStateInvalidException.class);
  }
}
