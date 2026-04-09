package momzzangseven.mztkbe.global.audit.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AuditSource enum 단위 테스트")
class AuditSourceTest {

  @Test
  @DisplayName("AuditSource 의 values() 를 호출하면, USER 와 WEB3 두 상수만 존재한다")
  void enum_containsExpectedValues() {
    assertThat(AuditSource.values()).containsExactlyInAnyOrder(AuditSource.USER, AuditSource.WEB3);
  }

  @Test
  @DisplayName("'USER'/'WEB3' 문자열로 valueOf 를 호출하면, 각각 대응되는 enum 상수를 반환한다")
  void valueOf_returnsMatchingEnum() {
    assertThat(AuditSource.valueOf("USER")).isEqualTo(AuditSource.USER);
    assertThat(AuditSource.valueOf("WEB3")).isEqualTo(AuditSource.WEB3);
  }
}
