package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3TransactionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionWorkPersistenceAdapterTest {

  @Mock private EntityManager entityManager;
  @Mock private Web3TransactionJpaRepository repository;

  private TransactionWorkPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new TransactionWorkPersistenceAdapter(entityManager, repository);
  }

  @Test
  void claimByStatus_throws_whenStatusNull() {
    assertThatThrownBy(
            () -> adapter.claimByStatus(null, 10, "worker-1", java.time.Duration.ofMinutes(1)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("status is required");
  }

  @Test
  void claimByStatus_throws_whenLimitNonPositive() {
    assertThatThrownBy(
            () ->
                adapter.claimByStatus(
                    Web3TxStatus.CREATED, 0, "worker-1", java.time.Duration.ofMinutes(1)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("limit must be > 0");
  }

  @Test
  void claimByStatus_throws_whenWorkerBlank() {
    assertThatThrownBy(
            () ->
                adapter.claimByStatus(
                    Web3TxStatus.CREATED, 10, " ", java.time.Duration.ofMinutes(1)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("workerId is required");
  }

  @Test
  void assignNonce_throws_whenNonceNegative() {
    assertThatThrownBy(() -> adapter.assignNonce(1L, -1L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("nonce must be >= 0");
  }

  @Test
  void loadById_throws_whenTransactionIdInvalid() {
    assertThatThrownBy(() -> adapter.loadById(0L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");
  }

  @Test
  void loadByReferenceTypeAndReferenceIds_throws_whenReferenceTypeNull() {
    assertThatThrownBy(() -> adapter.loadByReferenceTypeAndReferenceIds(null, List.of("ref-1")))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceType is required");
  }

  @Test
  void markSigned_throws_whenSignedRawTxBlank() {
    assertThatThrownBy(() -> adapter.markSigned(1L, 1L, " ", "0x" + "a".repeat(64)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("signedRawTx is required");
  }

  @Test
  void markPending_throws_whenTxHashBlank() {
    assertThatThrownBy(() -> adapter.markPending(1L, " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("txHash is required");
  }

  @Test
  void loadByReferenceTypeAndReferenceIds_mapsSnapshots() {
    Web3TransactionEntity entity =
        Web3TransactionEntity.builder()
            .id(1L)
            .idempotencyKey("idem-1")
            .referenceType(Web3ReferenceType.USER_TO_USER)
            .referenceId("ref-1")
            .fromUserId(7L)
            .toUserId(22L)
            .fromAddress("0x" + "a".repeat(40))
            .toAddress("0x" + "b".repeat(40))
            .amountWei(BigInteger.TEN)
            .txType(Web3TxType.EIP7702)
            .status(Web3TxStatus.SIGNED)
            .txHash("0x" + "c".repeat(64))
            .build();
    when(repository.findByReferenceTypeAndReferenceIdIn(
            Web3ReferenceType.USER_TO_USER, List.of("ref-1")))
        .thenReturn(List.of(entity));

    List<LoadTransactionPort.TransactionSnapshot> snapshots =
        adapter.loadByReferenceTypeAndReferenceIds(
            Web3ReferenceType.USER_TO_USER, List.of("ref-1"));

    assertThat(snapshots).hasSize(1);
    assertThat(snapshots.getFirst().transactionId()).isEqualTo(1L);
    assertThat(snapshots.getFirst().status()).isEqualTo(Web3TxStatus.SIGNED);
    assertThat(snapshots.getFirst().txHash()).isEqualTo("0x" + "c".repeat(64));
  }
}
