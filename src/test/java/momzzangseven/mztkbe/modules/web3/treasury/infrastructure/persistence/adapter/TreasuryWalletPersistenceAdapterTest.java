package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerFailureReason;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerSlotStatus;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.DescribeKmsKeyPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.entity.Web3TreasuryWalletEntity;
import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.repository.Web3TreasuryWalletJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TreasuryWalletPersistenceAdapterTest {

  private static final String ALIAS = "sponsor-treasury";
  private static final String ADDRESS = "0xaec2962556aa2c9c3b3e873121cb4c61ae5f1823";
  private static final String KMS_KEY_ID = "4229019f-0fef-4049-af16-850de547606f";

  @Mock private Web3TreasuryWalletJpaRepository repository;
  @Mock private DescribeKmsKeyPort describeKmsKeyPort;

  private TreasuryWalletPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new TreasuryWalletPersistenceAdapter(repository, describeKmsKeyPort);
  }

  @Test
  void probe_returnsSlotMissing_whenAliasNotFound() {
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.empty());

    var result = adapter.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.SLOT_MISSING);
    assertThat(result.signable()).isFalse();
    verify(describeKmsKeyPort, never()).describe(KMS_KEY_ID);
  }

  @Test
  void probe_returnsUnprovisioned_whenAddressAndKmsKeyIdBothMissing() {
    Web3TreasuryWalletEntity entity = Web3TreasuryWalletEntity.builder().walletAlias(ALIAS).build();
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.of(entity));

    var result = adapter.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.UNPROVISIONED);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.NONE);
    assertThat(result.signable()).isFalse();
    verify(describeKmsKeyPort, never()).describe(KMS_KEY_ID);
  }

  @Test
  void probe_returnsCorruptedSlot_whenOnlyAddressPresent() {
    Web3TreasuryWalletEntity entity =
        Web3TreasuryWalletEntity.builder().walletAlias(ALIAS).treasuryAddress(ADDRESS).build();
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.of(entity));

    var result = adapter.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.UNPROVISIONED);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.CORRUPTED_SLOT);
    assertThat(result.signable()).isFalse();
    verify(describeKmsKeyPort, never()).describe(KMS_KEY_ID);
  }

  @Test
  void probe_returnsCorruptedSlot_whenOnlyKmsKeyIdPresent() {
    Web3TreasuryWalletEntity entity =
        Web3TreasuryWalletEntity.builder().walletAlias(ALIAS).kmsKeyId(KMS_KEY_ID).build();
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.of(entity));

    var result = adapter.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.UNPROVISIONED);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.CORRUPTED_SLOT);
    assertThat(result.signable()).isFalse();
    verify(describeKmsKeyPort, never()).describe(KMS_KEY_ID);
  }

  @Test
  void probe_returnsReady_whenStatusActiveAndKmsKeyEnabled() {
    Web3TreasuryWalletEntity entity = activeKmsEntity();
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.of(entity));
    when(describeKmsKeyPort.describe(KMS_KEY_ID)).thenReturn(KmsKeyState.ENABLED);

    var result = adapter.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.READY);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.NONE);
    assertThat(result.signerAddress()).isEqualTo(ADDRESS);
    assertThat(result.signable()).isTrue();
  }

  @Test
  void probe_returnsKmsKeyDisabled_whenKmsStateDisabled() {
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.of(activeKmsEntity()));
    when(describeKmsKeyPort.describe(KMS_KEY_ID)).thenReturn(KmsKeyState.DISABLED);

    var result = adapter.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.PROVISIONED);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.KMS_KEY_DISABLED);
    assertThat(result.signable()).isFalse();
  }

  @Test
  void probe_returnsKmsKeyPendingDeletion_whenKmsStatePendingDeletion() {
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.of(activeKmsEntity()));
    when(describeKmsKeyPort.describe(KMS_KEY_ID)).thenReturn(KmsKeyState.PENDING_DELETION);

    var result = adapter.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.PROVISIONED);
    assertThat(result.failureReason())
        .isEqualTo(ExecutionSignerFailureReason.KMS_KEY_PENDING_DELETION);
    assertThat(result.signable()).isFalse();
  }

  @Test
  void probe_returnsKmsKeyPendingImport_whenKmsStatePendingImport() {
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.of(activeKmsEntity()));
    when(describeKmsKeyPort.describe(KMS_KEY_ID)).thenReturn(KmsKeyState.PENDING_IMPORT);

    var result = adapter.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.PROVISIONED);
    assertThat(result.failureReason())
        .isEqualTo(ExecutionSignerFailureReason.KMS_KEY_PENDING_IMPORT);
    assertThat(result.signable()).isFalse();
  }

  @Test
  void probe_returnsKmsKeyUnavailable_whenKmsStateUnavailable() {
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.of(activeKmsEntity()));
    when(describeKmsKeyPort.describe(KMS_KEY_ID)).thenReturn(KmsKeyState.UNAVAILABLE);

    var result = adapter.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.PROVISIONED);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.KMS_KEY_UNAVAILABLE);
    assertThat(result.signable()).isFalse();
  }

  @Test
  void probe_returnsKmsDescribeFailed_whenDescribePortThrows() {
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.of(activeKmsEntity()));
    when(describeKmsKeyPort.describe(KMS_KEY_ID)).thenThrow(new RuntimeException("aws down"));

    var result = adapter.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.PROVISIONED);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.KMS_DESCRIBE_FAILED);
    assertThat(result.signable()).isFalse();
  }

  @Test
  void probe_returnsWalletDisabled_whenStatusDisabled_andDoesNotCallKms() {
    Web3TreasuryWalletEntity entity = kmsEntityWithStatus(TreasuryWalletStatus.DISABLED.name());
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.of(entity));

    var result = adapter.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.PROVISIONED);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.WALLET_DISABLED);
    assertThat(result.signable()).isFalse();
    verify(describeKmsKeyPort, never()).describe(KMS_KEY_ID);
  }

  @Test
  void probe_returnsWalletArchived_whenStatusArchived_andDoesNotCallKms() {
    Web3TreasuryWalletEntity entity = kmsEntityWithStatus(TreasuryWalletStatus.ARCHIVED.name());
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.of(entity));

    var result = adapter.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.PROVISIONED);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.WALLET_ARCHIVED);
    assertThat(result.signable()).isFalse();
    verify(describeKmsKeyPort, never()).describe(KMS_KEY_ID);
  }

  @Test
  void probe_returnsKmsKeyIdMissing_whenStatusNull_andDoesNotCallKms() {
    Web3TreasuryWalletEntity entity = kmsEntityWithStatus(null);
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.of(entity));

    var result = adapter.probe(ALIAS);

    assertThat(result.slotStatus()).isEqualTo(ExecutionSignerSlotStatus.UNPROVISIONED);
    assertThat(result.failureReason()).isEqualTo(ExecutionSignerFailureReason.KMS_KEY_ID_MISSING);
    assertThat(result.signable()).isFalse();
    verify(describeKmsKeyPort, never()).describe(KMS_KEY_ID);
  }

  @Test
  void loadAddressByAlias_returnsStoredAddressProjection_whenPresent() {
    Web3TreasuryWalletEntity entity = activeKmsEntity();
    when(repository.findByWalletAlias(ALIAS)).thenReturn(Optional.of(entity));

    var result = adapter.loadAddressByAlias(ALIAS);

    assertThat(result).contains(ADDRESS);
  }

  private static Web3TreasuryWalletEntity activeKmsEntity() {
    return kmsEntityWithStatus(TreasuryWalletStatus.ACTIVE.name());
  }

  private static Web3TreasuryWalletEntity kmsEntityWithStatus(String status) {
    return Web3TreasuryWalletEntity.builder()
        .walletAlias(ALIAS)
        .treasuryAddress(ADDRESS)
        .kmsKeyId(KMS_KEY_ID)
        .status(status)
        .build();
  }
}
