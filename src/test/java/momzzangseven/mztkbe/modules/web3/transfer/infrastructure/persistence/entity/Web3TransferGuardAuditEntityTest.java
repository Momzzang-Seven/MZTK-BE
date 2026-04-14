package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferGuardAuditReason;
import org.junit.jupiter.api.Test;

class Web3TransferGuardAuditEntityTest {

  @Test
  void builder_storesFields() {
    Web3TransferGuardAuditEntity entity =
        Web3TransferGuardAuditEntity.builder()
            .userId(7L)
            .clientIp("127.0.0.1")
            .domainType(DomainReferenceType.QUESTION_REWARD)
            .referenceId("101")
            .reason(TransferGuardAuditReason.REQUEST_RESOLVED_MISMATCH)
            .requestedAmountWei(BigInteger.TEN)
            .resolvedAmountWei(BigInteger.ONE)
            .build();

    assertThat(entity.getDomainType()).isEqualTo(DomainReferenceType.QUESTION_REWARD);
    assertThat(entity.getReason()).isEqualTo(TransferGuardAuditReason.REQUEST_RESOLVED_MISMATCH);
  }

  @Test
  void builder_preservesCreatedAt_whenProvided() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 4, 8, 9, 0);
    Web3TransferGuardAuditEntity entity =
        Web3TransferGuardAuditEntity.builder().createdAt(createdAt).build();

    assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
  }
}
