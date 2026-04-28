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
import java.time.ZoneOffset;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAddressMismatchException;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAlreadyProvisionedException;
import momzzangseven.mztkbe.global.error.web3.TreasuryPrivateKeyInvalidException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyMaterialWrapperPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.RecordTreasuryProvisionAuditPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SignDigestPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
  @Mock private RecordTreasuryProvisionAuditPort recordTreasuryProvisionAuditPort;

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
            recordTreasuryProvisionAuditPort,
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
  void execute_throws_whenAlreadyProvisioned() {
    when(loadTreasuryWalletPort.existsByAliasOrAddress("reward-treasury", DERIVED_ADDRESS))
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
  void execute_drivesKmsLifecycleAndPersists_onSuccess() {
    when(loadTreasuryWalletPort.existsByAliasOrAddress(anyString(), anyString())).thenReturn(false);
    when(kmsKeyLifecyclePort.createKey()).thenReturn("kms-key-id");
    when(kmsKeyLifecyclePort.getParametersForImport("kms-key-id"))
        .thenReturn(
            new KmsKeyLifecyclePort.ImportParams(new byte[] {1, 2, 3}, new byte[] {4, 5, 6}));
    when(kmsKeyMaterialWrapperPort.wrap(any(byte[].class), any(byte[].class)))
        .thenReturn(new byte[] {7, 8, 9});
    when(signDigestPort.signDigest(eq("kms-key-id"), any(byte[].class), eq(DERIVED_ADDRESS)))
        .thenReturn(new Vrs(new byte[32], new byte[32], (byte) 27));
    when(saveTreasuryWalletPort.save(any(TreasuryWallet.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    ProvisionTreasuryKeyResult result = service.execute(command);

    assertThat(result.walletAlias()).isEqualTo("reward-treasury");
    assertThat(result.kmsKeyId()).isEqualTo("kms-key-id");
    assertThat(result.walletAddress()).isEqualTo(DERIVED_ADDRESS);
    verify(kmsKeyLifecyclePort)
        .importKeyMaterial(eq("kms-key-id"), any(byte[].class), any(byte[].class));
    verify(kmsKeyLifecyclePort).createAlias("reward-treasury", "kms-key-id");
    verify(saveTreasuryWalletPort).save(any(TreasuryWallet.class));
  }

  @Test
  void execute_cleansUpKmsKey_whenSignDigestFails() {
    when(loadTreasuryWalletPort.existsByAliasOrAddress(anyString(), anyString())).thenReturn(false);
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
  }

  @Test
  void execute_recordsAudit_onSuccess() {
    when(loadTreasuryWalletPort.existsByAliasOrAddress(anyString(), anyString())).thenReturn(false);
    when(kmsKeyLifecyclePort.createKey()).thenReturn("kms-key-id");
    when(kmsKeyLifecyclePort.getParametersForImport("kms-key-id"))
        .thenReturn(new KmsKeyLifecyclePort.ImportParams(new byte[] {1}, new byte[] {2}));
    when(kmsKeyMaterialWrapperPort.wrap(any(byte[].class), any(byte[].class)))
        .thenReturn(new byte[] {3});
    when(signDigestPort.signDigest(anyString(), any(byte[].class), anyString()))
        .thenReturn(new Vrs(new byte[32], new byte[32], (byte) 27));
    when(saveTreasuryWalletPort.save(any(TreasuryWallet.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    ProvisionTreasuryKeyCommand command =
        new ProvisionTreasuryKeyCommand(1L, PRIVATE_KEY_HEX, TreasuryRole.REWARD, DERIVED_ADDRESS);

    service.execute(command);

    verify(recordTreasuryProvisionAuditPort)
        .record(new RecordTreasuryProvisionAuditPort.AuditCommand(1L, DERIVED_ADDRESS, true, null));
  }
}
