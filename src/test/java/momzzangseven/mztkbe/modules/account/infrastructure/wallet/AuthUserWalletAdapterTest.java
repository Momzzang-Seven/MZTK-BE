package momzzangseven.mztkbe.modules.account.infrastructure.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetActiveWalletAddressUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthUserWalletAdapter unit test")
class AuthUserWalletAdapterTest {

  @Mock private GetActiveWalletAddressUseCase getActiveWalletAddressUseCase;

  @InjectMocks private AuthUserWalletAdapter adapter;

  @Test
  @DisplayName("[M-151] loadActiveWalletAddress returns address when wallet exists")
  void loadActiveWalletAddress_walletExists_returnsAddress() {
    when(getActiveWalletAddressUseCase.execute(1L)).thenReturn(Optional.of("0xabc123def456"));

    Optional<String> result = adapter.loadActiveWalletAddress(1L);

    assertThat(result).isPresent().contains("0xabc123def456");
    verify(getActiveWalletAddressUseCase).execute(1L);
  }

  @Test
  @DisplayName("[M-152] loadActiveWalletAddress returns empty when no wallet registered")
  void loadActiveWalletAddress_noWallet_returnsEmpty() {
    when(getActiveWalletAddressUseCase.execute(2L)).thenReturn(Optional.empty());

    Optional<String> result = adapter.loadActiveWalletAddress(2L);

    assertThat(result).isEmpty();
    verify(getActiveWalletAddressUseCase).execute(2L);
  }
}
