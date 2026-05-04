package momzzangseven.mztkbe.global.security.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Map;
import momzzangseven.mztkbe.global.audit.application.port.out.RecordAdminAuditPort;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminOnlyAspect 단위 테스트")
class AdminOnlyAspectTest {

  @Mock private RecordAdminAuditPort recordAdminAuditPort;
  @Mock private ProceedingJoinPoint joinPoint;
  @Mock private MethodSignature methodSignature;

  private AdminOnlyAspect aspect;

  private static final RoleHierarchy ROLE_HIERARCHY =
      RoleHierarchyImpl.fromHierarchy(
          """
          ROLE_ADMIN_SEED > ROLE_ADMIN
          ROLE_ADMIN_GENERATED > ROLE_ADMIN
          ROLE_ADMIN > ROLE_TRAINER
          ROLE_TRAINER > ROLE_USER
          """);

  @BeforeEach
  void setUp() {
    aspect = new AdminOnlyAspect(recordAdminAuditPort, ROLE_HIERARCHY);
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName(
      "ROLE_ADMIN 사용자가 호출하면, around 는 어노테이션의 targetType 을 그대로 전달하는 성공 audit 를 기록하고 "
          + "민감 인자(privateKey/secretKey)를 마스킹한다")
  void around_withAdminRole_recordsSuccessAuditAndSanitizedArguments() throws Throwable {
    Method method =
        DummyAdminMethods.class.getDeclaredMethod(
            "adminAction", Long.class, String.class, String.class, Payload.class);
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs())
        .thenReturn(new Object[] {1L, "target-1", "raw-secret", new Payload("alice", "k", 3)});
    when(joinPoint.proceed()).thenReturn("OK");
    setAuthentication("ROLE_ADMIN");

    Object result = aspect.around(joinPoint);

    assertThat(result).isEqualTo("OK");
    ArgumentCaptor<RecordAdminAuditPort.AuditCommand> captor =
        ArgumentCaptor.forClass(RecordAdminAuditPort.AuditCommand.class);
    verify(recordAdminAuditPort).record(captor.capture());

    RecordAdminAuditPort.AuditCommand command = captor.getValue();
    assertThat(command.operatorId()).isEqualTo(1L);
    assertThat(command.targetType()).isEqualTo(AuditTargetType.TREASURY_KEY);
    assertThat(command.success()).isTrue();
    assertThat(command.targetId()).isEqualTo("target-1");
    assertThat(command.detail()).doesNotContainKeys("operatorId", "success", "targetId");
    assertThat(command.detail()).doesNotContainKey("failureReason");
    assertThat(command.detail().get("arguments")).isInstanceOf(Map.class);

    @SuppressWarnings("unchecked")
    Map<String, Object> arguments = (Map<String, Object>) command.detail().get("arguments");
    assertThat(arguments).containsEntry("privateKey", "***");
    assertThat(arguments).containsEntry("targetId", "target-1");
    assertThat(arguments.get("payload")).isInstanceOf(Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) arguments.get("payload");
    assertThat(payload).containsEntry("name", "alice");
    assertThat(payload).containsEntry("secretKey", "***");
    assertThat(payload).containsEntry("count", 3);
  }

  @Test
  @DisplayName("audit=false 메서드는 admin 권한 검증과 비즈니스 실행은 수행하되 audit row를 기록하지 않는다")
  void around_withAuditDisabled_allowsAdminWithoutRecordingAudit() throws Throwable {
    Method method = DummyAdminMethods.class.getDeclaredMethod("readWithoutAudit", Long.class);
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs()).thenReturn(new Object[] {1L});
    when(joinPoint.proceed()).thenReturn("READ_OK");
    setAuthentication("ROLE_ADMIN");

    Object result = aspect.around(joinPoint);

