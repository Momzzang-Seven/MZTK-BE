package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.DescribeKmsKeyPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link VerifyTreasuryWalletForSignService} — covers [M-92], [M-93].
 *
 * <p>The service is a pre-sign gate: it must throw a typed {@link TreasuryWalletStateException} for
 * every signability failure so callers never reach the {@code SignDigestPort}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VerifyTreasuryWalletForSignService 단위 테스트")
class VerifyTreasuryWalletForSignServiceTest {

  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2024-06-01T12:00:00Z"), ZoneOffset.UTC);
  private static final String ALIAS = TreasuryRole.REWARD.toAlias();
  private static final String KMS_ID = "kms-key-id";
  private static final String ADDRESS = "0xDeadBeef" + "0".repeat(32);

  @Mock private LoadTreasuryWalletPort loadTreasuryWalletPort;
  @Mock private DescribeKmsKeyPort describeKmsKeyPort;

  @InjectMocks private VerifyTreasuryWalletForSignService service;

  private TreasuryWallet activeWalletWithKms() {
    return TreasuryWallet.provision(ALIAS, KMS_ID, ADDRESS, TreasuryRole.REWARD, FIXED);
  }

  @Nested
  @DisplayName("A. 정상 경로 — ACTIVE + ENABLED")
  class HappyPath {

    @Test
    @DisplayName("[M-92] execute — ACTIVE 지갑 + ENABLED KMS 키는 예외 없음")
    void execute_activeWalletAndEnabledKey_passes() {
      when(loadTreasuryWalletPort.loadByAlias(ALIAS))
          .thenReturn(Optional.of(activeWalletWithKms()));
      when(describeKmsKeyPort.describe(KMS_ID)).thenReturn(KmsKeyState.ENABLED);

      assertThatCode(() -> service.execute(ALIAS)).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("B. 게이트 실패")
  class FailureGates {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("[M-93a] null/blank alias → Web3InvalidInputException, 포트 미호출")
    void execute_nullOrBlankAlias_throwsWeb3InvalidInput(String alias) {
      assertThatThrownBy(() -> service.execute(alias))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("walletAlias");

      verify(loadTreasuryWalletPort, never()).loadByAlias(anyString());
      verify(describeKmsKeyPort, never()).describe(anyString());
    }

    @Test
    @DisplayName("[M-93b] 지갑이 없으면 TreasuryWalletStateException")
    void execute_walletNotFound_throwsState() {
      when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.execute(ALIAS))
          .isInstanceOf(TreasuryWalletStateException.class)
          .hasMessageContaining(ALIAS)
          .hasMessageContaining("not found");

      verify(describeKmsKeyPort, never()).describe(anyString());
    }

    @Test
    @DisplayName("[M-93c] 지갑이 DISABLED → assertSignable에서 TreasuryWalletStateException")
    void execute_disabledWallet_throwsState() {
      TreasuryWallet disabled = activeWalletWithKms().disable(FIXED);
      when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.of(disabled));

      assertThatThrownBy(() -> service.execute(ALIAS))
          .isInstanceOf(TreasuryWalletStateException.class)
          .hasMessageContaining("DISABLED");

      verify(describeKmsKeyPort, never()).describe(anyString());
    }

    @Test
    @DisplayName("[M-93d] 지갑이 ARCHIVED → assertSignable에서 TreasuryWalletStateException")
    void execute_archivedWallet_throwsState() {
      TreasuryWallet archived = activeWalletWithKms().disable(FIXED).archive(FIXED);
      when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.of(archived));

      assertThatThrownBy(() -> service.execute(ALIAS))
          .isInstanceOf(TreasuryWalletStateException.class)
          .hasMessageContaining("ARCHIVED");
    }

    @Test
    @DisplayName("[M-93e] 지갑은 ACTIVE인데 kmsKeyId가 null/blank → TreasuryWalletStateException")
    void execute_walletWithoutKmsKey_throwsState() {
      TreasuryWallet noKey = activeWalletWithKms().toBuilder().kmsKeyId(null).build();
      when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.of(noKey));

      assertThatThrownBy(() -> service.execute(ALIAS))
          .isInstanceOf(TreasuryWalletStateException.class)
          .hasMessageContaining("no KMS key");

      verify(describeKmsKeyPort, never()).describe(anyString());
    }

    @ParameterizedTest
    @EnumSource(
        value = KmsKeyState.class,
        names = {"DISABLED", "PENDING_DELETION", "PENDING_IMPORT", "UNAVAILABLE"})
    @DisplayName("[M-93f] KMS 상태가 ENABLED 외이면 TreasuryWalletStateException")
    void execute_kmsKeyNotEnabled_throwsState(KmsKeyState state) {
      when(loadTreasuryWalletPort.loadByAlias(ALIAS))
          .thenReturn(Optional.of(activeWalletWithKms()));
      when(describeKmsKeyPort.describe(KMS_ID)).thenReturn(state);

      assertThatThrownBy(() -> service.execute(ALIAS))
          .isInstanceOf(TreasuryWalletStateException.class)
          .hasMessageContaining(state.name());
    }
  }
}
