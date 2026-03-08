package momzzangseven.mztkbe.modules.web3.admin.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class Web3AdminActionAuditEntityTest {

  @Test
  void builderAndGetters_work() {
    Web3AdminActionAuditEntity entity =
        Web3AdminActionAuditEntity.builder()
            .id(1L)
            .operatorId(10L)
            .actionType("MARK_SUCCEEDED")
            .targetType("WEB3_TRANSACTION")
            .targetId("44")
            .success(true)
            .detailJson("{\"a\":1}")
            .createdAt(LocalDateTime.of(2026, 3, 1, 10, 0))
            .build();

    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getOperatorId()).isEqualTo(10L);
    assertThat(entity.isSuccess()).isTrue();
  }

  @Test
  void onCreate_setsCreatedAt_whenMissing() {
    Web3AdminActionAuditEntity entity = Web3AdminActionAuditEntity.builder().build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isNotNull();
  }
}
