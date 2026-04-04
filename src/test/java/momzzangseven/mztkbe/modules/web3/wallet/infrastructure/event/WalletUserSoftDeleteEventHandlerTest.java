package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import momzzangseven.mztkbe.modules.account.domain.event.UserSoftDeletedEvent;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.RecordWalletEventPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletEvent;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletUserSoftDeleteEventHandler unit test")
class WalletUserSoftDeleteEventHandlerTest {

  @Mock private LoadWalletPort loadWalletPort;
  @Mock private SaveWalletPort saveWalletPort;
  @Mock private RecordWalletEventPort recordWalletEventPort;
  @Mock private EntityManager entityManager;

  @InjectMocks private WalletUserSoftDeleteEventHandler handler;

  @Test
  @DisplayName("[M-157] handles account UserSoftDeletedEvent and marks wallet USER_DELETED")
  void handleUserSoftDeleted_activeWallet_marksUserDeleted() {
    // given
    UserWallet activeWallet = activeWallet(1L, 10L, "0xabc");
    when(loadWalletPort.findWalletsByUserIdAndStatus(10L, WalletStatus.ACTIVE))
        .thenReturn(List.of(activeWallet));
    when(saveWalletPort.save(any(UserWallet.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // when
    handler.handleUserSoftDeleted(new UserSoftDeletedEvent(10L));

    // then
    ArgumentCaptor<UserWallet> walletCaptor = ArgumentCaptor.forClass(UserWallet.class);
    verify(saveWalletPort).save(walletCaptor.capture());
    assertThat(walletCaptor.getValue().getStatus()).isEqualTo(WalletStatus.USER_DELETED);

    verify(entityManager).flush();
    verify(recordWalletEventPort).record(any(WalletEvent.class));
  }

  @Test
  @DisplayName("handles null userId gracefully")
  void handleUserSoftDeleted_nullUserId_doesNothing() {
    handler.handleUserSoftDeleted(new UserSoftDeletedEvent(null));

    verify(loadWalletPort, never()).findWalletsByUserIdAndStatus(any(), any(WalletStatus.class));
    verify(saveWalletPort, never()).save(any(UserWallet.class));
  }

  @Test
  @DisplayName("handles no wallets found")
  void handleUserSoftDeleted_noWallets_doesNothing() {
    when(loadWalletPort.findWalletsByUserIdAndStatus(10L, WalletStatus.ACTIVE))
        .thenReturn(List.of());

    handler.handleUserSoftDeleted(new UserSoftDeletedEvent(10L));

    verify(saveWalletPort, never()).save(any(UserWallet.class));
    verify(recordWalletEventPort, never()).record(any(WalletEvent.class));
  }

  private UserWallet activeWallet(Long id, Long userId, String address) {
    return UserWallet.builder()
        .id(id)
        .userId(userId)
        .walletAddress(address)
        .status(WalletStatus.ACTIVE)
        .registeredAt(Instant.now().minusSeconds(86400))
        .build();
  }
}
