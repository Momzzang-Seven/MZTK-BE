package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferPrepareStatus;
import org.junit.jupiter.api.Test;

class Web3TransferPrepareEntityTest {

  @Test
  void onCreate_setsDefaultStatusAndTimestamps() {
    Web3TransferPrepareEntity entity =
        Web3TransferPrepareEntity.builder()
            .prepareId("prepare-1")
            .fromUserId(7L)
            .referenceType(TokenTransferReferenceType.USER_TO_USER)
            .referenceId("101")
            .idempotencyKey("domain:QUESTION_REWARD:101:7")
            .authorityAddress("0x" + "a".repeat(40))
            .toAddress("0x" + "b".repeat(40))
            .amountWei(BigInteger.TEN)
            .authorityNonce(1L)
            .delegateTarget("0x" + "c".repeat(40))
            .authExpiresAt(LocalDateTime.now().plusMinutes(10))
            .payloadHashToSign("0x" + "d".repeat(64))
            .salt("0x" + "e".repeat(64))
            .build();

    entity.onCreate();

    assertThat(entity.getStatus()).isEqualTo(TransferPrepareStatus.CREATED);
    assertThat(entity.getCreatedAt()).isNotNull();
    assertThat(entity.getUpdatedAt()).isNotNull();
  }

  @Test
  void onUpdate_updatesUpdatedAt() {
    Web3TransferPrepareEntity entity =
        Web3TransferPrepareEntity.builder()
            .prepareId("prepare-1")
            .fromUserId(7L)
            .referenceType(TokenTransferReferenceType.USER_TO_USER)
            .referenceId("101")
            .idempotencyKey("domain:QUESTION_REWARD:101:7")
            .authorityAddress("0x" + "a".repeat(40))
            .toAddress("0x" + "b".repeat(40))
            .amountWei(BigInteger.TEN)
            .authorityNonce(1L)
            .delegateTarget("0x" + "c".repeat(40))
            .authExpiresAt(LocalDateTime.now().plusMinutes(10))
            .payloadHashToSign("0x" + "d".repeat(64))
            .salt("0x" + "e".repeat(64))
            .status(TransferPrepareStatus.CREATED)
            .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
            .build();

    entity.onUpdate();

    assertThat(entity.getUpdatedAt()).isAfter(LocalDateTime.of(2026, 1, 1, 0, 0));
  }
}
