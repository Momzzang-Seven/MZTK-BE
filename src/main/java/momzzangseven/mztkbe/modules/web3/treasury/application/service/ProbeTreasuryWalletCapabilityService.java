package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ExecutionSignerCapabilityView;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ExecutionSignerFailureReason;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ExecutionSignerSlotStatus;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ProbeTreasuryWalletCapabilityUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.DescribeKmsKeyPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.springframework.stereotype.Service;

/**
 * Composes {@link LoadTreasuryWalletPort} + {@link DescribeKmsKeyPort} to produce an {@link
 * ExecutionSignerCapabilityView} describing a treasury wallet's current signing readiness.
 *
 * <p>Pairs with {@code VerifyTreasuryWalletForSignService} (same two out-ports) but answers with a
 * diagnostic view instead of throwing — intended for health indicators and admin surfaces that must
 * surface {@code slotStatus}/{@code failureReason} enums to operators.
 *
 * <p>Intentionally not {@code @Transactional}: the path is "single read + KMS describe (network)".
 * Wrapping it in a read-only transaction would pin a JDBC connection across the AWS round-trip; the
 * {@code DescribeKmsKeyPort} adapter additionally suspends the transaction via {@code
 * Propagation.NOT_SUPPORTED} when called from within a caller transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProbeTreasuryWalletCapabilityService implements ProbeTreasuryWalletCapabilityUseCase {

  private final LoadTreasuryWalletPort loadTreasuryWalletPort;
  private final DescribeKmsKeyPort describeKmsKeyPort;

  @Override
  public ExecutionSignerCapabilityView probe(String walletAlias) {
    if (walletAlias == null || walletAlias.isBlank()) {
      throw new Web3InvalidInputException("walletAlias is required");
    }
    return loadTreasuryWalletPort
        .loadByAlias(walletAlias)
        .map(this::mapCapability)
        .orElseGet(() -> ExecutionSignerCapabilityView.slotMissing(walletAlias));
  }

  private ExecutionSignerCapabilityView mapCapability(TreasuryWallet wallet) {
    String walletAlias = wallet.getWalletAlias();
    boolean hasAddress = hasText(wallet.getWalletAddress());
    boolean hasKmsKeyId = hasText(wallet.getKmsKeyId());

    if (!hasAddress && !hasKmsKeyId) {
      return ExecutionSignerCapabilityView.unprovisioned(walletAlias);
    }
    if (hasAddress != hasKmsKeyId) {
      return ExecutionSignerCapabilityView.unavailable(
          walletAlias,
          ExecutionSignerSlotStatus.UNPROVISIONED,
          ExecutionSignerFailureReason.CORRUPTED_SLOT);
    }
    return mapKmsCapability(wallet);
  }

  private ExecutionSignerCapabilityView mapKmsCapability(TreasuryWallet wallet) {
    String walletAlias = wallet.getWalletAlias();
    TreasuryWalletStatus status = wallet.getStatus();
    if (status == TreasuryWalletStatus.DISABLED) {
      return ExecutionSignerCapabilityView.provisionedUnavailable(
          walletAlias, ExecutionSignerFailureReason.WALLET_DISABLED);
    }
    if (status == TreasuryWalletStatus.ARCHIVED) {
      return ExecutionSignerCapabilityView.provisionedUnavailable(
          walletAlias, ExecutionSignerFailureReason.WALLET_ARCHIVED);
    }
    if (status != TreasuryWalletStatus.ACTIVE) {
      return ExecutionSignerCapabilityView.unavailable(
          walletAlias,
          ExecutionSignerSlotStatus.UNPROVISIONED,
          ExecutionSignerFailureReason.KMS_KEY_ID_MISSING);
    }

    KmsKeyState state;
    try {
      state = describeKmsKeyPort.describe(wallet.getKmsKeyId());
    } catch (RuntimeException e) {
      log.warn(
          "describeKmsKeyPort failed for alias={} kmsKeyId={}",
          walletAlias,
          wallet.getKmsKeyId(),
          e);
      return ExecutionSignerCapabilityView.provisionedUnavailable(
          walletAlias, ExecutionSignerFailureReason.KMS_DESCRIBE_FAILED);
    }

    return switch (state) {
      case ENABLED -> ExecutionSignerCapabilityView.ready(walletAlias, wallet.getWalletAddress());
      case DISABLED ->
          ExecutionSignerCapabilityView.provisionedUnavailable(
              walletAlias, ExecutionSignerFailureReason.KMS_KEY_DISABLED);
      case PENDING_DELETION ->
          ExecutionSignerCapabilityView.provisionedUnavailable(
              walletAlias, ExecutionSignerFailureReason.KMS_KEY_PENDING_DELETION);
      case PENDING_IMPORT ->
          ExecutionSignerCapabilityView.provisionedUnavailable(
              walletAlias, ExecutionSignerFailureReason.KMS_KEY_PENDING_IMPORT);
      case UNAVAILABLE ->
          ExecutionSignerCapabilityView.provisionedUnavailable(
              walletAlias, ExecutionSignerFailureReason.KMS_KEY_UNAVAILABLE);
    };
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
