package momzzangseven.mztkbe.global.security.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Map;
import momzzangseven.mztkbe.global.audit.application.port.out.RecordAdminAuditPort;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.AuditLogSerializer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AdminOnlyAspectTest {

  @Mock private RecordAdminAuditPort recordAdminAuditPort;
  @Mock private AuditLogSerializer auditLogSerializer;
  @Mock private ProceedingJoinPoint joinPoint;
  @Mock private MethodSignature methodSignature;

  private AdminOnlyAspect aspect;

  @BeforeEach
  void setUp() {
    aspect = new AdminOnlyAspect(recordAdminAuditPort, auditLogSerializer);
    lenient()
        .when(auditLogSerializer.normalize(anyMap()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void around_withAdminRole_recordsSuccessAuditAndSanitizedArguments() throws Throwable {
    Method method =
        DummyAdminMethods.class.getDeclaredMethod(
            "adminAction", Long.class, String.class, String.class, Payload.class);
    AdminOnly adminOnly = method.getAnnotation(AdminOnly.class);

    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs())
        .thenReturn(new Object[] {1L, "target-1", "raw-secret", new Payload("alice", "k", 3)});
    when(joinPoint.proceed()).thenReturn("OK");
    setAuthentication("ROLE_ADMIN");

    Object result = aspect.around(joinPoint, adminOnly);

    assertThat(result).isEqualTo("OK");
    ArgumentCaptor<RecordAdminAuditPort.AuditCommand> captor =
        ArgumentCaptor.forClass(RecordAdminAuditPort.AuditCommand.class);
    verify(recordAdminAuditPort).record(captor.capture());

    RecordAdminAuditPort.AuditCommand command = captor.getValue();
    assertThat(command.operatorId()).isEqualTo(1L);
    assertThat(command.success()).isTrue();
    assertThat(command.targetId()).isEqualTo("target-1");
    assertThat(command.detail()).containsEntry("success", true);
    assertThat(command.detail()).containsEntry("failureReason", null);
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
  void around_withoutAuthentication_allowsExecutionAndRecordsAudit() throws Throwable {
    Method method =
        DummyAdminMethods.class.getDeclaredMethod(
            "adminAction", Long.class, String.class, String.class, Payload.class);
    AdminOnly adminOnly = method.getAnnotation(AdminOnly.class);

    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs())
        .thenReturn(new Object[] {1L, "target-2", "raw-secret", new Payload("bob", "k2", 5)});
    when(joinPoint.proceed()).thenReturn("NO_AUTH_OK");
    SecurityContextHolder.clearContext();

    Object result = aspect.around(joinPoint, adminOnly);

    assertThat(result).isEqualTo("NO_AUTH_OK");
    verify(recordAdminAuditPort).record(any(RecordAdminAuditPort.AuditCommand.class));
  }

  @Test
  void around_whenProceedThrows_rethrowsAndRecordsFailure() throws Throwable {
    Method method =
        DummyAdminMethods.class.getDeclaredMethod(
            "adminAction", Long.class, String.class, String.class, Payload.class);
    AdminOnly adminOnly = method.getAnnotation(AdminOnly.class);

    RuntimeException boom = new IllegalStateException("boom");
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs())
        .thenReturn(new Object[] {1L, "target-3", "raw-secret", new Payload("carol", "k3", 7)});
    when(joinPoint.proceed()).thenThrow(boom);
    setAuthentication("ROLE_ADMIN");

    assertThatThrownBy(() -> aspect.around(joinPoint, adminOnly)).isSameAs(boom);

    ArgumentCaptor<RecordAdminAuditPort.AuditCommand> captor =
        ArgumentCaptor.forClass(RecordAdminAuditPort.AuditCommand.class);
    verify(recordAdminAuditPort).record(captor.capture());
    assertThat(captor.getValue().success()).isFalse();
    assertThat(captor.getValue().detail()).containsEntry("failureReason", "IllegalStateException");
  }

  @Test
  void around_whenOperatorExpressionIsNotNumeric_throwsAuthenticationError() throws Throwable {
    Method method = DummyAdminMethods.class.getDeclaredMethod("nonNumericOperator", Long.class);
    AdminOnly adminOnly = method.getAnnotation(AdminOnly.class);

    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs()).thenReturn(new Object[] {1L});

    assertThatThrownBy(() -> aspect.around(joinPoint, adminOnly))
        .isInstanceOf(UserNotAuthenticatedException.class)
        .hasMessageContaining("operatorId must resolve to a number");

    verify(recordAdminAuditPort, never()).record(any(RecordAdminAuditPort.AuditCommand.class));
  }

  @Test
  void around_whenNonAdminAuthentication_throwsBusinessException() throws Throwable {
    Method method =
        DummyAdminMethods.class.getDeclaredMethod(
            "adminAction", Long.class, String.class, String.class, Payload.class);
    AdminOnly adminOnly = method.getAnnotation(AdminOnly.class);

    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs())
        .thenReturn(new Object[] {1L, "target-4", "raw-secret", new Payload("dave", "k4", 9)});
    setAuthentication("ROLE_USER");

    assertThatThrownBy(() -> aspect.around(joinPoint, adminOnly))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Unauthorized access");

    verify(recordAdminAuditPort, never()).record(any(RecordAdminAuditPort.AuditCommand.class));
  }

  @Test
  void around_whenAuditRecordingFails_returnsOriginalResult() throws Throwable {
    Method method = DummyAdminMethods.class.getDeclaredMethod("blankTarget", Long.class);
    AdminOnly adminOnly = method.getAnnotation(AdminOnly.class);

    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs()).thenReturn(new Object[] {1L});
    when(joinPoint.proceed()).thenReturn("OK_WITH_AUDIT_FAILURE");
    doThrow(new RuntimeException("audit-failed"))
        .when(recordAdminAuditPort)
        .record(any(RecordAdminAuditPort.AuditCommand.class));
    setAuthentication("ROLE_ADMIN");

    Object result = aspect.around(joinPoint, adminOnly);

    assertThat(result).isEqualTo("OK_WITH_AUDIT_FAILURE");
  }

  @Test
  void around_withBlankTargetExpression_recordsNullTargetId() throws Throwable {
    Method method = DummyAdminMethods.class.getDeclaredMethod("blankTarget", Long.class);
    AdminOnly adminOnly = method.getAnnotation(AdminOnly.class);

    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs()).thenReturn(new Object[] {1L});
    when(joinPoint.proceed()).thenReturn("OK");
    setAuthentication("ROLE_ADMIN");

    aspect.around(joinPoint, adminOnly);

    ArgumentCaptor<RecordAdminAuditPort.AuditCommand> captor =
        ArgumentCaptor.forClass(RecordAdminAuditPort.AuditCommand.class);
    verify(recordAdminAuditPort).record(captor.capture());
    assertThat(captor.getValue().targetId()).isNull();
  }

  @Test
  void around_whenOperatorIdNonPositive_throwsAuthenticationError() throws Throwable {
    Method method =
        DummyAdminMethods.class.getDeclaredMethod(
            "adminAction", Long.class, String.class, String.class, Payload.class);
    AdminOnly adminOnly = method.getAnnotation(AdminOnly.class);

    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs())
        .thenReturn(new Object[] {0L, "target-5", "raw-secret", new Payload("erin", "k5", 1)});

    assertThatThrownBy(() -> aspect.around(joinPoint, adminOnly))
        .isInstanceOf(UserNotAuthenticatedException.class);
  }

  private void setAuthentication(String authority) {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("admin", "pw", authority));
  }

  private static class DummyAdminMethods {

    @AdminOnly(actionType = "DELETE", targetType = "USER", operatorId = "#p0", targetId = "#p1")
    String adminAction(Long operatorId, String targetId, String privateKey, Payload payload) {
      return "OK";
    }

    @AdminOnly(actionType = "READ", targetType = "USER", operatorId = "'abc'")
    String nonNumericOperator(Long operatorId) {
      return "NO";
    }

    @AdminOnly(actionType = "LIST", targetType = "USER", operatorId = "#p0", targetId = "")
    String blankTarget(Long operatorId) {
      return "OK";
    }
  }

  private record Payload(String name, String secretKey, int count) {}
}
