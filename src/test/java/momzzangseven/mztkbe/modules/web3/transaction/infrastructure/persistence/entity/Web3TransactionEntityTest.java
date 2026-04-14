package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;
import org.junit.jupiter.api.Test;

class Web3TransactionEntityTest {

  @Test
  void onCreate_setsDefaultStatusAndType() {
    Web3TransactionEntity entity =
        Web3TransactionEntity.builder()
            .idempotencyKey("idem-1")
            .referenceType(Web3ReferenceType.USER_TO_USER)
            .referenceId("ref-1")
            .fromAddress("0x" + "a".repeat(40))
            .toAddress("0x" + "b".repeat(40))
            .amountWei(BigInteger.ONE)
            .build();

    entity.onCreate();

    assertThat(entity.getStatus()).isEqualTo(Web3TxStatus.CREATED);
    assertThat(entity.getTxType()).isEqualTo(Web3TxType.EIP1559);
  }

  @Test
  void onCreate_keepsExplicitStatusAndType() {
    Web3TransactionEntity entity =
        Web3TransactionEntity.builder()
            .idempotencyKey("idem-1")
            .referenceType(Web3ReferenceType.USER_TO_USER)
            .referenceId("ref-1")
            .fromAddress("0x" + "a".repeat(40))
            .toAddress("0x" + "b".repeat(40))
            .amountWei(BigInteger.ONE)
            .status(Web3TxStatus.SIGNED)
            .txType(Web3TxType.EIP7702)
            .build();

    entity.onCreate();

    assertThat(entity.getStatus()).isEqualTo(Web3TxStatus.SIGNED);
    assertThat(entity.getTxType()).isEqualTo(Web3TxType.EIP7702);
  }
}
