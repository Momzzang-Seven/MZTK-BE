package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAddressMismatchException;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAlreadyProvisionedException;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.error.web3.TreasuryPrivateKeyInvalidException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyMaterialWrapperPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SignDigestPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletProvisionedEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.web3j.crypto.Credentials;

@ExtendWith(MockitoExtension.class)
class ProvisionTreasuryKeyServiceTest {

  private static final String PRIVATE_KEY_HEX =
      "4f3edf983ac636a65a842ce7c78d9aa706d3b113bce036f4edc6f6dc0d1e6f73";
  private static final String DERIVED_ADDRESS =
      Credentials.create(PRIVATE_KEY_HEX).getAddress().toLowerCase();

  @Mock private LoadTreasuryWalletPort loadTreasuryWalletPort;
  @Mock private SaveTreasuryWalletPort saveTreasuryWalletPort;
  @Mock private KmsKeyLifecyclePort kmsKeyLifecyclePort;
  @Mock private KmsKeyMaterialWrapperPort kmsKeyMaterialWrapperPort;
  @Mock private SignDigestPort signDigestPort;
  @Mock private TreasuryAuditRecorder treasuryAuditRecorder;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  private ProvisionTreasuryKeyService service;

  @BeforeEach
  void setUp() {
    Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    service =
        new ProvisionTreasuryKeyService(
            loadTreasuryWalletPort,
            saveTreasuryWalletPort,
            kmsKeyLifecyclePort,
            kmsKeyMaterialWrapperPort,
            signDigestPort,
            treasuryAuditRecorder,
            applicationEventPublisher,
            fixed);
  }

