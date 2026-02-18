package momzzangseven.mztkbe.global.security.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminOnly {

  String actionType();

  String targetType();

  String operatorId() default "#p0";

  String targetId() default "";
}
