package momzzangseven.mztkbe.global.security.aspect;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.audit.application.AdminAuditDetailNormalizer;
import momzzangseven.mztkbe.global.audit.application.port.out.RecordAdminAuditPort;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Validates the admin role and records an admin action audit row around any {@code @AdminOnly}
 * method.
 *
 * <p>Pinned to {@link Ordered#HIGHEST_PRECEDENCE} so this aspect always runs outside Spring's
 * transaction interceptor. That way the audit recording in the {@code finally} block executes after
 * the surrounding transaction has fully committed (or rolled back), and the audit adapter's {@code
 * REQUIRES_NEW} propagation is not the only thing keeping audit writes independent of the caller's
 * transaction.
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminOnlyAspect {

  private static final ExpressionParser SPEL = new SpelExpressionParser();
  private static final DefaultParameterNameDiscoverer PARAM_DISCOVERER =
      new DefaultParameterNameDiscoverer();

  private final RecordAdminAuditPort recordAdminAuditPort;
  private final RoleHierarchy roleHierarchy;

  /**
   * Matches every method annotated with {@link AdminOnly}. Centralising the FQN here means a future
   * package move only requires updating this single string.
   *
   * <p>We intentionally avoid the {@code @annotation(adminOnly)} parameter-binding form: combined
   * with {@link Order @Order(HIGHEST_PRECEDENCE)}, Spring AOP can fail to propagate the {@code
   * JoinPointMatch} attribute through the surrounding interceptor chain, surfacing as {@code
   * IllegalStateException: Required to bind 2 arguments, but only bound 1 (JoinPointMatch was NOT
   * bound in invocation)}. Resolving the annotation manually inside the advice side-steps that
   * binding entirely while preserving the desired aspect ordering.
   */
  @Pointcut("execution(@momzzangseven.mztkbe.global.security.aspect.AdminOnly * *(..))")
  public void adminOnlyMethods() {}

  @Around("adminOnlyMethods()")
  public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method targetMethod = resolveTargetMethod(signature, joinPoint.getTarget());
    AdminOnly adminOnly = AnnotationUtils.findAnnotation(targetMethod, AdminOnly.class);
    if (adminOnly == null) {
      // The pointcut guarantees @AdminOnly is present on the target method. Reaching here means
      // the annotation was renamed/moved and the pointcut FQN above is now stale — fail loudly so
      // the admin guard is never silently bypassed.
      throw new IllegalStateException(
          "@AdminOnly annotation could not be resolved on " + targetMethod);
    }

    Object[] args = joinPoint.getArgs();
    StandardEvaluationContext context = evaluationContext(targetMethod, args, null, null);

    Long operatorId = evalLong(adminOnly.operatorId(), context);
    validateAdmin(operatorId);

    Object result = null;
    Exception caught = null;
    try {
      result = joinPoint.proceed();
      return result;
    } catch (Exception e) {
      caught = e;
      throw e;
    } finally {
      recordAdminAudit(adminOnly, targetMethod, args, operatorId, result, caught);
    }
  }

  private static Method resolveTargetMethod(MethodSignature signature, Object target) {
    Method method = signature.getMethod();
    return target != null ? AopUtils.getMostSpecificMethod(method, target.getClass()) : method;
  }

  private void recordAdminAudit(
      AdminOnly adminOnly,
      Method method,
      Object[] args,
      Long operatorId,
      Object result,
      Exception caught) {
    try {
      StandardEvaluationContext context = evaluationContext(method, args, result, caught);
      String targetId = evalString(adminOnly.targetId(), context);
      boolean success = caught == null;
      String failureReason = success ? null : caught.getClass().getSimpleName();

      // operatorId, success, actionType, targetType and targetId are persisted as dedicated
      // columns on admin_action_audits — keep them out of detail_json so the row has a single
      // source of truth for those fields.
      Map<String, Object> rawDetail = new LinkedHashMap<>();
      rawDetail.put("method", method.getDeclaringClass().getSimpleName() + "." + method.getName());
      rawDetail.put("failureReason", failureReason);
      rawDetail.put("arguments", sanitizeArguments(method, args));

      recordAdminAuditPort.record(
          new RecordAdminAuditPort.AuditCommand(
              operatorId,
              adminOnly.actionType(),
              adminOnly.targetType(),
              targetId,
              success,
              AdminAuditDetailNormalizer.normalize(rawDetail)));
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
        roleHierarchy.getReachableGrantedAuthorities(authentication.getAuthorities()).stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_ADMIN"::equals);
    if (!isAdmin) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
    }
  }

  private StandardEvaluationContext evaluationContext(
      Method method, Object[] args, Object result, Exception caught) {
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
    context.setVariable("error", caught);
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
