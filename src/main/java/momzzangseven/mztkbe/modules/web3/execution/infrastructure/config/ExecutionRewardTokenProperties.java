package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "web3.reward-token")
public class ExecutionRewardTokenProperties {

  @Valid private Worker worker = new Worker();

  @Getter
  @Setter
  public static class Worker {
    @Min(1)
    private int retryBackoffSeconds;
  }
}
