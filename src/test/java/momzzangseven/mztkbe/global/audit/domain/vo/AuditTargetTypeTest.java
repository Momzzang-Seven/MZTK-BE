package momzzangseven.mztkbe.global.audit.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AuditTargetType enum 단위 테스트")
class AuditTargetTypeTest {

  @Test
  @DisplayName("AuditTargetType 의 values() 를 호출하면, 모든 상수가 존재한다")
  void enum_containsExpectedValues() {
    assertThat(AuditTargetType.values())
        .containsExactlyInAnyOrder(
            AuditTargetType.TREASURY_KEY,
            AuditTargetType.WEB3_TRANSACTION,
            AuditTargetType.ADMIN_ACCOUNT,
            AuditTargetType.QNA_ESCROW_QUESTION);
  }

  @Test
  @DisplayName("'TREASURY_KEY'/'WEB3_TRANSACTION' 문자열로 valueOf 를 호출하면, 각각 대응되는 enum 상수를 반환한다")
  void valueOf_returnsMatchingEnum() {
    assertThat(AuditTargetType.valueOf("TREASURY_KEY")).isEqualTo(AuditTargetType.TREASURY_KEY);
    assertThat(AuditTargetType.valueOf("WEB3_TRANSACTION"))
        .isEqualTo(AuditTargetType.WEB3_TRANSACTION);
  }

  @Test
  @DisplayName("[M-72] ADMIN_ACCOUNT exists in AuditTargetType enum")
  void valueOf_adminAccount_returnsAdminAccount() {
    assertThat(AuditTargetType.valueOf("ADMIN_ACCOUNT")).isEqualTo(AuditTargetType.ADMIN_ACCOUNT);
  }

  @Test
  @DisplayName("[M-73] AuditTargetType values() includes ADMIN_ACCOUNT alongside existing values")
  void values_includesAdminAccountAlongsideExistingValues() {
    AuditTargetType[] values = AuditTargetType.values();

    assertThat(values).hasSize(4);
    assertThat(values)
        .containsExactlyInAnyOrder(
            AuditTargetType.TREASURY_KEY,
            AuditTargetType.WEB3_TRANSACTION,
            AuditTargetType.ADMIN_ACCOUNT,
            AuditTargetType.QNA_ESCROW_QUESTION);
  }

  @Test
  @DisplayName("[M-74] AuditTargetType.ADMIN_ACCOUNT name() returns \"ADMIN_ACCOUNT\"")
  void adminAccount_name_returnsAdminAccount() {
    assertThat(AuditTargetType.ADMIN_ACCOUNT.name()).isEqualTo("ADMIN_ACCOUNT");
  }

  @Test
  @DisplayName("QNA_ESCROW_QUESTION name() returns expected enum constant")
  void qnaEscrowQuestion_name_returnsExpectedName() {
    assertThat(AuditTargetType.QNA_ESCROW_QUESTION.name()).isEqualTo("QNA_ESCROW_QUESTION");
  }
}
