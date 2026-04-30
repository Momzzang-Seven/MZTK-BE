package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link LoadTreasuryWalletService} — covers [M-90], [M-91].
 *
 * <p>Aspect-driven admin audit ({@code @AdminOnly}) is verified by the integration test [E-2]; this
 * unit test focuses on the use-case body (port delegation, projection, input validation).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoadTreasuryWalletService 단위 테스트")
class LoadTreasuryWalletServiceTest {

  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2024-06-01T12:00:00Z"), ZoneOffset.UTC);
  private static final String ALIAS = TreasuryRole.REWARD.toAlias();
  private static final Long OPERATOR = 7L;

  @Mock private LoadTreasuryWalletPort loadTreasuryWalletPort;

  @InjectMocks private LoadTreasuryWalletService service;

  @Nested
  @DisplayName("A. 정상 경로")
  class HappyPath {

    @Test
    @DisplayName("[M-90a] execute — 지갑이 있으면 Optional<TreasuryWalletView>로 매핑")
    void execute_walletPresent_returnsView() {
      TreasuryWallet wallet =
          TreasuryWallet.provision(
              ALIAS, "kms-id", "0xDeadBeef" + "0".repeat(32), TreasuryRole.REWARD, FIXED);
      when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.of(wallet));

      Optional<TreasuryWalletView> result = service.execute(ALIAS, OPERATOR);

      assertThat(result).isPresent();
      TreasuryWalletView view = result.get();
      assertThat(view.walletAlias()).isEqualTo(ALIAS);
      assertThat(view.role()).isEqualTo(TreasuryRole.REWARD);
      assertThat(view.kmsKeyId()).isEqualTo("kms-id");
      assertThat(view.status()).isEqualTo(TreasuryWalletStatus.ACTIVE);
    }

    @Test
    @DisplayName("[M-90b] execute — 지갑이 없으면 Optional.empty 반환")
    void execute_walletAbsent_returnsEmpty() {
      when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.empty());

      Optional<TreasuryWalletView> result = service.execute(ALIAS, OPERATOR);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("B. 입력 검증 실패")
  class InputValidation {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("[M-91] execute — null/blank walletAlias → Web3InvalidInputException")
    void execute_nullOrBlankAlias_throwsWeb3InvalidInput(String alias) {
      assertThatThrownBy(() -> service.execute(alias, OPERATOR))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("walletAlias");

      verify(loadTreasuryWalletPort, never()).loadByAlias(anyString());
    }
  }
}
