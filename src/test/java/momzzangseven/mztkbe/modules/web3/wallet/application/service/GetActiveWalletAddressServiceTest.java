package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetActiveWalletAddressService 단위 테스트")
class GetActiveWalletAddressServiceTest {

  @Mock private LoadWalletPort loadWalletPort;

  @InjectMocks private GetActiveWalletAddressService service;

  private static final Long USER_ID = 1L;
  private static final String WALLET_ADDRESS = "0xabc1234567890abcdef1234567890abcdef123456";

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[M-17] ACTIVE 지갑이 있으면 해당 지갑의 주소를 Optional<String>으로 반환한다")
    void execute_activeWalletExists_returnsAddress() {
      // given
      when(loadWalletPort.findWalletsByUserIdAndStatus(USER_ID, WalletStatus.ACTIVE))
          .thenReturn(
              List.of(
                  UserWallet.builder()
                      .id(1L)
                      .userId(USER_ID)
                      .walletAddress(WALLET_ADDRESS)
                      .status(WalletStatus.ACTIVE)
                      .registeredAt(Instant.now())
                      .build()));

      // when
      Optional<String> result = service.execute(USER_ID);

      // then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(WALLET_ADDRESS);
    }

    @Test
    @DisplayName("[M-19] ACTIVE 지갑이 여러 개이면 첫 번째 주소를 반환한다")
    void execute_multipleActiveWallets_returnsFirstAddress() {
      // given
      String firstAddress = "0x1111111111111111111111111111111111111111";
      String secondAddress = "0x2222222222222222222222222222222222222222";
      when(loadWalletPort.findWalletsByUserIdAndStatus(USER_ID, WalletStatus.ACTIVE))
          .thenReturn(
              List.of(
                  UserWallet.builder()
                      .id(1L)
                      .userId(USER_ID)
                      .walletAddress(firstAddress)
                      .status(WalletStatus.ACTIVE)
                      .registeredAt(Instant.now())
                      .build(),
                  UserWallet.builder()
                      .id(2L)
                      .userId(USER_ID)
                      .walletAddress(secondAddress)
                      .status(WalletStatus.ACTIVE)
                      .registeredAt(Instant.now())
                      .build()));

      // when
      Optional<String> result = service.execute(USER_ID);

      // then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(firstAddress);
    }
  }

  @Nested
  @DisplayName("경계 케이스")
  class BoundaryCases {

    @Test
    @DisplayName("[M-18] ACTIVE 지갑이 없으면 Optional.empty()를 반환한다")
    void execute_noActiveWallet_returnsEmpty() {
      // given
      when(loadWalletPort.findWalletsByUserIdAndStatus(USER_ID, WalletStatus.ACTIVE))
          .thenReturn(List.of());

      // when
      Optional<String> result = service.execute(USER_ID);

      // then
      assertThat(result).isEmpty();
    }
  }
}
