package momzzangseven.mztkbe.global.security.aspect;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditSource;
import momzzangseven.mztkbe.modules.web3.token.application.service.ProvisionTreasuryKeyService;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.MarkTransactionSucceededCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.service.MarkTransactionSucceededService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Compile-time / reflection-time guard tests around the {@link AdminOnly} annotation. These tests
 * exist to defend the refactor's core safety property: that {@code auditSource} is mandatory and
 * that the two web3 services route their audits through {@link AuditSource#WEB3}.
 */
@DisplayName("@AdminOnly 어노테이션 컴파일/리플렉션 가드 테스트")
class AdminOnlyAnnotationTest {

  @Test
  @DisplayName("@AdminOnly 의 auditSource 속성을 리플렉션으로 조회하면, default value 가 없어 호출자가 명시를 강제받는다")
  void auditSource_hasNoDefaultValue() throws NoSuchMethodException {
    Method auditSource = AdminOnly.class.getDeclaredMethod("auditSource");

    assertThat(auditSource.getReturnType()).isEqualTo(AuditSource.class);
    assertThat(auditSource.getDefaultValue()).isNull();
  }

  @Test
  @DisplayName(
      "MarkTransactionSucceededService.execute 를 리플렉션으로 조회하면, @AdminOnly(auditSource=WEB3) 메타데이터가 그대로 부착되어 있다")
  void markTransactionSucceededService_isAnnotatedWithWeb3AuditSource()
      throws NoSuchMethodException {
    AdminOnly annotation =
        MarkTransactionSucceededService.class
            .getMethod("execute", MarkTransactionSucceededCommand.class)
            .getAnnotation(AdminOnly.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.actionType()).isEqualTo("TRANSACTION_MARK_SUCCEEDED");
    assertThat(annotation.targetType()).isEqualTo("WEB3_TRANSACTION");
    assertThat(annotation.operatorId()).isEqualTo("#command.operatorId()");
    assertThat(annotation.targetId()).isEqualTo("#command.transactionId()");
    assertThat(annotation.auditSource()).isEqualTo(AuditSource.WEB3);
  }

  @Test
  @DisplayName(
      "ProvisionTreasuryKeyService.execute 를 리플렉션으로 조회하면, @AdminOnly(auditSource=WEB3) 메타데이터가 그대로 부착되어 있다")
  void provisionTreasuryKeyService_isAnnotatedWithWeb3AuditSource() throws NoSuchMethodException {
    AdminOnly annotation =
        ProvisionTreasuryKeyService.class
            .getMethod("execute", Long.class, String.class, String.class)
            .getAnnotation(AdminOnly.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.actionType()).isEqualTo("TREASURY_KEY_PROVISION");
    assertThat(annotation.targetType()).isEqualTo("TREASURY_KEY");
    assertThat(annotation.operatorId()).isEqualTo("#operatorId");
    assertThat(annotation.targetId())
        .isEqualTo("#result != null ? #result.treasuryAddress() : null");
    assertThat(annotation.auditSource()).isEqualTo(AuditSource.WEB3);
  }
}
