package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Web3TransactionAuditEntityTest {

  @Test
  void onCreate_setsCreatedAt_whenNull() {
    Web3TransactionAuditEntity entity =
        Web3TransactionAuditEntity.builder()
            .web3TransactionId(10L)
            .eventType("STATE_CHANGE")
            .build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isNotNull();
  }

  @Test
  void builder_preservesDetailJsonAndRpcAlias() {
    Web3TransactionAuditEntity entity =
        Web3TransactionAuditEntity.builder()
            .web3TransactionId(10L)
            .eventType("BROADCAST")
            .rpcAlias("main")
            .detailJson("{\"ok\":true}")
            .build();

    assertThat(entity.getRpcAlias()).isEqualTo("main");
    assertThat(entity.getDetailJson()).contains("ok");
  }
}
