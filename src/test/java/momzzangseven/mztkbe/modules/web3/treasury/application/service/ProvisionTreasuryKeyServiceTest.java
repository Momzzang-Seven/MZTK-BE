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
import java.util.Optional;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAddressMismatchException;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAlreadyProvisionedException;
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
            treasuryAuditRecorder,
            applicationEventPublisher,
            fixed);
    service =
        new ProvisionTreasuryKeyService(
            delegate,
            kmsKeyLifecyclePort,
            kmsKeyMaterialWrapperPort,
            signDigestPort,
            treasuryAuditRecorder);
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

  // ---------- Pre-mint failures ----------

  @Test
  void execute_cleansUpPreMintedKey_whenSignDigestFails() {
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
    verify(loadTreasuryWalletPort, never()).loadByAliasForUpdate(anyString());
    verify(saveTreasuryWalletPort, never()).save(any());
  }

  @Test
  void execute_cleansUpPreMintedKey_whenImportKeyMaterialFails() {
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
    verify(loadTreasuryWalletPort, never()).loadByAliasForUpdate(anyString());
  }

  @Test
  void execute_sanitySignTerminalKmsFailure_propagatesNonRetryable() {
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
  }

  @Test
  void c4_alreadyProvisionedActive_throws() {
    TreasuryWallet existing =
        legacyRow(DERIVED_ADDRESS, "existing-kms-id", TreasuryWalletStatus.ACTIVE);
    stubKmsMintHappyPath();
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(existing));
    when(kmsKeyLifecyclePort.describeAliasTarget("reward-treasury"))
        .thenReturn(KmsKeyState.ENABLED);

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(TreasuryWalletAlreadyProvisionedException.class);

    // Pre-minted key (unused) gets cleaned up
    verify(kmsKeyLifecyclePort).disableKey("kms-key-id");
  }

  @Test
  void c4_aliasRepairMode_whenAliasPointsToPendingDeletion() {
    TreasuryWallet existing =
        legacyRow(DERIVED_ADDRESS, "existing-kms-id", TreasuryWalletStatus.ACTIVE);
    stubKmsMintHappyPath();
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(existing));
    when(kmsKeyLifecyclePort.describeAliasTarget("reward-treasury"))
        .thenReturn(KmsKeyState.PENDING_DELETION);

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    ProvisionTreasuryKeyResult result = service.execute(command);

    assertThat(result.kmsKeyId()).isEqualTo("existing-kms-id");
    ArgumentCaptor<TreasuryWalletProvisionedEvent> captor =
        ArgumentCaptor.forClass(TreasuryWalletProvisionedEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().aliasRepairMode()).isTrue();
    // The C4 repair path doesn't attach the pre-minted key — outer finally cleans it up
    verify(kmsKeyLifecyclePort).disableKey("kms-key-id");
  }

  @Test
  void c5_reEnableSameKey_disabledActive_publishesReactivated() {
    TreasuryWallet disabled =
        legacyRow(DERIVED_ADDRESS, "existing-kms-id", TreasuryWalletStatus.DISABLED).toBuilder()
            .disabledAt(LocalDateTime.parse("2025-12-01T00:00:00"))
            .build();
    stubKmsMintHappyPath();
    when(loadTreasuryWalletPort.loadByAliasForUpdate("reward-treasury"))
        .thenReturn(Optional.of(disabled));
    when(saveTreasuryWalletPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);
    service.execute(command);

    verify(applicationEventPublisher).publishEvent(any(TreasuryWalletReactivatedEvent.class));
    // Pre-minted key unused → outer finally cleanup
    verify(kmsKeyLifecyclePort).disableKey("kms-key-id");
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
