package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ExecutionSignerFailureReason;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ExecutionSignerSlotStatus;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.DescribeKmsKeyPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProbeTreasuryWalletCapabilityServiceTest {

  private static final String ALIAS = "sponsor-treasury";
  private static final String ADDRESS = "0xaec2962556aa2c9c3b3e873121cb4c61ae5f1823";
  private static final String KMS_KEY_ID = "4229019f-0fef-4049-af16-850de547606f";

  @Mock private LoadTreasuryWalletPort loadTreasuryWalletPort;
  @Mock private DescribeKmsKeyPort describeKmsKeyPort;

  private ProbeTreasuryWalletCapabilityService service;

  @BeforeEach
  void setUp() {
    service = new ProbeTreasuryWalletCapabilityService(loadTreasuryWalletPort, describeKmsKeyPort);
  }

  @Test
  void probe_throws_whenAliasIsNullOrBlank() {
    assertThatThrownBy(() -> service.probe(null)).isInstanceOf(Web3InvalidInputException.class);
    assertThatThrownBy(() -> service.probe("  ")).isInstanceOf(Web3InvalidInputException.class);
    verify(loadTreasuryWalletPort, never()).loadByAlias(ALIAS);
  }

  @Test
  void probe_returnsSlotMissing_whenAliasNotFound() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.empty());

    var result = service.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.SLOT_MISSING);
    assertThat(result.signable()).isFalse();
    verify(describeKmsKeyPort, never()).describe(KMS_KEY_ID);
  }

  @Test
  void probe_returnsUnprovisioned_whenAddressAndKmsKeyIdBothMissing() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS))
        .thenReturn(Optional.of(walletBuilder().build()));

    var result = service.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.UNPROVISIONED);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.NONE);
    assertThat(result.signable()).isFalse();
    verify(describeKmsKeyPort, never()).describe(KMS_KEY_ID);
  }

  @Test
  void probe_returnsCorruptedSlot_whenOnlyAddressPresent() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS))
        .thenReturn(Optional.of(walletBuilder().walletAddress(ADDRESS).build()));

    var result = service.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.UNPROVISIONED);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.CORRUPTED_SLOT);
    assertThat(result.signable()).isFalse();
    verify(describeKmsKeyPort, never()).describe(KMS_KEY_ID);
  }

  @Test
  void probe_returnsCorruptedSlot_whenOnlyKmsKeyIdPresent() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS))
        .thenReturn(Optional.of(walletBuilder().kmsKeyId(KMS_KEY_ID).build()));

    var result = service.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.UNPROVISIONED);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.CORRUPTED_SLOT);
    assertThat(result.signable()).isFalse();
    verify(describeKmsKeyPort, never()).describe(KMS_KEY_ID);
  }

  @Test
  void probe_returnsReady_whenStatusActiveAndKmsKeyEnabled() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.of(activeWallet()));
    when(describeKmsKeyPort.describe(KMS_KEY_ID)).thenReturn(KmsKeyState.ENABLED);

    var result = service.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.READY);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.NONE);
    assertThat(result.signerAddress()).isEqualTo(ADDRESS);
    assertThat(result.signable()).isTrue();
  }

  @Test
  void probe_returnsKmsKeyDisabled_whenKmsStateDisabled() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.of(activeWallet()));
    when(describeKmsKeyPort.describe(KMS_KEY_ID)).thenReturn(KmsKeyState.DISABLED);

    var result = service.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.PROVISIONED);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.KMS_KEY_DISABLED);
    assertThat(result.signable()).isFalse();
  }

  @Test
  void probe_returnsKmsKeyPendingDeletion_whenKmsStatePendingDeletion() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.of(activeWallet()));
    when(describeKmsKeyPort.describe(KMS_KEY_ID)).thenReturn(KmsKeyState.PENDING_DELETION);

    var result = service.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.PROVISIONED);
    assertThat(result.failureReason())
        .isEqualTo(ExecutionSignerFailureReason.KMS_KEY_PENDING_DELETION);
    assertThat(result.signable()).isFalse();
  }

  @Test
  void probe_returnsKmsKeyPendingImport_whenKmsStatePendingImport() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.of(activeWallet()));
    when(describeKmsKeyPort.describe(KMS_KEY_ID)).thenReturn(KmsKeyState.PENDING_IMPORT);

    var result = service.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.PROVISIONED);
    assertThat(result.failureReason())
        .isEqualTo(ExecutionSignerFailureReason.KMS_KEY_PENDING_IMPORT);
    assertThat(result.signable()).isFalse();
  }

  @Test
  void probe_returnsKmsKeyUnavailable_whenKmsStateUnavailable() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.of(activeWallet()));
    when(describeKmsKeyPort.describe(KMS_KEY_ID)).thenReturn(KmsKeyState.UNAVAILABLE);

    var result = service.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.PROVISIONED);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.KMS_KEY_UNAVAILABLE);
    assertThat(result.signable()).isFalse();
  }

  @Test
  void probe_returnsKmsDescribeFailed_whenDescribePortThrows() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.of(activeWallet()));
    when(describeKmsKeyPort.describe(KMS_KEY_ID)).thenThrow(new RuntimeException("aws down"));

    var result = service.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.PROVISIONED);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.KMS_DESCRIBE_FAILED);
    assertThat(result.signable()).isFalse();
  }

  @Test
  void probe_returnsWalletDisabled_whenStatusDisabled_andDoesNotCallKms() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS))
        .thenReturn(Optional.of(walletWithStatus(TreasuryWalletStatus.DISABLED)));

    var result = service.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.PROVISIONED);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.WALLET_DISABLED);
    assertThat(result.signable()).isFalse();
    verify(describeKmsKeyPort, never()).describe(KMS_KEY_ID);
  }

  @Test
  void probe_returnsWalletArchived_whenStatusArchived_andDoesNotCallKms() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS))
        .thenReturn(Optional.of(walletWithStatus(TreasuryWalletStatus.ARCHIVED)));

    var result = service.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.PROVISIONED);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.WALLET_ARCHIVED);
    assertThat(result.signable()).isFalse();
    verify(describeKmsKeyPort, never()).describe(KMS_KEY_ID);
  }

  @Test
  void probe_returnsKmsKeyIdMissing_whenStatusNull_andDoesNotCallKms() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.of(walletWithStatus(null)));

    var result = service.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.UNPROVISIONED);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.KMS_KEY_ID_MISSING);
    assertThat(result.signable()).isFalse();
    verify(describeKmsKeyPort, never()).describe(KMS_KEY_ID);
  }

  private static TreasuryWallet activeWallet() {
    return walletWithStatus(TreasuryWalletStatus.ACTIVE);
  }

  private static TreasuryWallet walletWithStatus(TreasuryWalletStatus status) {
    return walletBuilder()
        .walletAddress(ADDRESS)
        .kmsKeyId(KMS_KEY_ID)
        .status(status)
        .keyOrigin(TreasuryKeyOrigin.IMPORTED)
        .build();
  }

  private static TreasuryWallet.TreasuryWalletBuilder walletBuilder() {
    return TreasuryWallet.builder().walletAlias(ALIAS);
  }
}
