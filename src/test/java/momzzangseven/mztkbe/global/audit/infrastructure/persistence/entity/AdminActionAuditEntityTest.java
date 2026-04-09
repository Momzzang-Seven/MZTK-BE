package momzzangseven.mztkbe.global.audit.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AdminActionAuditEntity 단위 테스트")
class AdminActionAuditEntityTest {

  @Test
  @DisplayName("Builder 로 모든 필드를 설정하면, 엔티티의 getter 들은 동일한 값을 반환한다")
  void builder_setsAllFields() {
    OffsetDateTime now = OffsetDateTime.now();

    AdminActionAuditEntity entity =
        AdminActionAuditEntity.builder()
            .id(10L)
            .operatorId(1L)
            .source("WEB3")
            .actionType("TRANSACTION_MARK_SUCCEEDED")
            .targetType("WEB3_TRANSACTION")
            .targetId("tx-1")
            .success(true)
            .detailJson("{\"k\":\"v\"}")
            .createdAt(now)
            .build();

    assertThat(entity.getId()).isEqualTo(10L);
    assertThat(entity.getOperatorId()).isEqualTo(1L);
    assertThat(entity.getSource()).isEqualTo("WEB3");
    assertThat(entity.getActionType()).isEqualTo("TRANSACTION_MARK_SUCCEEDED");
    assertThat(entity.getTargetType()).isEqualTo("WEB3_TRANSACTION");
    assertThat(entity.getTargetId()).isEqualTo("tx-1");
    assertThat(entity.isSuccess()).isTrue();
    assertThat(entity.getDetailJson()).isEqualTo("{\"k\":\"v\"}");
    assertThat(entity.getCreatedAt()).isEqualTo(now);
  }
}
