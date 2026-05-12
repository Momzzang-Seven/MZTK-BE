package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

/** Enables execution infrastructure beans when any execution mode is active. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ExecutionModeEnabledCondition.class)
public @interface ConditionalOnExecutionModeEnabled {}
