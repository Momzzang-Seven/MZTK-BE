package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnInternalExecutionEnabled;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "web3.execution.internal-issuer")
@ConditionalOnInternalExecutionEnabled
public class InternalExecutionIssuerProperties implements LoadInternalExecutionIssuerPolicyPort {

  @NotNull private Boolean enabled = Boolean.FALSE;

  @Min(1)
  private int batchSize = 20;

  @Min(1)
  private long fixedDelayMs = 1_000L;

  @NotBlank private String cron = "0/10 * * * * *";

  @NotBlank private String zone = "Asia/Seoul";

  @Min(1)
  private int eip1559TtlSeconds = 90;

  @NotNull private Signer signer = new Signer();

  @NotNull
  private List<ExecutionActionType> actionTypes =
      List.of(ExecutionActionType.QNA_ADMIN_SETTLE, ExecutionActionType.QNA_ADMIN_REFUND);

  @Override
  public InternalExecutionIssuerPolicy loadPolicy() {
    return new InternalExecutionIssuerPolicy(enabled, batchSize, List.copyOf(actionTypes));
  }

  @Getter
  @Setter
  public static class Signer {
    private String keyEncryptionKeyB64;
    private String walletAlias;
  }
}
