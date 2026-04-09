package momzzangseven.mztkbe.global.audit.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuditSourceTest {

  @Test
  void enum_containsExpectedValues() {
    assertThat(AuditSource.values()).containsExactlyInAnyOrder(AuditSource.USER, AuditSource.WEB3);
  }

  @Test
  void valueOf_returnsMatchingEnum() {
    assertThat(AuditSource.valueOf("USER")).isEqualTo(AuditSource.USER);
    assertThat(AuditSource.valueOf("WEB3")).isEqualTo(AuditSource.WEB3);
  }
}
