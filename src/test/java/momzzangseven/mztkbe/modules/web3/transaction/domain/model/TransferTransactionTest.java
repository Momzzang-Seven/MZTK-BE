package momzzangseven.mztkbe.modules.web3.transaction.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TransferTransactionTest {

  @Test
  void builder_setsAllCoreFields() {
    LocalDateTime now = LocalDateTime.now();

    TransferTransaction tx =
        TransferTransaction.builder()
            .id(1L)
            .idempotencyKey("idem-1")
            .referenceType(Web3ReferenceType.USER_TO_USER)
            .referenceId("ref-1")
            .fromUserId(10L)
            .toUserId(20L)
            .fromAddress("0x" + "a".repeat(40))
            .toAddress("0x" + "b".repeat(40))
            .amountWei(BigInteger.TEN)
            .nonce(3L)
            .txType(Web3TxType.EIP7702)
            .status(Web3TxStatus.CREATED)
            .createdAt(now)
            .updatedAt(now)
            .build();

    assertThat(tx.getId()).isEqualTo(1L);
    assertThat(tx.getReferenceType()).isEqualTo(Web3ReferenceType.USER_TO_USER);
    assertThat(tx.getAmountWei()).isEqualTo(BigInteger.TEN);
    assertThat(tx.getTxType()).isEqualTo(Web3TxType.EIP7702);
  }
}
