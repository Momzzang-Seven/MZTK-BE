package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import java.util.List;
import lombok.Getter;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.InternalExecutionActionPolicy;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnInternalExecutionEnabled;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Getter
@Component
@ConditionalOnInternalExecutionEnabled
public class InternalExecutionIssuerProperties implements LoadInternalExecutionIssuerPolicyPort {

  private static final String NEW_PREFIX = "web3.execution.internal";

  private final Boolean enabled;
  private final int batchSize;
  private final long fixedDelayMs;
  private final String cron;
  private final String zone;
  private final int eip1559TtlSeconds;
  private final InternalExecutionActionPolicy actionPolicy;

  public InternalExecutionIssuerProperties(Environment environment) {
    Binder binder = Binder.get(environment);
    this.enabled = bind(binder, Bindable.of(Boolean.class), Boolean.FALSE, NEW_PREFIX + ".enabled");
    this.batchSize = bind(binder, Bindable.of(Integer.class), 20, NEW_PREFIX + ".batch-size");
    this.fixedDelayMs =
        bind(binder, Bindable.of(Long.class), 1_000L, NEW_PREFIX + ".fixed-delay-ms");
    this.cron = bind(binder, Bindable.of(String.class), "0/10 * * * * *", NEW_PREFIX + ".cron");
    this.zone = bind(binder, Bindable.of(String.class), "Asia/Seoul", NEW_PREFIX + ".zone");
    this.eip1559TtlSeconds =
        bind(binder, Bindable.of(Integer.class), 90, NEW_PREFIX + ".eip1559-ttl-seconds");
    this.actionPolicy =
        bind(
            binder,
            Bindable.of(InternalExecutionActionPolicy.class),
            InternalExecutionActionPolicy.QNA_ADMIN,
            NEW_PREFIX + ".action-policy");
  }

  @Override
  public InternalExecutionIssuerPolicy loadPolicy() {
    return new InternalExecutionIssuerPolicy(enabled, batchSize, actionPolicy.actionTypes());
  }

  public List<ExecutionActionType> getActionTypes() {
    return actionPolicy.actionTypes();
  }

  private static <T> T bind(
      Binder binder, Bindable<T> bindable, T defaultValue, String propertyName) {
    return binder.bind(propertyName, bindable).orElse(defaultValue);
  }
}
