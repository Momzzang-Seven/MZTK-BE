package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "web3.execution.internal-issuer")
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class InternalExecutionIssuerProperties implements LoadInternalExecutionIssuerPolicyPort {

  @NotNull private Boolean enabled = Boolean.FALSE;

  @Min(1)
  private int batchSize = 20;

  @Min(1)
  private long fixedDelayMs = 1_000L;

  @NotBlank private String cron = "0/10 * * * * *";

  @NotBlank private String zone = "Asia/Seoul";

  @NotNull
  private List<ExecutionActionType> actionTypes = List.of(ExecutionActionType.QNA_ADMIN_SETTLE);

  @Override
  public InternalExecutionIssuerPolicy loadPolicy() {
    return new InternalExecutionIssuerPolicy(enabled, batchSize, List.copyOf(actionTypes));
  }
}
