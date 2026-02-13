package momzzangseven.mztkbe.global.security.aspect;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.audit.application.port.out.RecordAdminAuditPort;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.auth.AuthErrorCode;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.modules.web3.transaction.application.audit.AuditLogSerializer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AdminOnlyAspect {

  private static final ExpressionParser SPEL = new SpelExpressionParser();
  private static final DefaultParameterNameDiscoverer PARAM_DISCOVERER =
      new DefaultParameterNameDiscoverer();

  private final RecordAdminAuditPort recordAdminAuditPort;
  private final AuditLogSerializer auditLogSerializer;

  @Around("@annotation(adminOnly)")
  public Object around(ProceedingJoinPoint joinPoint, AdminOnly adminOnly) throws Throwable {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    Object[] args = joinPoint.getArgs();
    StandardEvaluationContext context = evaluationContext(method, args, null, null);

    Long operatorId = evalLong(adminOnly.operatorId(), context);
    validateAdmin(operatorId);

    Object result = null;
    RuntimeException runtimeException = null;
    try {
      result = joinPoint.proceed();
      return result;
    } catch (RuntimeException e) {
      runtimeException = e;
      throw e;
    } finally {
      recordAdminAudit(adminOnly, method, args, operatorId, result, runtimeException);
    }
  }

  private void recordAdminAudit(
      AdminOnly adminOnly,
      Method method,
      Object[] args,
      Long operatorId,
      Object result,
      RuntimeException runtimeException) {
    try {
      StandardEvaluationContext context = evaluationContext(method, args, result, runtimeException);
      String targetId = evalString(adminOnly.targetId(), context);
      boolean success = runtimeException == null;
      String failureReason = success ? null : runtimeException.getClass().getSimpleName();

      Map<String, Object> rawDetail = new LinkedHashMap<>();
      rawDetail.put("method", method.getDeclaringClass().getSimpleName() + "." + method.getName());
      rawDetail.put("operatorId", operatorId);
      rawDetail.put("success", success);
      rawDetail.put("failureReason", failureReason);
      rawDetail.put("arguments", sanitizeArguments(method, args));
      rawDetail.put("targetId", targetId);

      recordAdminAuditPort.record(
          new RecordAdminAuditPort.AuditCommand(
              operatorId,
              adminOnly.actionType(),
              adminOnly.targetType(),
              targetId,
              success,
              auditLogSerializer.normalize(rawDetail)));
    } catch (Exception auditException) {
      log.warn(
          "Failed to record admin audit via aspect: action={}, operatorId={}",
          adminOnly.actionType(),
          operatorId,
          auditException);
    }
  }

  private void validateAdmin(Long operatorId) {
    if (operatorId == null || operatorId <= 0) {
      throw new UserNotAuthenticatedException();
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return;
    }

    boolean isAdmin =
        authentication.getAuthorities().stream()
            .anyMatch(grantedAuthority -> "ROLE_ADMIN".equals(grantedAuthority.getAuthority()));
    if (!isAdmin) {
      throw new BusinessException(AuthErrorCode.UNAUTHORIZED_ACCESS);
    }
  }

  private StandardEvaluationContext evaluationContext(
      Method method, Object[] args, Object result, RuntimeException runtimeException) {
    StandardEvaluationContext context = new StandardEvaluationContext();
    String[] parameterNames = PARAM_DISCOVERER.getParameterNames(method);
    if (parameterNames != null) {
      for (int i = 0; i < parameterNames.length; i++) {
        context.setVariable(parameterNames[i], args[i]);
      }
    }
    for (int i = 0; i < args.length; i++) {
      context.setVariable("p" + i, args[i]);
    }
    context.setVariable("result", result);
    context.setVariable("error", runtimeException);
    return context;
  }

  private Long evalLong(String expression, StandardEvaluationContext context) {
    Object value = SPEL.parseExpression(expression).getValue(context);
    if (value instanceof Number number) {
      return number.longValue();
    }
    throw new UserNotAuthenticatedException("operatorId must resolve to a number");
  }

  private String evalString(String expression, StandardEvaluationContext context) {
    if (expression == null || expression.isBlank()) {
      return null;
    }
    Object value = SPEL.parseExpression(expression).getValue(context);
    return value == null ? null : String.valueOf(value);
  }

  private Map<String, Object> sanitizeArguments(Method method, Object[] args) {
    Map<String, Object> details = new LinkedHashMap<>();
    String[] parameterNames = PARAM_DISCOVERER.getParameterNames(method);

    for (int i = 0; i < args.length; i++) {
      String key =
          (parameterNames != null && i < parameterNames.length) ? parameterNames[i] : "arg" + i;
      details.put(key, sanitizeValue(key, args[i]));
    }
    return details;
  }

  private Object sanitizeValue(String key, Object value) {
    if (value == null) {
      return null;
    }
    String lowerKey = key.toLowerCase();
    if (lowerKey.contains("private")
        || lowerKey.contains("secret")
        || lowerKey.contains("password")
        || lowerKey.contains("key")) {
      return "***";
    }
    if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
      return value;
    }
    if (value.getClass().isRecord()) {
      Map<String, Object> recordMap = new LinkedHashMap<>();
      for (RecordComponent component : value.getClass().getRecordComponents()) {
        try {
          Object componentValue = component.getAccessor().invoke(value);
          recordMap.put(component.getName(), sanitizeValue(component.getName(), componentValue));
        } catch (Exception ignored) {
          recordMap.put(component.getName(), "<unavailable>");
        }
      }
      return recordMap;
    }
    return String.valueOf(value);
  }
}
