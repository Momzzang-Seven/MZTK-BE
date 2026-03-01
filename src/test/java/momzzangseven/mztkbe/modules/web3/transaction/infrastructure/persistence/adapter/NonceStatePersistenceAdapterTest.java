package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3NonceStateEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3NonceStateJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NonceStatePersistenceAdapterTest {

  @Mock private Web3NonceStateJpaRepository repository;
  @Mock private Web3CoreProperties web3CoreProperties;

  private NonceStatePersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new NonceStatePersistenceAdapter(repository, web3CoreProperties);
  }

  @Test
  void reserveNextNonce_throws_whenAddressBlank() {
    assertThatThrownBy(() -> adapter.reserveNextNonce(" "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("fromAddress is required");
  }

  @Test
  void reserveNextNonce_returnsAndIncrementsExistingNonce() {
    Web3NonceStateEntity entity =
        Web3NonceStateEntity.builder().fromAddress("0x" + "a".repeat(40)).nextNonce(5L).build();
    when(repository.findByFromAddressForUpdate("0x" + "a".repeat(40)))
        .thenReturn(Optional.of(entity));

    long reserved = adapter.reserveNextNonce("0x" + "A".repeat(40));

    assertThat(reserved).isEqualTo(5L);
    assertThat(entity.getNextNonce()).isEqualTo(6L);
    verify(repository).save(entity);
  }

  @Test
  void reserveNextNonce_initializesState_whenMissing() {
    when(repository.findByFromAddressForUpdate("0x" + "b".repeat(40))).thenReturn(Optional.empty());

    long reserved = adapter.reserveNextNonce("0x" + "B".repeat(40));

    ArgumentCaptor<Web3NonceStateEntity> captor =
        ArgumentCaptor.forClass(Web3NonceStateEntity.class);
    verify(repository).save(captor.capture());
    assertThat(reserved).isEqualTo(0L);
    assertThat(captor.getValue().getFromAddress()).isEqualTo("0x" + "b".repeat(40));
    assertThat(captor.getValue().getNextNonce()).isEqualTo(1L);
  }
}
