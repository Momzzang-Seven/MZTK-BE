package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAutoAcceptPolicyPort;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnQnaAutoAcceptEnabled;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "web3.qna.auto-accept")
@ConditionalOnQnaAutoAcceptEnabled
public class QnaAutoAcceptProperties implements LoadQnaAutoAcceptPolicyPort {

  @NotNull private Boolean enabled = Boolean.FALSE;

  @Min(1)
  private long delaySeconds = 604_800L;

  @Min(1)
  private int batchSize = 50;

  @NotBlank private String cron = "0 17 * * * *";

  @NotBlank private String zone = "Asia/Seoul";

  @Override
  public QnaAutoAcceptPolicy loadPolicy() {
    return new QnaAutoAcceptPolicy(delaySeconds, batchSize);
  }
}
