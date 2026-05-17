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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAddressMismatchException;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAlreadyProvisionedException;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import momzzangseven.mztkbe.global.error.web3.TreasuryPrivateKeyInvalidException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.AliasTargetInfo;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyMaterialWrapperPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SignDigestPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletKeyReplacedEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletProvisionedEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletReactivatedEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryKeyOrigin;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;
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
  @Mock private KmsAuditRecorder kmsAuditRecorder;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  private ProvisionTreasuryKeyTransactionalDelegate delegate;
  private ProvisionTreasuryKeyService service;

  @BeforeEach
  void setUp() {
    Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    delegate =
        new ProvisionTreasuryKeyTransactionalDelegate(
            loadTreasuryWalletPort,
            saveTreasuryWalletPort,
            kmsKeyLifecyclePort,
            kmsKeyMaterialWrapperPort,
            signDigestPort,
            treasuryAuditRecorder,
            kmsAuditRecorder,
            applicationEventPublisher,
            fixed);
    service = new ProvisionTreasuryKeyService(delegate, treasuryAuditRecorder);
  }

  // ---------- Input validation ----------

  @Test
  void execute_throws_whenCommandNull() {
    assertThatThrownBy(() -> service.execute(null)).isInstanceOf(Web3InvalidInputException.class);
  }

  @Test
  void execute_throws_whenPrivateKeyInvalid() {
    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, "z".repeat(64), TreasuryRole.REWARD, DERIVED_ADDRESS);
    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(TreasuryPrivateKeyInvalidException.class);
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

  // ---------- Mint failures (mint now lives inside the delegate, called only by mint-required
  // actions). Use C0 fixture (existingOpt empty → FreshProvision) so mint is exercised.

  @Test
  void execute_cleansUpMintedKey_whenSignDigestFails() {
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.empty());
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
    verify(saveTreasuryWalletPort, never()).save(any());
  }

  @Test
  void execute_cleansUpMintedKey_whenImportKeyMaterialFails() {
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.empty());
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

    assertThatThrownBy(() -> service.execute(command)).isInstanceOf(RuntimeException.class);

    verify(kmsKeyLifecyclePort).disableKey("kms-key-id");
    verify(kmsKeyLifecyclePort).scheduleKeyDeletion(eq("kms-key-id"), anyInt());
  }

  @Test
  void execute_sanitySignTerminalKmsFailure_propagatesNonRetryable() {
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.empty());
    stubKmsMintHappyPathExceptSign();
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
  }

  // ---------- 13-case dispatch ----------

  @Test
  void c0_freshProvision_insertsAndPublishesProvisionedEvent() {
    stubKmsMintHappyPath();
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.empty());
    when(saveTreasuryWalletPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    service.execute(command);

    ArgumentCaptor<TreasuryWalletProvisionedEvent> captor =
        ArgumentCaptor.forClass(TreasuryWalletProvisionedEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().kmsKeyId()).isEqualTo("kms-key-id");
    assertThat(captor.getValue().aliasRepairMode()).isFalse();
    verify(kmsKeyLifecyclePort, times(1)).createKey();
    verify(kmsKeyLifecyclePort, never()).disableKey(anyString());
  }

  @Test
  void c1_backfillSameAddrActive_updatesAndPublishesProvisioned() {
    TreasuryWallet legacy = legacyRow(DERIVED_ADDRESS, null, TreasuryWalletStatus.ACTIVE);
    stubKmsMintHappyPath();
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(legacy));
    when(saveTreasuryWalletPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    service.execute(command);

    verify(applicationEventPublisher).publishEvent(any(TreasuryWalletProvisionedEvent.class));
    verify(kmsKeyLifecyclePort, times(1)).createKey();
  }

  @Test
  void c2_backfillSameAddrDisabled_promotesToActive() {
    TreasuryWallet legacy = legacyRow(DERIVED_ADDRESS, null, TreasuryWalletStatus.DISABLED);
    stubKmsMintHappyPath();
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(legacy));
    when(saveTreasuryWalletPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    service.execute(command);

    verify(applicationEventPublisher).publishEvent(any(TreasuryWalletProvisionedEvent.class));
    verify(kmsKeyLifecyclePort, times(1)).createKey();
  }

  @Test
  void c3_backfillSameAddrArchived_promotesToActive() {
    TreasuryWallet legacy = legacyRow(DERIVED_ADDRESS, null, TreasuryWalletStatus.ARCHIVED);
    stubKmsMintHappyPath();
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(legacy));
    when(saveTreasuryWalletPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    service.execute(command);

    verify(applicationEventPublisher).publishEvent(any(TreasuryWalletProvisionedEvent.class));
    verify(kmsKeyLifecyclePort, times(1)).createKey();
  }

  @Test
  void c4_alreadyProvisionedActive_andAliasMatchesRow_throws_andNoMint() {
    TreasuryWallet existing =
        legacyRow(DERIVED_ADDRESS, "existing-kms-id", TreasuryWalletStatus.ACTIVE);
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(existing));
    when(kmsKeyLifecyclePort.describeAlias("reward-treasury"))
        .thenReturn(new AliasTargetInfo(KmsKeyState.ENABLED, "existing-kms-id"));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(TreasuryWalletAlreadyProvisionedException.class);

    verifyNoMintHappened();
  }

  @Test
  void c4_aliasRepairMode_whenAliasPointsToPendingDeletion_andNoMint() {
    TreasuryWallet existing =
        legacyRow(DERIVED_ADDRESS, "existing-kms-id", TreasuryWalletStatus.ACTIVE);
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(existing));
    when(kmsKeyLifecyclePort.describeAlias("reward-treasury"))
        .thenReturn(new AliasTargetInfo(KmsKeyState.PENDING_DELETION, "stale-kms-id"));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    ProvisionTreasuryKeyResult result = service.execute(command);

    assertThat(result.kmsKeyId()).isEqualTo("existing-kms-id");
    ArgumentCaptor<TreasuryWalletProvisionedEvent> captor =
        ArgumentCaptor.forClass(TreasuryWalletProvisionedEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().aliasRepairMode()).isTrue();

    verifyNoMintHappened();
  }

  @Test
  void c4_aliasRepairMode_whenAliasEnabledButTargetMismatch_andNoMint() {
    TreasuryWallet existing =
        legacyRow(DERIVED_ADDRESS, "existing-kms-id", TreasuryWalletStatus.ACTIVE);
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(existing));
    when(kmsKeyLifecyclePort.describeAlias("reward-treasury"))
        .thenReturn(new AliasTargetInfo(KmsKeyState.ENABLED, "drift-kms-id"));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    ProvisionTreasuryKeyResult result = service.execute(command);

    assertThat(result.kmsKeyId()).isEqualTo("existing-kms-id");
    ArgumentCaptor<TreasuryWalletProvisionedEvent> captor =
        ArgumentCaptor.forClass(TreasuryWalletProvisionedEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().aliasRepairMode()).isTrue();

    verifyNoMintHappened();
  }

  @Test
  void c4_r5_reactivated_whenAliasDisabledAndTargetMatches() {
    TreasuryWallet existing =
        legacyRow(DERIVED_ADDRESS, "existing-kms-id", TreasuryWalletStatus.ACTIVE);
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(existing));
    when(kmsKeyLifecyclePort.describeAlias("reward-treasury"))
        .thenReturn(new AliasTargetInfo(KmsKeyState.DISABLED, "existing-kms-id"));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    ProvisionTreasuryKeyResult result = service.execute(command);

    assertThat(result.kmsKeyId()).isEqualTo("existing-kms-id");
    ArgumentCaptor<TreasuryWalletReactivatedEvent> captor =
        ArgumentCaptor.forClass(TreasuryWalletReactivatedEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().walletAlias()).isEqualTo("reward-treasury");
    assertThat(captor.getValue().kmsKeyId()).isEqualTo("existing-kms-id");
    assertThat(captor.getValue().walletAddress()).isEqualTo(DERIVED_ADDRESS);

    verify(applicationEventPublisher, never())
        .publishEvent(any(TreasuryWalletProvisionedEvent.class));
    verify(applicationEventPublisher, never())
        .publishEvent(any(TreasuryWalletKeyReplacedEvent.class));
    verifyNoMintHappened();
  }

  @Test
  void c4_aliasRepair_whenAliasDisabledButTargetMismatch_andNoMint() {
    TreasuryWallet existing =
        legacyRow(DERIVED_ADDRESS, "existing-kms-id", TreasuryWalletStatus.ACTIVE);
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(existing));
    when(kmsKeyLifecyclePort.describeAlias("reward-treasury"))
        .thenReturn(new AliasTargetInfo(KmsKeyState.DISABLED, "different-kms-id"));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    ProvisionTreasuryKeyResult result = service.execute(command);

    assertThat(result.kmsKeyId()).isEqualTo("existing-kms-id");
    ArgumentCaptor<TreasuryWalletProvisionedEvent> captor =
        ArgumentCaptor.forClass(TreasuryWalletProvisionedEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().aliasRepairMode()).isTrue();

    verify(applicationEventPublisher, never())
        .publishEvent(any(TreasuryWalletReactivatedEvent.class));
    verify(applicationEventPublisher, never())
        .publishEvent(any(TreasuryWalletKeyReplacedEvent.class));
    verifyNoMintHappened();
  }

  @Test
  void c4_concern2_replaceKeyNoDispose_whenAliasPendingDeletionAndTargetMatches() {
    TreasuryWallet existing =
        legacyRow(DERIVED_ADDRESS, "existing-kms-id", TreasuryWalletStatus.ACTIVE);
    stubKmsMintHappyPath();
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(existing));
    when(kmsKeyLifecyclePort.describeAlias("reward-treasury"))
        .thenReturn(new AliasTargetInfo(KmsKeyState.PENDING_DELETION, "existing-kms-id"));
    when(saveTreasuryWalletPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    service.execute(command);

    ArgumentCaptor<TreasuryWalletKeyReplacedEvent> captor =
        ArgumentCaptor.forClass(TreasuryWalletKeyReplacedEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().disposeOldKey()).isFalse();
    assertThat(captor.getValue().oldKmsKeyId()).isEqualTo("existing-kms-id");
    assertThat(captor.getValue().newKmsKeyId()).isEqualTo("kms-key-id");
    verify(kmsKeyLifecyclePort, times(1)).createKey();

    verify(applicationEventPublisher, never())
        .publishEvent(any(TreasuryWalletReactivatedEvent.class));
    verify(applicationEventPublisher, never())
        .publishEvent(any(TreasuryWalletProvisionedEvent.class));
  }

  @Test
  void c5_reEnableSameKey_disabledActive_publishesReactivated_andNoMint() {
    TreasuryWallet disabled =
        legacyRow(DERIVED_ADDRESS, "existing-kms-id", TreasuryWalletStatus.DISABLED).toBuilder()
            .disabledAt(LocalDateTime.parse("2025-12-01T00:00:00"))
            .build();
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(disabled));
    when(saveTreasuryWalletPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    service.execute(command);

    verify(applicationEventPublisher).publishEvent(any(TreasuryWalletReactivatedEvent.class));

    verifyNoMintHappened();
  }

  @Test
  void c6_replaceKey_sameAddrArchived_disposeFalse() {
    TreasuryWallet archived =
        legacyRow(DERIVED_ADDRESS, "old-kms-id", TreasuryWalletStatus.ARCHIVED);
    stubKmsMintHappyPath();
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(archived));
    when(saveTreasuryWalletPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    service.execute(command);

    ArgumentCaptor<TreasuryWalletKeyReplacedEvent> captor =
        ArgumentCaptor.forClass(TreasuryWalletKeyReplacedEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().disposeOldKey()).isFalse();
    assertThat(captor.getValue().oldKmsKeyId()).isEqualTo("old-kms-id");
    assertThat(captor.getValue().newKmsKeyId()).isEqualTo("kms-key-id");
    verify(kmsKeyLifecyclePort, times(1)).createKey();
  }

  @Test
  void c7_replaceKey_diffAddrActive_disposeTrue() {
    String otherAddress = "0x" + "9".repeat(40);
    TreasuryWallet existing = legacyRow(otherAddress, "old-kms-id", TreasuryWalletStatus.ACTIVE);
    stubKmsMintHappyPath();
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(existing));
    when(saveTreasuryWalletPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    service.execute(command);

    ArgumentCaptor<TreasuryWalletKeyReplacedEvent> captor =
        ArgumentCaptor.forClass(TreasuryWalletKeyReplacedEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().disposeOldKey()).isTrue();
    verify(kmsKeyLifecyclePort, times(1)).createKey();
  }

  @Test
  void c8_replaceKey_diffAddrDisabled_disposeTrue() {
    String otherAddress = "0x" + "9".repeat(40);
    TreasuryWallet existing = legacyRow(otherAddress, "old-kms-id", TreasuryWalletStatus.DISABLED);
    stubKmsMintHappyPath();
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(existing));
    when(saveTreasuryWalletPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    service.execute(command);

    ArgumentCaptor<TreasuryWalletKeyReplacedEvent> captor =
        ArgumentCaptor.forClass(TreasuryWalletKeyReplacedEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().disposeOldKey()).isTrue();
    verify(kmsKeyLifecyclePort, times(1)).createKey();
  }

  @Test
  void c9_replaceKey_diffAddrArchived_disposeFalse() {
    String otherAddress = "0x" + "9".repeat(40);
    TreasuryWallet existing = legacyRow(otherAddress, "old-kms-id", TreasuryWalletStatus.ARCHIVED);
    stubKmsMintHappyPath();
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(existing));
    when(saveTreasuryWalletPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    service.execute(command);

    ArgumentCaptor<TreasuryWalletKeyReplacedEvent> captor =
        ArgumentCaptor.forClass(TreasuryWalletKeyReplacedEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().disposeOldKey()).isFalse();
    verify(kmsKeyLifecyclePort, times(1)).createKey();
  }

  @Test
  void c10_backfillDiffAddrActive_overwritesAddress() {
    String otherAddress = "0x" + "9".repeat(40);
    TreasuryWallet legacy = legacyRow(otherAddress, null, TreasuryWalletStatus.ACTIVE);
    stubKmsMintHappyPath();
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(legacy));
    when(saveTreasuryWalletPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    service.execute(command);

    ArgumentCaptor<TreasuryWallet> captor = ArgumentCaptor.forClass(TreasuryWallet.class);
    verify(saveTreasuryWalletPort).save(captor.capture());
    assertThat(captor.getValue().getWalletAddress()).isEqualToIgnoringCase(DERIVED_ADDRESS);
    verify(kmsKeyLifecyclePort, times(1)).createKey();
  }

  @Test
  void c11_backfillDiffAddrDisabled_promotesActive() {
    String otherAddress = "0x" + "9".repeat(40);
    TreasuryWallet legacy = legacyRow(otherAddress, null, TreasuryWalletStatus.DISABLED);
    stubKmsMintHappyPath();
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(legacy));
    when(saveTreasuryWalletPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    service.execute(command);

    verify(applicationEventPublisher).publishEvent(any(TreasuryWalletProvisionedEvent.class));
    verify(kmsKeyLifecyclePort, times(1)).createKey();
  }

  @Test
  void c12_backfillDiffAddrArchived_promotesActive() {
    String otherAddress = "0x" + "9".repeat(40);
    TreasuryWallet legacy = legacyRow(otherAddress, null, TreasuryWalletStatus.ARCHIVED);
    stubKmsMintHappyPath();
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(legacy));
    when(saveTreasuryWalletPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    service.execute(command);

    verify(applicationEventPublisher).publishEvent(any(TreasuryWalletProvisionedEvent.class));
    verify(kmsKeyLifecyclePort, times(1)).createKey();
  }

  // ---------- audit de-dup (I1) ----------

  /**
   * Regression for the double-audit bug: when the delegate (or its rollback synchronizer) has
   * already written a failure audit row and signalled that via {@code failureAuditWritten}, the
   * outer catch block must not write a second row with the exception class name.
   */
  @Test
  void outerCatch_skipsAudit_whenDelegateAlreadyAudited() {
    ProvisionTreasuryKeyTransactionalDelegate spyDelegate = Mockito.spy(delegate);
    Mockito.doAnswer(
            inv -> {
              AtomicBoolean failureAuditWritten = inv.getArgument(2);
              failureAuditWritten.set(true);
              throw new TransactionSystemException("commit returned STATUS_UNKNOWN");
            })
        .when(spyDelegate)
        .lockedCommit(any(), anyString(), any(), eq(false));
    ProvisionTreasuryKeyService spyService =
        new ProvisionTreasuryKeyService(spyDelegate, treasuryAuditRecorder);

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    assertThatThrownBy(() -> spyService.execute(command))
        .isInstanceOf(TransactionSystemException.class);

    // The outer catch must not write a second audit row when the delegate has already audited.
    verify(treasuryAuditRecorder, never())
        .record(eq(1L), anyString(), eq(false), eq("TransactionSystemException"));
  }

  // ---------- PR #177 R6-C — fresh-INSERT race retry ----------

  /**
   * On {@link DataIntegrityViolationException} from the first {@code lockedCommit} the service must
   * invoke {@code lockedCommit} again with {@code isRaceRetry=true} so {@code
   * handleExistingProvisionedRow} short-circuits to {@link
   * TreasuryWalletAlreadyProvisionedException}.
   */
  @Test
  void executionService_onDIV_retriesWithRaceRetryFlag() {
    ProvisionTreasuryKeyTransactionalDelegate spyDelegate = Mockito.spy(delegate);
    Mockito.doThrow(new DataIntegrityViolationException("UNIQUE alias violation"))
        .when(spyDelegate)
        .lockedCommit(any(), anyString(), any(), eq(false));
    Mockito.doAnswer(
            inv -> {
              AtomicBoolean retryFlag = inv.getArgument(2);
              retryFlag.set(true);
              throw new TreasuryWalletAlreadyProvisionedException(
                  "treasury wallet fresh-INSERT race lost for alias 'reward-treasury'");
            })
        .when(spyDelegate)
        .lockedCommit(any(), anyString(), any(), eq(true));

    ProvisionTreasuryKeyService spyService =
        new ProvisionTreasuryKeyService(spyDelegate, treasuryAuditRecorder);
    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    assertThatThrownBy(() -> spyService.execute(command))
        .isInstanceOf(TreasuryWalletAlreadyProvisionedException.class);

    verify(spyDelegate, times(1)).lockedCommit(any(), anyString(), any(), eq(false));
    verify(spyDelegate, times(1)).lockedCommit(any(), anyString(), any(), eq(true));
    // The delegate's fast-path wrote the FRESH_PROVISION_RACE audit; outer catch must not
    // double-record with the exception's class name.
    verify(treasuryAuditRecorder, never())
        .record(eq(1L), anyString(), eq(false), eq("TreasuryWalletAlreadyProvisionedException"));
  }

  /**
   * If the race retry itself throws something the delegate did not audit (e.g. an unexpected
   * RuntimeException), the outer catch must write the audit with the exception class name and
   * re-throw.
   */
  @Test
  void executionService_onDIV_retryThrowsUnexpected_escalatesWithAudit() {
    ProvisionTreasuryKeyTransactionalDelegate spyDelegate = Mockito.spy(delegate);
    Mockito.doThrow(new DataIntegrityViolationException("UNIQUE alias violation"))
        .when(spyDelegate)
        .lockedCommit(any(), anyString(), any(), eq(false));
    Mockito.doThrow(new IllegalStateException("unexpected during retry"))
        .when(spyDelegate)
        .lockedCommit(any(), anyString(), any(), eq(true));

    ProvisionTreasuryKeyService spyService =
        new ProvisionTreasuryKeyService(spyDelegate, treasuryAuditRecorder);
    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    assertThatThrownBy(() -> spyService.execute(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("unexpected during retry");

    verify(treasuryAuditRecorder)
        .record(eq(1L), eq(DERIVED_ADDRESS), eq(false), eq("IllegalStateException"));
  }

  /**
   * Drive the delegate directly with an existing ACTIVE row + isRaceRetry=true and assert it does
   * NOT call {@code describeAlias} and instead throws ALREADY_PROVISIONED with audit reason {@code
   * FRESH_PROVISION_RACE}.
   */
  @Test
  void delegateLockedCommit_raceRetryActiveRow_skipsDescribeAndThrows409() {
    TreasuryWallet existing = legacyRow(DERIVED_ADDRESS, "kms-key-id", TreasuryWalletStatus.ACTIVE);
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(existing));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(7L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    AtomicBoolean failureAuditWritten = new AtomicBoolean(false);

    assertThatThrownBy(
            () -> delegate.lockedCommit(command, DERIVED_ADDRESS, failureAuditWritten, true))
        .isInstanceOf(TreasuryWalletAlreadyProvisionedException.class);

    assertThat(failureAuditWritten).isTrue();
    verify(kmsKeyLifecyclePort, never()).describeAlias(anyString());
    verify(treasuryAuditRecorder)
        .record(eq(7L), eq(DERIVED_ADDRESS), eq(false), eq("FRESH_PROVISION_RACE"));
  }

  // ---------- helpers ----------

  private void stubKmsMintHappyPath() {
    when(kmsKeyLifecyclePort.createKey()).thenReturn("kms-key-id");
    when(kmsKeyLifecyclePort.getParametersForImport("kms-key-id"))
        .thenReturn(new KmsKeyLifecyclePort.ImportParams(new byte[] {1}, new byte[] {2}));
    when(kmsKeyMaterialWrapperPort.wrap(any(byte[].class), any(byte[].class)))
        .thenReturn(new byte[] {3});
    when(signDigestPort.signDigest(anyString(), any(byte[].class), anyString()))
        .thenReturn(new Vrs(new byte[32], new byte[32], (byte) 27));
  }

  private void stubKmsMintHappyPathExceptSign() {
    when(kmsKeyLifecyclePort.createKey()).thenReturn("kms-key-id");
    when(kmsKeyLifecyclePort.getParametersForImport("kms-key-id"))
        .thenReturn(new KmsKeyLifecyclePort.ImportParams(new byte[] {1}, new byte[] {2}));
    when(kmsKeyMaterialWrapperPort.wrap(any(byte[].class), any(byte[].class)))
        .thenReturn(new byte[] {3});
  }

  private void verifyNoMintHappened() {
    verify(kmsKeyLifecyclePort, never()).createKey();
    verify(kmsKeyLifecyclePort, never()).getParametersForImport(anyString());
    verify(kmsKeyLifecyclePort, never())
        .importKeyMaterial(anyString(), any(byte[].class), any(byte[].class));
    verify(signDigestPort, never()).signDigest(anyString(), any(byte[].class), anyString());
    verify(kmsKeyLifecyclePort, never()).disableKey(anyString());
    verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(anyString(), anyInt());
  }

  private static TreasuryWallet legacyRow(
      String address, String kmsKeyId, TreasuryWalletStatus status) {
    return TreasuryWallet.builder()
        .id(42L)
        .walletAlias("reward-treasury")
        .walletAddress(address)
        .kmsKeyId(kmsKeyId)
        .status(status)
        .keyOrigin(TreasuryKeyOrigin.IMPORTED)
        .createdAt(LocalDateTime.parse("2025-12-01T00:00:00"))
        .updatedAt(LocalDateTime.parse("2025-12-01T00:00:00"))
        .build();
  }
}
