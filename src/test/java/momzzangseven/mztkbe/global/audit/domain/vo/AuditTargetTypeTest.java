package momzzangseven.mztkbe.global.audit.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AuditTargetType enum 단위 테스트")
class AuditTargetTypeTest {

  @Test
  @DisplayName("AuditTargetType 의 values() 를 호출하면, TREASURY_KEY 와 WEB3_TRANSACTION 두 상수만 존재한다")
  void enum_containsExpectedValues() {
    assertThat(AuditTargetType.values())
        .containsExactlyInAnyOrder(AuditTargetType.TREASURY_KEY, AuditTargetType.WEB3_TRANSACTION);
  }

  @Test
  @DisplayName("'TREASURY_KEY'/'WEB3_TRANSACTION' 문자열로 valueOf 를 호출하면, 각각 대응되는 enum 상수를 반환한다")
  void valueOf_returnsMatchingEnum() {
    assertThat(AuditTargetType.valueOf("TREASURY_KEY")).isEqualTo(AuditTargetType.TREASURY_KEY);
    assertThat(AuditTargetType.valueOf("WEB3_TRANSACTION"))
        .isEqualTo(AuditTargetType.WEB3_TRANSACTION);
  }
}
