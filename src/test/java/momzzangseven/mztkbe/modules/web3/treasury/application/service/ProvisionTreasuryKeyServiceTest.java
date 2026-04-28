package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import momzzangseven.mztkbe.global.error.web3.TreasuryPrivateKeyInvalidException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyResult;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryAliasPolicyPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.RecordTreasuryProvisionAuditPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.TreasuryKeyEncryptionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvisionTreasuryKeyServiceTest {

  @Mock private TreasuryKeyEncryptionPort treasuryKeyEncryptionPort;
  @Mock private SaveTreasuryKeyPort saveTreasuryKeyPort;
  @Mock private RecordTreasuryProvisionAuditPort recordTreasuryProvisionAuditPort;
  @Mock private LoadTreasuryAliasPolicyPort loadTreasuryAliasPolicyPort;

  private ProvisionTreasuryKeyService service;

  @BeforeEach
  void setUp() {
    service =
        new ProvisionTreasuryKeyService(
            treasuryKeyEncryptionPort,
            saveTreasuryKeyPort,
            recordTreasuryProvisionAuditPort,
            loadTreasuryAliasPolicyPort);
  }

  @Test
  void execute_throws_whenOperatorIdInvalid() {
    assertThatThrownBy(() -> service.execute(0L, "reward-main", "f".repeat(64)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("operatorId must be positive");
  }

  @Test
  void execute_throws_whenAliasNotAllowlisted() {
    when(loadTreasuryAliasPolicyPort.defaultRewardTreasuryAlias()).thenReturn("reward-main");
    when(loadTreasuryAliasPolicyPort.allowedAliases()).thenReturn(Set.of("ops-main"));

    assertThatThrownBy(() -> service.execute(1L, "reward-main", "f".repeat(64)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("walletAlias must be configured");
  }

  @Test
  void execute_savesEncryptedKey_andRecordsSuccessAudit() {
    when(loadTreasuryAliasPolicyPort.defaultRewardTreasuryAlias()).thenReturn("reward-main");
    when(loadTreasuryAliasPolicyPort.allowedAliases()).thenReturn(Set.of("reward-main"));
    when(treasuryKeyEncryptionPort.generateKeyB64()).thenReturn("kek-base64");
    when(treasuryKeyEncryptionPort.encrypt(anyString(), eq("kek-base64")))
        .thenReturn("encrypted-key");

    ProvisionTreasuryKeyResult result = service.execute(1L, null, "f".repeat(64));

    assertThat(result.treasuryAddress()).startsWith("0x");
    assertThat(result.treasuryPrivateKeyEncrypted()).isEqualTo("encrypted-key");
    assertThat(result.treasuryKeyEncryptionKeyB64()).isEqualTo("kek-base64");
    verify(saveTreasuryKeyPort)
        .upsert(eq("reward-main"), eq(result.treasuryAddress()), eq("encrypted-key"));

    ArgumentCaptor<RecordTreasuryProvisionAuditPort.AuditCommand> captor =
        ArgumentCaptor.forClass(RecordTreasuryProvisionAuditPort.AuditCommand.class);
    verify(recordTreasuryProvisionAuditPort).record(captor.capture());
    assertThat(captor.getValue().operatorId()).isEqualTo(1L);
    assertThat(captor.getValue().success()).isTrue();
  }

  @Test
  void execute_throws_whenPrivateKeyLengthInvalid() {
    when(loadTreasuryAliasPolicyPort.defaultRewardTreasuryAlias()).thenReturn("reward-main");
    when(loadTreasuryAliasPolicyPort.allowedAliases()).thenReturn(Set.of("reward-main"));

    assertThatThrownBy(() -> service.execute(1L, "reward-main", "abcd"))
        .isInstanceOf(TreasuryPrivateKeyInvalidException.class)
        .hasMessageContaining("treasuryPrivateKey must be 32-byte hex");
  }

  @Test
  void execute_throws_whenWalletAliasMissing() {
    when(loadTreasuryAliasPolicyPort.defaultRewardTreasuryAlias()).thenReturn(" ");

    assertThatThrownBy(() -> service.execute(1L, null, "f".repeat(64)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("walletAlias is required");
  }

  @Test
  void execute_throws_whenPrivateKeyRequired() {
    when(loadTreasuryAliasPolicyPort.defaultRewardTreasuryAlias()).thenReturn("reward-main");
    when(loadTreasuryAliasPolicyPort.allowedAliases()).thenReturn(Set.of("reward-main"));

    assertThatThrownBy(() -> service.execute(1L, "reward-main", " "))
        .isInstanceOf(TreasuryPrivateKeyInvalidException.class)
        .hasMessageContaining("treasuryPrivateKey is required");
  }

  @Test
  void execute_throws_whenPrivateKeyNotHex() {
    when(loadTreasuryAliasPolicyPort.defaultRewardTreasuryAlias()).thenReturn("reward-main");
    when(loadTreasuryAliasPolicyPort.allowedAliases()).thenReturn(Set.of("reward-main"));

    assertThatThrownBy(() -> service.execute(1L, "reward-main", "z".repeat(64)))
        .isInstanceOf(TreasuryPrivateKeyInvalidException.class)
        .hasMessageContaining("treasuryPrivateKey must be hex string");
  }
}
