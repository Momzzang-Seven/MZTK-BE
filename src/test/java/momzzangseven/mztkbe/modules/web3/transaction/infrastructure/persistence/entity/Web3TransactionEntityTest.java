package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class Web3TransactionEntityTest {

  @Test
  void onCreate_setsDefaultStatusTypeAndTimestamps() {
    Web3TransactionEntity entity =
        Web3TransactionEntity.builder()
            .idempotencyKey("idem-1")
            .referenceType(Web3ReferenceType.USER_TO_SERVER)
            .referenceId("ref-1")
            .fromAddress("0x" + "1".repeat(40))
            .toAddress("0x" + "2".repeat(40))
            .amountWei(java.math.BigInteger.TEN)
            .build();

    ReflectionTestUtils.invokeMethod(entity, "onCreate");

    assertThat(entity.getStatus()).isEqualTo(Web3TxStatus.CREATED);
    assertThat(entity.getTxType()).isEqualTo(Web3TxType.EIP1559);
    assertThat(entity.getCreatedAt()).isNotNull();
    assertThat(entity.getUpdatedAt()).isNotNull();
  }

  @Test
  void onUpdate_refreshesUpdatedAt() throws InterruptedException {
    Web3TransactionEntity entity =
        Web3TransactionEntity.builder()
            .idempotencyKey("idem-2")
            .referenceType(Web3ReferenceType.USER_TO_SERVER)
            .referenceId("ref-2")
            .fromAddress("0x" + "1".repeat(40))
            .toAddress("0x" + "2".repeat(40))
            .amountWei(java.math.BigInteger.ONE)
            .status(Web3TxStatus.CREATED)
            .txType(Web3TxType.EIP1559)
            .createdAt(LocalDateTime.now().minusMinutes(1))
            .updatedAt(LocalDateTime.now().minusMinutes(1))
            .build();

    LocalDateTime before = entity.getUpdatedAt();

    ReflectionTestUtils.invokeMethod(entity, "onUpdate");

    assertThat(entity.getUpdatedAt()).isAfter(before);
  }
}