    assertThat(result).isEqualTo("READ_OK");
    verify(recordAdminAuditPort, never()).record(any(RecordAdminAuditPort.AuditCommand.class));
  }

  @Test
  @DisplayName("audit=false 메서드도 non-admin 호출은 거부하고 audit row를 기록하지 않는다")
  void around_withAuditDisabledAndNonAdmin_throwsWithoutAudit() throws Throwable {
    Method method = DummyAdminMethods.class.getDeclaredMethod("readWithoutAudit", Long.class);
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs()).thenReturn(new Object[] {1L});
    setAuthentication("ROLE_USER");

    assertThatThrownBy(() -> aspect.around(joinPoint))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Unauthorized access");
    verify(recordAdminAuditPort, never()).record(any(RecordAdminAuditPort.AuditCommand.class));
  }

  @Test
  @DisplayName(
      "SecurityContext 인증 정보가 없으면, around 는 UserNotAuthenticatedException 을 던지고 audit 를 기록하지 않는다")
  void around_withoutAuthentication_throwsUserNotAuthenticatedException() throws Throwable {
    Method method =
        DummyAdminMethods.class.getDeclaredMethod(
            "adminAction", Long.class, String.class, String.class, Payload.class);
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs())
        .thenReturn(new Object[] {1L, "target-2", "raw-secret", new Payload("bob", "k2", 5)});
    SecurityContextHolder.clearContext();

    assertThatThrownBy(() -> aspect.around(joinPoint))
        .isInstanceOf(UserNotAuthenticatedException.class);

    verify(recordAdminAuditPort, never()).record(any(RecordAdminAuditPort.AuditCommand.class));
  }

  @Test
  @DisplayName(
      "내부 비즈니스 메서드가 예외를 던지면, around 는 예외를 재전파하면서 success=false + failureReason 의 audit 를 기록한다")
  void around_whenProceedThrows_rethrowsAndRecordsFailure() throws Throwable {
    Method method =
        DummyAdminMethods.class.getDeclaredMethod(
            "adminAction", Long.class, String.class, String.class, Payload.class);

    RuntimeException boom = new IllegalStateException("boom");
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs())
        .thenReturn(new Object[] {1L, "target-3", "raw-secret", new Payload("carol", "k3", 7)});
    when(joinPoint.proceed()).thenThrow(boom);
    setAuthentication("ROLE_ADMIN");

    assertThatThrownBy(() -> aspect.around(joinPoint)).isSameAs(boom);

    ArgumentCaptor<RecordAdminAuditPort.AuditCommand> captor =
        ArgumentCaptor.forClass(RecordAdminAuditPort.AuditCommand.class);
    verify(recordAdminAuditPort).record(captor.capture());
    assertThat(captor.getValue().success()).isFalse();
    assertThat(captor.getValue().detail()).containsEntry("failureReason", "IllegalStateException");
  }

  @Test
  @DisplayName(
      "operatorId SpEL 표현식이 숫자가 아닌 값으로 평가되면, around 는 UserNotAuthenticatedException 을 던지고 audit 를 기록하지 않는다")
  void around_whenOperatorExpressionIsNotNumeric_throwsAuthenticationError() throws Throwable {
    Method method = DummyAdminMethods.class.getDeclaredMethod("nonNumericOperator", Long.class);
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs()).thenReturn(new Object[] {1L});

    assertThatThrownBy(() -> aspect.around(joinPoint))
        .isInstanceOf(UserNotAuthenticatedException.class)
        .hasMessageContaining("operatorId must resolve to a number");

    verify(recordAdminAuditPort, never()).record(any(RecordAdminAuditPort.AuditCommand.class));
  }

  @Test
  @DisplayName(
      "ROLE_USER 등 비-admin 으로 인증된 호출이면, around 는 BusinessException(Unauthorized) 을 던지고 audit 를 기록하지 않는다")
  void around_whenNonAdminAuthentication_throwsBusinessException() throws Throwable {
    Method method =
        DummyAdminMethods.class.getDeclaredMethod(
            "adminAction", Long.class, String.class, String.class, Payload.class);
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs())
        .thenReturn(new Object[] {1L, "target-4", "raw-secret", new Payload("dave", "k4", 9)});
    setAuthentication("ROLE_USER");

    assertThatThrownBy(() -> aspect.around(joinPoint))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Unauthorized access");

    verify(recordAdminAuditPort, never()).record(any(RecordAdminAuditPort.AuditCommand.class));
  }

  @Test
  @DisplayName("audit 기록 단계에서 RecordAdminAuditPort 가 예외를 던져도, around 는 비즈니스 로직의 원래 반환값을 그대로 반환한다")
  void around_whenAuditRecordingFails_returnsOriginalResult() throws Throwable {
    Method method = DummyAdminMethods.class.getDeclaredMethod("blankTarget", Long.class);
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs()).thenReturn(new Object[] {1L});
    when(joinPoint.proceed()).thenReturn("OK_WITH_AUDIT_FAILURE");
    doThrow(new RuntimeException("audit-failed"))
        .when(recordAdminAuditPort)
        .record(any(RecordAdminAuditPort.AuditCommand.class));
    setAuthentication("ROLE_ADMIN");

    Object result = aspect.around(joinPoint);

    assertThat(result).isEqualTo("OK_WITH_AUDIT_FAILURE");
  }

  @Test
  @DisplayName(
      "@AdminOnly(targetType=WEB3_TRANSACTION) 메서드 호출이 성공하면, "
          + "around 는 AuditCommand.targetType 에 WEB3_TRANSACTION 을 전달한다")
  void around_passesAnnotationTargetTypeToAuditCommand() throws Throwable {
    Method method = DummyAdminMethods.class.getDeclaredMethod("web3Action", Long.class);
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs()).thenReturn(new Object[] {1L});
    when(joinPoint.proceed()).thenReturn("OK");
    setAuthentication("ROLE_ADMIN");

    aspect.around(joinPoint);

    ArgumentCaptor<RecordAdminAuditPort.AuditCommand> captor =
        ArgumentCaptor.forClass(RecordAdminAuditPort.AuditCommand.class);
    verify(recordAdminAuditPort).record(captor.capture());
    assertThat(captor.getValue().targetType()).isEqualTo(AuditTargetType.WEB3_TRANSACTION);
  }

  @Test
  @DisplayName("@AdminOnly 의 targetId SpEL 표현식이 빈 문자열이면, around 는 audit 의 targetId 를 null 로 기록한다")
  void around_withBlankTargetExpression_recordsNullTargetId() throws Throwable {
    Method method = DummyAdminMethods.class.getDeclaredMethod("blankTarget", Long.class);
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs()).thenReturn(new Object[] {1L});
    when(joinPoint.proceed()).thenReturn("OK");
    setAuthentication("ROLE_ADMIN");

    aspect.around(joinPoint);

    ArgumentCaptor<RecordAdminAuditPort.AuditCommand> captor =
        ArgumentCaptor.forClass(RecordAdminAuditPort.AuditCommand.class);
    verify(recordAdminAuditPort).record(captor.capture());
    assertThat(captor.getValue().targetId()).isNull();
  }

  @Test
  @DisplayName("operatorId SpEL 결과가 0 이하 숫자이면, around 는 UserNotAuthenticatedException 을 던진다")
  void around_whenOperatorIdNonPositive_throwsAuthenticationError() throws Throwable {
    Method method =
        DummyAdminMethods.class.getDeclaredMethod(
            "adminAction", Long.class, String.class, String.class, Payload.class);
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs())
        .thenReturn(new Object[] {0L, "target-5", "raw-secret", new Payload("erin", "k5", 1)});

    assertThatThrownBy(() -> aspect.around(joinPoint))
        .isInstanceOf(UserNotAuthenticatedException.class);
  }

  @Test
  @DisplayName(
      "[M-75] ROLE_ADMIN_SEED 사용자가 호출하면, around 는 RoleHierarchy 를 통해 ROLE_ADMIN 도달을 확인하고 "
          + "실행을 허용하며 성공 audit 를 기록한다")
  void around_withAdminSeedRole_allowsExecutionAndRecordsAudit() throws Throwable {
    // given
    Method method = DummyAdminMethods.class.getDeclaredMethod("blankTarget", Long.class);
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs()).thenReturn(new Object[] {1L});
    when(joinPoint.proceed()).thenReturn("SEED_OK");
    setAuthentication("ROLE_ADMIN_SEED");

    // when
    Object result = aspect.around(joinPoint);

    // then
    assertThat(result).isEqualTo("SEED_OK");
    ArgumentCaptor<RecordAdminAuditPort.AuditCommand> captor =
        ArgumentCaptor.forClass(RecordAdminAuditPort.AuditCommand.class);
    verify(recordAdminAuditPort).record(captor.capture());
    assertThat(captor.getValue().success()).isTrue();
    assertThat(captor.getValue().operatorId()).isEqualTo(1L);
  }

  @Test
  @DisplayName(
      "[M-76] ROLE_ADMIN_GENERATED 사용자가 호출하면, around 는 RoleHierarchy 를 통해 ROLE_ADMIN 도달을 확인하고 "
          + "실행을 허용하며 성공 audit 를 기록한다")
  void around_withAdminGeneratedRole_allowsExecutionAndRecordsAudit() throws Throwable {
    // given
    Method method = DummyAdminMethods.class.getDeclaredMethod("blankTarget", Long.class);
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs()).thenReturn(new Object[] {1L});
    when(joinPoint.proceed()).thenReturn("GENERATED_OK");
    setAuthentication("ROLE_ADMIN_GENERATED");

    // when
    Object result = aspect.around(joinPoint);

    // then
    assertThat(result).isEqualTo("GENERATED_OK");
    ArgumentCaptor<RecordAdminAuditPort.AuditCommand> captor =
        ArgumentCaptor.forClass(RecordAdminAuditPort.AuditCommand.class);
    verify(recordAdminAuditPort).record(captor.capture());
    assertThat(captor.getValue().success()).isTrue();
    assertThat(captor.getValue().operatorId()).isEqualTo(1L);
  }

  @Test
  @DisplayName(
      "[M-77] ROLE_TRAINER 사용자가 호출하면, around 는 RoleHierarchy 를 통해 ROLE_ADMIN 에 도달 불가를 확인하고 "
          + "BusinessException 을 던지며 audit 를 기록하지 않는다")
  void around_withTrainerRole_throwsBusinessExceptionWithoutAudit() throws Throwable {
    // given
    Method method = DummyAdminMethods.class.getDeclaredMethod("blankTarget", Long.class);
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs()).thenReturn(new Object[] {1L});
    setAuthentication("ROLE_TRAINER");

    // when & then
    assertThatThrownBy(() -> aspect.around(joinPoint))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Unauthorized access");

    verify(recordAdminAuditPort, never()).record(any(RecordAdminAuditPort.AuditCommand.class));
  }

  private void setAuthentication(String authority) {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("admin", "pw", authority));
  }

  private static class DummyAdminMethods {

    @AdminOnly(
        actionType = "DELETE",
        targetType = AuditTargetType.TREASURY_KEY,
        operatorId = "#p0",
        targetId = "#p1")
    String adminAction(Long operatorId, String targetId, String privateKey, Payload payload) {
      return "OK";
    }

    @AdminOnly(actionType = "READ", targetType = AuditTargetType.TREASURY_KEY, operatorId = "'abc'")
    String nonNumericOperator(Long operatorId) {
      return "NO";
    }

    @AdminOnly(
        actionType = "LIST",
        targetType = AuditTargetType.TREASURY_KEY,
        operatorId = "#p0",
        targetId = "")
    String blankTarget(Long operatorId) {
      return "OK";
    }

    @AdminOnly(
        actionType = "TRANSACTION_MARK_SUCCEEDED",
        targetType = AuditTargetType.WEB3_TRANSACTION,
        operatorId = "#p0")
    String web3Action(Long operatorId) {
      return "OK";
    }

    @AdminOnly(
        actionType = "READ",
        targetType = AuditTargetType.POST,
        operatorId = "#p0",
        audit = false)
    String readWithoutAudit(Long operatorId) {
      return "READ_OK";
    }
  }

  private record Payload(String name, String secretKey, int count) {}
}
