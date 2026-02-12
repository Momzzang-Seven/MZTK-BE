package momzzangseven.mztkbe.modules.web3.admin.application.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import momzzangseven.mztkbe.modules.web3.admin.domain.model.Web3AdminActionType;
import momzzangseven.mztkbe.modules.web3.admin.domain.model.Web3AdminTargetType;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminOnly {

  Web3AdminActionType actionType();

  Web3AdminTargetType targetType();

  String operatorId() default "#p0";

  String targetId() default "";
}
