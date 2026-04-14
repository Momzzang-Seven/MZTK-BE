package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;
import org.junit.jupiter.api.Test;

class Web3TransactionAuditEntityTest {

  @Test
  void builder_preservesCreatedAt_whenProvided() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 4, 8, 9, 0);
    Web3TransactionAuditEntity entity =
        Web3TransactionAuditEntity.builder()
            .web3TransactionId(10L)
            .eventType(Web3TransactionAuditEventType.STATE_CHANGE)
            .createdAt(createdAt)
            .build();

    assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
  }

  @Test
  void builder_preservesDetailJsonAndRpcAlias() {
    Web3TransactionAuditEntity entity =
        Web3TransactionAuditEntity.builder()
            .web3TransactionId(10L)
            .eventType(Web3TransactionAuditEventType.BROADCAST)
            .rpcAlias("main")
            .detailJson("{\"ok\":true}")
            .build();

    assertThat(entity.getRpcAlias()).isEqualTo("main");
    assertThat(entity.getDetailJson()).contains("ok");
  }
}
