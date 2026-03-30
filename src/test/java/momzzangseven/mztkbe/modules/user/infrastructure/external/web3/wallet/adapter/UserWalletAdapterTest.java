package momzzangseven.mztkbe.modules.user.infrastructure.external.web3.wallet.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetActiveWalletAddressUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserWalletAdapter 단위 테스트")
class UserWalletAdapterTest {

  @Mock private GetActiveWalletAddressUseCase getActiveWalletAddressUseCase;

  @InjectMocks private UserWalletAdapter adapter;

  @Test
  @DisplayName("[M-20] ACTIVE 지갑이 있으면 loadActiveWalletAddress가 Optional<String>을 반환한다")
  void loadActiveWalletAddress_activeWalletExists_returnsAddress() {
    // given
    String expectedAddress = "0xabc1234567890abcdef1234567890abcdef123456";
    given(getActiveWalletAddressUseCase.execute(1L)).willReturn(Optional.of(expectedAddress));

    // when
    Optional<String> result = adapter.loadActiveWalletAddress(1L);

    // then
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(expectedAddress);
  }

  @Test
  @DisplayName("[M-21] ACTIVE 지갑이 없으면 Optional.empty()를 반환한다")
  void loadActiveWalletAddress_noActiveWallet_returnsEmpty() {
    // given
    given(getActiveWalletAddressUseCase.execute(1L)).willReturn(Optional.empty());

    // when
    Optional<String> result = adapter.loadActiveWalletAddress(1L);

    // then
    assertThat(result).isEmpty();
  }
}
