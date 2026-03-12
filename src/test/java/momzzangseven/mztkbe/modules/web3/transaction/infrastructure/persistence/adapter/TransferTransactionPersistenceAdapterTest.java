package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.TransferTransaction;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferTransactionPersistenceAdapterTest {

  @Mock private Web3TransactionJpaRepository repository;

  private TransferTransactionPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new TransferTransactionPersistenceAdapter(repository);
  }

  @Test
  void findByIdempotencyKey_mapsEntityToDomain() {
    Web3TransactionEntity entity =
        Web3TransactionEntity.builder()
            .id(11L)
            .idempotencyKey("idem-11")
            .referenceType(Web3ReferenceType.USER_TO_USER)
            .referenceId("ref-11")
            .fromUserId(7L)
            .toUserId(22L)
            .fromAddress("0x" + "a".repeat(40))
            .toAddress("0x" + "b".repeat(40))
            .amountWei(BigInteger.TEN)
            .txType(Web3TxType.EIP7702)
            .status(Web3TxStatus.CREATED)
            .build();
    when(repository.findByIdempotencyKey("idem-11")).thenReturn(Optional.of(entity));

    Optional<TransferTransaction> result = adapter.findByIdempotencyKey("idem-11");

    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(11L);
    assertThat(result.get().getReferenceId()).isEqualTo("ref-11");
  }

  @Test
  void createAndFlush_mapsDomainToEntity() {
    TransferTransaction domain =
        TransferTransaction.builder()
            .idempotencyKey("idem-22")
            .referenceType(Web3ReferenceType.USER_TO_SERVER)
            .referenceId("ref-22")
            .fromUserId(7L)
            .toUserId(1L)
            .fromAddress("0x" + "a".repeat(40))
            .toAddress("0x" + "b".repeat(40))
            .amountWei(BigInteger.ONE)
            .nonce(3L)
            .txType(Web3TxType.EIP7702)
            .status(Web3TxStatus.CREATED)
            .authorizationExpiresAt(LocalDateTime.now().plusMinutes(10))
            .build();
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    TransferTransaction created = adapter.createAndFlush(domain);

    ArgumentCaptor<Web3TransactionEntity> captor =
        ArgumentCaptor.forClass(Web3TransactionEntity.class);
    verify(repository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("idem-22");
    assertThat(created.getIdempotencyKey()).isEqualTo("idem-22");
  }
}
