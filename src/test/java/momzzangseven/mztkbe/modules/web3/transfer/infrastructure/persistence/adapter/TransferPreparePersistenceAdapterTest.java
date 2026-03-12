package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferPrepare;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferPrepareStatus;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3TransferPrepareEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.Web3TransferPrepareJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferPreparePersistenceAdapterTest {

  @Mock private Web3TransferPrepareJpaRepository repository;

  private TransferPreparePersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new TransferPreparePersistenceAdapter(repository);
  }

  @Test
  void create_throws_whenPrepareIdMissing() {
    TransferPrepare prepare = basePrepare().prepareId(" ").build();

    assertThatThrownBy(() -> adapter.create(prepare))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("create requires prepareId");
  }

  @Test
  void update_throws_whenEntityNotFound() {
    TransferPrepare prepare = basePrepare().build();
    when(repository.findById("prepare-1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adapter.update(prepare))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transfer prepare not found");
  }

  @Test
  void findFirstByIdempotencyKey_mapsEntityToDomain() {
    Web3TransferPrepareEntity entity =
        Web3TransferPrepareEntity.builder()
            .prepareId("prepare-1")
            .fromUserId(7L)
            .toUserId(22L)
            .acceptedCommentId(201L)
            .referenceType(TokenTransferReferenceType.USER_TO_USER)
            .referenceId("101")
            .idempotencyKey("domain:QUESTION_REWARD:101:7")
            .authorityAddress("0x" + "a".repeat(40))
            .toAddress("0x" + "b".repeat(40))
            .amountWei(BigInteger.TEN)
            .authorityNonce(5L)
            .delegateTarget("0x" + "c".repeat(40))
            .authExpiresAt(LocalDateTime.now().plusMinutes(5))
            .payloadHashToSign("0x" + "d".repeat(64))
            .salt("0x" + "e".repeat(64))
            .status(TransferPrepareStatus.CREATED)
            .build();
    when(repository.findFirstByIdempotencyKeyOrderByCreatedAtDesc("domain:QUESTION_REWARD:101:7"))
        .thenReturn(Optional.of(entity));

    Optional<TransferPrepare> result =
        adapter.findFirstByIdempotencyKey("domain:QUESTION_REWARD:101:7");

    assertThat(result).isPresent();
    assertThat(result.get().getPrepareId()).isEqualTo("prepare-1");
    assertThat(result.get().getStatus()).isEqualTo(TransferPrepareStatus.CREATED);
  }

  @Test
  void create_mapsAndPersists() {
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    TransferPrepare created = adapter.create(basePrepare().build());

    assertThat(created.getPrepareId()).isEqualTo("prepare-1");
    assertThat(created.getAuthorityNonce()).isEqualTo(5L);
  }

  private TransferPrepare.TransferPrepareBuilder basePrepare() {
    return TransferPrepare.builder()
        .prepareId("prepare-1")
        .fromUserId(7L)
        .toUserId(22L)
        .acceptedCommentId(201L)
        .referenceType(TokenTransferReferenceType.USER_TO_USER)
        .referenceId("101")
        .idempotencyKey("domain:QUESTION_REWARD:101:7")
        .authorityAddress("0x" + "a".repeat(40))
        .toAddress("0x" + "b".repeat(40))
        .amountWei(BigInteger.TEN)
        .authorityNonce(5L)
        .delegateTarget("0x" + "c".repeat(40))
        .authExpiresAt(LocalDateTime.now().plusMinutes(5))
        .payloadHashToSign("0x" + "d".repeat(64))
        .salt("0x" + "e".repeat(64))
        .status(TransferPrepareStatus.CREATED);
  }
}
