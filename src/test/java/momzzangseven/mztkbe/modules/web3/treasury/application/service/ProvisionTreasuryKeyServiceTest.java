package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
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
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.TreasuryAdvisoryLockPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.AliasProvisionedAuditEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.KeyLifecycleEvent;
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
  @Mock private TreasuryAdvisoryLockPort treasuryAdvisoryLockPort;
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
            treasuryAdvisoryLockPort,
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
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(DERIVED_ADDRESS))
        .thenReturn(List.of());
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
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(DERIVED_ADDRESS))
        .thenReturn(List.of());
    when(kmsKeyLifecyclePort.describeAliasTarget("reward-treasury"))
        .thenReturn(KmsKeyState.UNAVAILABLE);

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    ProvisionTreasuryKeyResult result = service.execute(command);

    assertThat(result.kmsKeyId()).isEqualTo("existing-kms-id");
    List<Object> events = capturePublishedEvents(2);
    AliasProvisionedAuditEvent audit = firstOf(events, AliasProvisionedAuditEvent.class);
    KeyLifecycleEvent.BoundAlias bound = firstOf(events, KeyLifecycleEvent.BoundAlias.class);
    assertThat(audit.aliasRepairMode()).isTrue();
    assertThat(audit.coBind()).isFalse();
    assertThat(bound.kmsKeyId()).isEqualTo("existing-kms-id");
    verify(kmsKeyLifecyclePort, never()).createKey();
    verify(saveTreasuryWalletPort, never()).save(any());
    verify(treasuryAuditRecorder, never()).record(eq(1L), any(), any(), eq(true), any());
  }

  @Test
  void execute_entersAliasRepairMode_whenAliasPointsToPendingDeletionKey() {
    TreasuryWallet existing =
        legacyRow(DERIVED_ADDRESS).toBuilder().kmsKeyId("existing-kms-id").build();
    when(loadTreasuryWalletPort.loadByAlias("reward-treasury")).thenReturn(Optional.of(existing));
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(DERIVED_ADDRESS))
        .thenReturn(List.of());
    when(kmsKeyLifecyclePort.describeAliasTarget("reward-treasury"))
        .thenReturn(KmsKeyState.PENDING_DELETION);

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    service.execute(command);

    List<Object> events = capturePublishedEvents(2);
    assertThat(firstOf(events, AliasProvisionedAuditEvent.class).aliasRepairMode()).isTrue();
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
  void execute_coBindsToActiveCohort_reusingSharedKeyWithoutCreatingNewKey() {
    TreasuryWallet sibling =
        TreasuryWallet.provision(
            TreasuryRole.SPONSOR.toAlias(),
            "shared-kms-id",
            DERIVED_ADDRESS,
            TreasuryRole.SPONSOR,
            fixedClock());
    when(loadTreasuryWalletPort.loadByAlias("reward-treasury")).thenReturn(Optional.empty());
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(DERIVED_ADDRESS))
        .thenReturn(List.of(sibling));
    when(saveTreasuryWalletPort.save(any(TreasuryWallet.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    ProvisionTreasuryKeyResult result = service.execute(command);

    assertThat(result.kmsKeyId()).isEqualTo("shared-kms-id");
    verify(treasuryAdvisoryLockPort).lockForAddress(DERIVED_ADDRESS);
    verify(kmsKeyLifecyclePort, never()).createKey();
    ArgumentCaptor<TreasuryWallet> savedCaptor = ArgumentCaptor.forClass(TreasuryWallet.class);
    verify(saveTreasuryWalletPort).save(savedCaptor.capture());
    assertThat(savedCaptor.getValue().getKmsKeyId()).isEqualTo("shared-kms-id");

    List<Object> events = capturePublishedEvents(2);
    AliasProvisionedAuditEvent audit = firstOf(events, AliasProvisionedAuditEvent.class);
    KeyLifecycleEvent.BoundAlias bound = firstOf(events, KeyLifecycleEvent.BoundAlias.class);
    assertThat(audit.coBind()).isTrue();
    assertThat(bound.kmsKeyId()).isEqualTo("shared-kms-id");
    assertThat(bound.walletAlias()).isEqualTo("reward-treasury");
  }

  @Test
  void execute_rejectsCoBind_whenCohortNotAllActive() {
    TreasuryWallet disabledSibling =
        TreasuryWallet.provision(
                TreasuryRole.SPONSOR.toAlias(),
                "shared-kms-id",
                DERIVED_ADDRESS,
                TreasuryRole.SPONSOR,
                fixedClock())
            .disable(fixedClock());
    when(loadTreasuryWalletPort.loadByAlias("reward-treasury")).thenReturn(Optional.empty());
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(DERIVED_ADDRESS))
        .thenReturn(List.of(disabledSibling));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(TreasuryWalletAlreadyProvisionedException.class);

    verify(treasuryAuditRecorder)
        .record(
            eq(1L),
            eq("reward-treasury"),
            eq(DERIVED_ADDRESS),
            eq(false),
            eq("COHORT_NOT_ALL_ACTIVE"));
    verify(kmsKeyLifecyclePort, never()).createKey();
    verify(saveTreasuryWalletPort, never()).save(any());
    verify(applicationEventPublisher, never()).publishEvent(any());
  }

  @Test
  void execute_rejectsMixedCohort_whenSiblingsDisagreeOnKey() {
    TreasuryWallet sibA =
        TreasuryWallet.provision(
            TreasuryRole.SPONSOR.toAlias(),
            "kms-a",
            DERIVED_ADDRESS,
            TreasuryRole.SPONSOR,
            fixedClock());
    TreasuryWallet sibB =
        TreasuryWallet.provision(
            TreasuryRole.QNA_SIGNER.toAlias(),
            "kms-b",
            DERIVED_ADDRESS,
            TreasuryRole.QNA_SIGNER,
            fixedClock());
    when(loadTreasuryWalletPort.loadByAlias("reward-treasury")).thenReturn(Optional.empty());
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(DERIVED_ADDRESS))
        .thenReturn(List.of(sibA, sibB));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(TreasuryWalletStateException.class);

    verify(treasuryAuditRecorder)
        .record(
            eq(1L),
            eq("reward-treasury"),
            eq(DERIVED_ADDRESS),
            eq(false),
            eq("COHORT_STATE_INCONSISTENT"));
    verify(kmsKeyLifecyclePort, never()).createKey();
    verify(saveTreasuryWalletPort, never()).save(any());
  }

  @Test
  void execute_coBind_doesNotRegisterRollbackCleanup() {
    TransactionSynchronizationManager.initSynchronization();
    try {
      TreasuryWallet sibling =
          TreasuryWallet.provision(
              TreasuryRole.SPONSOR.toAlias(),
              "shared-kms-id",
              DERIVED_ADDRESS,
              TreasuryRole.SPONSOR,
              fixedClock());
      when(loadTreasuryWalletPort.loadByAlias("reward-treasury")).thenReturn(Optional.empty());
      when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(DERIVED_ADDRESS))
          .thenReturn(List.of(sibling));
      when(saveTreasuryWalletPort.save(any(TreasuryWallet.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      ProvisionTreasuryKeyCommand command =
          new ProvisionTreasuryKeyCommand(
              1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

      service.execute(command);

      // co-bind must never register rollback cleanup — the shared key must not be torn down
      assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
      verify(kmsKeyLifecyclePort, never()).disableKey(anyString());
      verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(anyString(), anyInt());
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void execute_throws_whenPrivateKeyInvalid() {
    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, "z".repeat(64), TreasuryRole.REWARD, DERIVED_ADDRESS);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(TreasuryPrivateKeyInvalidException.class);
  }

  @Test
  void execute_drivesKmsLifecycleAndPublishesEvents_onNewProvision() {
    primeSuccessfulMocks(Optional.empty());

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    ProvisionTreasuryKeyResult result = service.execute(command);

    assertThat(result.walletAlias()).isEqualTo("reward-treasury");
    assertThat(result.kmsKeyId()).isEqualTo("kms-key-id");
    assertThat(result.walletAddress()).isEqualTo(DERIVED_ADDRESS);
    verify(treasuryAdvisoryLockPort).lockForAddress(DERIVED_ADDRESS);
    verify(kmsKeyLifecyclePort)
        .importKeyMaterial(eq("kms-key-id"), any(byte[].class), any(byte[].class));
    verify(saveTreasuryWalletPort).save(any(TreasuryWallet.class));
    verify(kmsKeyLifecyclePort, never()).createAlias(anyString(), anyString());

    List<Object> events = capturePublishedEvents(2);
    AliasProvisionedAuditEvent audit = firstOf(events, AliasProvisionedAuditEvent.class);
    KeyLifecycleEvent.BoundAlias bound = firstOf(events, KeyLifecycleEvent.BoundAlias.class);
    assertThat(audit.aliasRepairMode()).isFalse();
    assertThat(audit.coBind()).isFalse();
    assertThat(bound.kmsKeyId()).isEqualTo("kms-key-id");
    assertThat(bound.walletAlias()).isEqualTo("reward-treasury");
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
  }

  @Test
  void execute_cleansUpKmsKey_whenSignDigestFails() {
    when(loadTreasuryWalletPort.loadByAlias("reward-treasury")).thenReturn(Optional.empty());
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(DERIVED_ADDRESS))
        .thenReturn(List.of());
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
  void execute_sanitySignTerminalKmsFailure_propagatesNonRetryableException() {
    when(loadTreasuryWalletPort.loadByAlias("reward-treasury")).thenReturn(Optional.empty());
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(DERIVED_ADDRESS))
        .thenReturn(List.of());
    when(kmsKeyLifecyclePort.createKey()).thenReturn("kms-key-id");
    when(kmsKeyLifecyclePort.getParametersForImport("kms-key-id"))
        .thenReturn(new KmsKeyLifecyclePort.ImportParams(new byte[] {1}, new byte[] {2}));
    when(kmsKeyMaterialWrapperPort.wrap(any(byte[].class), any(byte[].class)))
        .thenReturn(new byte[] {3});
    KmsSignFailedException terminal =
        new KmsSignFailedException("kms denied", new RuntimeException("AccessDenied"), false);
    when(signDigestPort.signDigest(anyString(), any(byte[].class), anyString()))
        .thenThrow(terminal);

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(KmsSignFailedException.class)
        .satisfies(ex -> assertThat(((KmsSignFailedException) ex).isRetryable()).isFalse());

    verify(kmsKeyLifecyclePort).disableKey("kms-key-id");
    verify(kmsKeyLifecyclePort).scheduleKeyDeletion(eq("kms-key-id"), anyInt());
    verify(saveTreasuryWalletPort, never()).save(any(TreasuryWallet.class));
    verify(applicationEventPublisher, never()).publishEvent(any());
  }

  @Test
  void execute_sanitySignTransientKmsFailure_propagatesRetryableException() {
    when(loadTreasuryWalletPort.loadByAlias("reward-treasury")).thenReturn(Optional.empty());
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(DERIVED_ADDRESS))
        .thenReturn(List.of());
    when(kmsKeyLifecyclePort.createKey()).thenReturn("kms-key-id");
    when(kmsKeyLifecyclePort.getParametersForImport("kms-key-id"))
        .thenReturn(new KmsKeyLifecyclePort.ImportParams(new byte[] {1}, new byte[] {2}));
    when(kmsKeyMaterialWrapperPort.wrap(any(byte[].class), any(byte[].class)))
        .thenReturn(new byte[] {3});
    KmsSignFailedException transientFailure =
        new KmsSignFailedException("kms throttled", new RuntimeException("Throttling"), true);
    when(signDigestPort.signDigest(anyString(), any(byte[].class), anyString()))
        .thenThrow(transientFailure);

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(KmsSignFailedException.class)
        .satisfies(ex -> assertThat(((KmsSignFailedException) ex).isRetryable()).isTrue());

    verify(kmsKeyLifecyclePort).disableKey("kms-key-id");
    verify(kmsKeyLifecyclePort).scheduleKeyDeletion(eq("kms-key-id"), anyInt());
    verify(saveTreasuryWalletPort, never()).save(any(TreasuryWallet.class));
    verify(applicationEventPublisher, never()).publishEvent(any());
  }

  @Test
  void execute_cleansUpKmsKey_whenImportKeyMaterialFails() {
    when(loadTreasuryWalletPort.loadByAlias("reward-treasury")).thenReturn(Optional.empty());
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(DERIVED_ADDRESS))
        .thenReturn(List.of());
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
  void execute_doesNotRecordSuccessAudit_inline() {
    primeSuccessfulMocks(Optional.empty());

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    service.execute(command);

    verify(treasuryAuditRecorder, never()).record(eq(1L), any(), any(), eq(true), any());
  }

  @Test
  void execute_throws_whenLegacyRowDisabled_andCleansUpKmsKey() {
    TreasuryWallet disabledLegacy =
        legacyRow(DERIVED_ADDRESS).toBuilder().status(TreasuryWalletStatus.DISABLED).build();
    when(loadTreasuryWalletPort.loadByAlias("reward-treasury"))
        .thenReturn(Optional.of(disabledLegacy));
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(DERIVED_ADDRESS))
        .thenReturn(List.of());
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
  void execute_synchronization_skipsCleanupAndRecordsAlertAudit_whenStatusUnknown() {
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

      registered.get(0).afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);

      verify(kmsKeyLifecyclePort, never()).disableKey(anyString());
      verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(anyString(), anyInt());
      verify(treasuryAuditRecorder)
          .record(
              eq(1L),
              eq("reward-treasury"),
              eq(DERIVED_ADDRESS),
              eq(false),
              eq("TX_STATUS_UNKNOWN"));
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void execute_synchronization_skipsCleanupAndRecordsAlertAudit_whenStatusUnrecognized() {
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

      registered.get(0).afterCompletion(99);

      verify(kmsKeyLifecyclePort, never()).disableKey(anyString());
      verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(anyString(), anyInt());
      verify(treasuryAuditRecorder)
          .record(
              eq(1L),
              eq("reward-treasury"),
              eq(DERIVED_ADDRESS),
              eq(false),
              eq("TX_STATUS_UNKNOWN"));
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
      when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(DERIVED_ADDRESS))
          .thenReturn(List.of());
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

      verify(kmsKeyLifecyclePort, times(1)).disableKey("kms-key-id");
      verify(kmsKeyLifecyclePort, times(1)).scheduleKeyDeletion(eq("kms-key-id"), anyInt());
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private static Clock fixedClock() {
    return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
  }

  private List<Object> capturePublishedEvents(int expectedCount) {
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(applicationEventPublisher, times(expectedCount)).publishEvent(captor.capture());
    return captor.getAllValues();
  }

  private static <T> T firstOf(List<Object> events, Class<T> type) {
    return events.stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
  }

  private void primeSuccessfulMocks(Optional<TreasuryWallet> existing) {
    when(loadTreasuryWalletPort.loadByAlias("reward-treasury")).thenReturn(existing);
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(DERIVED_ADDRESS))
        .thenReturn(List.of());
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