  @Test
  void execute_throws_whenCommandNull() {
    assertThatThrownBy(() -> service.execute(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }

  @Test
  void execute_throws_whenAddressMismatch() {
    String wrongAddress = "0x" + "a".repeat(40);
    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, wrongAddress);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(TreasuryWalletAddressMismatchException.class);

    verify(kmsKeyLifecyclePort, never()).createKey();
  }

  @Test
  void execute_throws_whenAliasAlreadyHasKmsKeyId_andAliasPointsToEnabledKey() {
    TreasuryWallet existing =
        legacyRow(DERIVED_ADDRESS).toBuilder().kmsKeyId("existing-kms-id").build();
    when(loadTreasuryWalletPort.loadByAlias("reward-treasury")).thenReturn(Optional.of(existing));
    when(kmsKeyLifecyclePort.describeAliasTarget("reward-treasury"))
        .thenReturn(KmsKeyState.ENABLED);

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(TreasuryWalletAlreadyProvisionedException.class);

    verify(kmsKeyLifecyclePort, never()).createKey();
    verify(applicationEventPublisher, never()).publishEvent(any());
  }

  @Test
  void execute_entersAliasRepairMode_whenRowHasKmsKeyIdButAliasMissing() {
    TreasuryWallet existing =
        legacyRow(DERIVED_ADDRESS).toBuilder().kmsKeyId("existing-kms-id").build();
    when(loadTreasuryWalletPort.loadByAlias("reward-treasury")).thenReturn(Optional.of(existing));
    when(kmsKeyLifecyclePort.describeAliasTarget("reward-treasury"))
        .thenReturn(KmsKeyState.UNAVAILABLE);

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    ProvisionTreasuryKeyResult result = service.execute(command);

    assertThat(result.kmsKeyId()).isEqualTo("existing-kms-id");
    ArgumentCaptor<TreasuryWalletProvisionedEvent> captor =
        ArgumentCaptor.forClass(TreasuryWalletProvisionedEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    TreasuryWalletProvisionedEvent event = captor.getValue();
    assertThat(event.aliasRepairMode()).isTrue();
    assertThat(event.kmsKeyId()).isEqualTo("existing-kms-id");
    verify(kmsKeyLifecyclePort, never()).createKey();
    verify(saveTreasuryWalletPort, never()).save(any());
    verify(treasuryAuditRecorder).record(1L, DERIVED_ADDRESS, true, null);
  }

  @Test
  void execute_entersAliasRepairMode_whenAliasPointsToPendingDeletionKey() {
    TreasuryWallet existing =
        legacyRow(DERIVED_ADDRESS).toBuilder().kmsKeyId("existing-kms-id").build();
    when(loadTreasuryWalletPort.loadByAlias("reward-treasury")).thenReturn(Optional.of(existing));
    when(kmsKeyLifecyclePort.describeAliasTarget("reward-treasury"))
        .thenReturn(KmsKeyState.PENDING_DELETION);

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    service.execute(command);

    ArgumentCaptor<TreasuryWalletProvisionedEvent> captor =
        ArgumentCaptor.forClass(TreasuryWalletProvisionedEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().aliasRepairMode()).isTrue();
  }

  @Test
  void execute_throws_whenLegacyRowAddressDoesNotMatchDerivedAddress() {
    TreasuryWallet existing = legacyRow("0x" + "9".repeat(40));
    when(loadTreasuryWalletPort.loadByAlias("reward-treasury")).thenReturn(Optional.of(existing));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(TreasuryWalletAddressMismatchException.class);

    verify(kmsKeyLifecyclePort, never()).createKey();
  }

  @Test
  void execute_throws_whenAddressOwnedByDifferentAlias() {
    when(loadTreasuryWalletPort.loadByAlias("reward-treasury")).thenReturn(Optional.empty());
    when(loadTreasuryWalletPort.existsAddressOwnedByOther("reward-treasury", DERIVED_ADDRESS))
        .thenReturn(true);

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(TreasuryWalletAlreadyProvisionedException.class);

    verify(kmsKeyLifecyclePort, never()).createKey();
  }

  @Test
  void execute_throws_whenPrivateKeyInvalid() {
    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, "z".repeat(64), TreasuryRole.REWARD, DERIVED_ADDRESS);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(TreasuryPrivateKeyInvalidException.class);
  }

  @Test
  void execute_drivesKmsLifecycleAndPublishesEvent_onNewProvision() {
    primeSuccessfulMocks(Optional.empty());

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    ProvisionTreasuryKeyResult result = service.execute(command);

    assertThat(result.walletAlias()).isEqualTo("reward-treasury");
    assertThat(result.kmsKeyId()).isEqualTo("kms-key-id");
    assertThat(result.walletAddress()).isEqualTo(DERIVED_ADDRESS);
    verify(kmsKeyLifecyclePort)
        .importKeyMaterial(eq("kms-key-id"), any(byte[].class), any(byte[].class));
    verify(saveTreasuryWalletPort).save(any(TreasuryWallet.class));
    verify(kmsKeyLifecyclePort, never()).createAlias(anyString(), anyString());
    ArgumentCaptor<TreasuryWalletProvisionedEvent> captor =
        ArgumentCaptor.forClass(TreasuryWalletProvisionedEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    TreasuryWalletProvisionedEvent event = captor.getValue();
    assertThat(event.aliasRepairMode()).isFalse();
    assertThat(event.kmsKeyId()).isEqualTo("kms-key-id");
    assertThat(event.walletAlias()).isEqualTo("reward-treasury");
  }

  @Test
  void execute_backfillsLegacyRow_preservingIdAndAddress() {
    TreasuryWallet legacy = legacyRow(DERIVED_ADDRESS);
    primeSuccessfulMocks(Optional.of(legacy));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    service.execute(command);

    ArgumentCaptor<TreasuryWallet> captor = ArgumentCaptor.forClass(TreasuryWallet.class);
    verify(saveTreasuryWalletPort).save(captor.capture());
    TreasuryWallet saved = captor.getValue();
    assertThat(saved.getId()).isEqualTo(legacy.getId());
    assertThat(saved.getWalletAddress()).isEqualToIgnoringCase(DERIVED_ADDRESS);
    assertThat(saved.getKmsKeyId()).isEqualTo("kms-key-id");
    assertThat(saved.getStatus()).isEqualTo(TreasuryWalletStatus.ACTIVE);
    assertThat(saved.getCreatedAt()).isEqualTo(legacy.getCreatedAt());
    verify(loadTreasuryWalletPort, never()).existsAddressOwnedByOther(anyString(), anyString());
  }

  @Test
  void execute_cleansUpKmsKey_whenSignDigestFails() {
    when(loadTreasuryWalletPort.loadByAlias("reward-treasury")).thenReturn(Optional.empty());
    when(loadTreasuryWalletPort.existsAddressOwnedByOther(anyString(), anyString()))
        .thenReturn(false);
    when(kmsKeyLifecyclePort.createKey()).thenReturn("kms-key-id");
    when(kmsKeyLifecyclePort.getParametersForImport("kms-key-id"))
        .thenReturn(new KmsKeyLifecyclePort.ImportParams(new byte[] {1}, new byte[] {2}));
    when(kmsKeyMaterialWrapperPort.wrap(any(byte[].class), any(byte[].class)))
        .thenReturn(new byte[] {3});
    when(signDigestPort.signDigest(anyString(), any(byte[].class), anyString()))
        .thenThrow(new RuntimeException("kms unreachable"));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("kms unreachable");

    verify(kmsKeyLifecyclePort).disableKey("kms-key-id");
    verify(kmsKeyLifecyclePort).scheduleKeyDeletion(eq("kms-key-id"), anyInt());
    verify(saveTreasuryWalletPort, never()).save(any(TreasuryWallet.class));
    verify(applicationEventPublisher, never()).publishEvent(any());
  }

  @Test
  void execute_cleansUpKmsKey_whenImportKeyMaterialFails() {
    when(loadTreasuryWalletPort.loadByAlias("reward-treasury")).thenReturn(Optional.empty());
    when(loadTreasuryWalletPort.existsAddressOwnedByOther(anyString(), anyString()))
        .thenReturn(false);
    when(kmsKeyLifecyclePort.createKey()).thenReturn("kms-key-id");
    when(kmsKeyLifecyclePort.getParametersForImport("kms-key-id"))
        .thenReturn(new KmsKeyLifecyclePort.ImportParams(new byte[] {1}, new byte[] {2}));
    when(kmsKeyMaterialWrapperPort.wrap(any(byte[].class), any(byte[].class)))
        .thenReturn(new byte[] {3});
    Mockito.doThrow(new RuntimeException("kms import refused"))
        .when(kmsKeyLifecyclePort)
        .importKeyMaterial(anyString(), any(byte[].class), any(byte[].class));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("kms import refused");

    verify(kmsKeyLifecyclePort).disableKey("kms-key-id");
    verify(kmsKeyLifecyclePort).scheduleKeyDeletion(eq("kms-key-id"), anyInt());
    verify(applicationEventPublisher, never()).publishEvent(any());
    verify(saveTreasuryWalletPort, never()).save(any(TreasuryWallet.class));
  }

  @Test
  void execute_recordsAudit_onSuccess() {
    primeSuccessfulMocks(Optional.empty());

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    service.execute(command);

    verify(treasuryAuditRecorder).record(1L, DERIVED_ADDRESS, true, null);
  }

  @Test
  void execute_throws_whenLegacyRowDisabled_andCleansUpKmsKey() {
    TreasuryWallet disabledLegacy =
        legacyRow(DERIVED_ADDRESS).toBuilder().status(TreasuryWalletStatus.DISABLED).build();
    when(loadTreasuryWalletPort.loadByAlias("reward-treasury"))
        .thenReturn(Optional.of(disabledLegacy));
    when(kmsKeyLifecyclePort.createKey()).thenReturn("kms-key-id");
    when(kmsKeyLifecyclePort.getParametersForImport("kms-key-id"))
        .thenReturn(new KmsKeyLifecyclePort.ImportParams(new byte[] {1}, new byte[] {2}));
    when(kmsKeyMaterialWrapperPort.wrap(any(byte[].class), any(byte[].class)))
        .thenReturn(new byte[] {3});
    when(signDigestPort.signDigest(anyString(), any(byte[].class), anyString()))
        .thenReturn(new Vrs(new byte[32], new byte[32], (byte) 27));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(TreasuryWalletStateException.class)
        .hasMessageContaining("DISABLED");

    verify(kmsKeyLifecyclePort).disableKey("kms-key-id");
    verify(kmsKeyLifecyclePort).scheduleKeyDeletion(eq("kms-key-id"), anyInt());
    verify(saveTreasuryWalletPort, never()).save(any(TreasuryWallet.class));
    verify(applicationEventPublisher, never()).publishEvent(any());
  }

  @Test
  void execute_registersSynchronization_andCleansUpOnRollback() {
    TransactionSynchronizationManager.initSynchronization();
    try {
      primeSuccessfulMocks(Optional.empty());

      ProvisionTreasuryKeyCommand command =
          new ProvisionTreasuryKeyCommand(
              1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

      service.execute(command);

      List<TransactionSynchronization> registered =
          TransactionSynchronizationManager.getSynchronizations();
      assertThat(registered).hasSize(1);
      verify(kmsKeyLifecyclePort, never()).disableKey(anyString());

      registered.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

      verify(kmsKeyLifecyclePort).disableKey("kms-key-id");
      verify(kmsKeyLifecyclePort).scheduleKeyDeletion(eq("kms-key-id"), anyInt());
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void execute_synchronization_doesNotCleanup_whenCommitted() {
    TransactionSynchronizationManager.initSynchronization();
    try {
      primeSuccessfulMocks(Optional.empty());

      ProvisionTreasuryKeyCommand command =
          new ProvisionTreasuryKeyCommand(
              1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

      service.execute(command);

      List<TransactionSynchronization> registered =
          TransactionSynchronizationManager.getSynchronizations();
      assertThat(registered).hasSize(1);

      registered.get(0).afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

      verify(kmsKeyLifecyclePort, never()).disableKey(anyString());
      verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(anyString(), anyInt());
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void execute_inMethodFailure_invokesCleanupOnce_evenWhenSynchronizationFiresAfterRollback() {
    TransactionSynchronizationManager.initSynchronization();
    try {
      when(loadTreasuryWalletPort.loadByAlias("reward-treasury")).thenReturn(Optional.empty());
      when(loadTreasuryWalletPort.existsAddressOwnedByOther(anyString(), anyString()))
          .thenReturn(false);
      when(kmsKeyLifecyclePort.createKey()).thenReturn("kms-key-id");
      when(kmsKeyLifecyclePort.getParametersForImport("kms-key-id"))
          .thenReturn(new KmsKeyLifecyclePort.ImportParams(new byte[] {1}, new byte[] {2}));
      when(kmsKeyMaterialWrapperPort.wrap(any(byte[].class), any(byte[].class)))
          .thenReturn(new byte[] {3});
      when(signDigestPort.signDigest(anyString(), any(byte[].class), anyString()))
          .thenThrow(new RuntimeException("kms unreachable"));

      ProvisionTreasuryKeyCommand command =
          new ProvisionTreasuryKeyCommand(
              1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

      assertThatThrownBy(() -> service.execute(command)).isInstanceOf(RuntimeException.class);

      List<TransactionSynchronization> registered =
          TransactionSynchronizationManager.getSynchronizations();
      assertThat(registered).hasSize(1);
      registered.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

      verify(kmsKeyLifecyclePort, Mockito.times(1)).disableKey("kms-key-id");
      verify(kmsKeyLifecyclePort, Mockito.times(1)).scheduleKeyDeletion(eq("kms-key-id"), anyInt());
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private void primeSuccessfulMocks(Optional<TreasuryWallet> existing) {
    when(loadTreasuryWalletPort.loadByAlias("reward-treasury")).thenReturn(existing);
    if (existing.isEmpty()) {
      when(loadTreasuryWalletPort.existsAddressOwnedByOther(anyString(), anyString()))
          .thenReturn(false);
    }
    when(kmsKeyLifecyclePort.createKey()).thenReturn("kms-key-id");
    when(kmsKeyLifecyclePort.getParametersForImport("kms-key-id"))
        .thenReturn(new KmsKeyLifecyclePort.ImportParams(new byte[] {1}, new byte[] {2}));
    when(kmsKeyMaterialWrapperPort.wrap(any(byte[].class), any(byte[].class)))
        .thenReturn(new byte[] {3});
    when(signDigestPort.signDigest(anyString(), any(byte[].class), anyString()))
        .thenReturn(new Vrs(new byte[32], new byte[32], (byte) 27));
    when(saveTreasuryWalletPort.save(any(TreasuryWallet.class)))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  private static TreasuryWallet legacyRow(String address) {
    return TreasuryWallet.builder()
        .id(42L)
        .walletAlias("reward-treasury")
        .walletAddress(address)
        .kmsKeyId(null)
        .status(null)
        .keyOrigin(null)
        .createdAt(LocalDateTime.parse("2025-12-01T00:00:00"))
        .updatedAt(LocalDateTime.parse("2025-12-01T00:00:00"))
        .build();
  }
}
