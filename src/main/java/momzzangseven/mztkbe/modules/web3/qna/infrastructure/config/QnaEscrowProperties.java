package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "web3.escrow")
@ConditionalOnAnyExecutionEnabled
public class QnaEscrowProperties {

  @NotBlank private String qnaContractAddress;

  @NotBlank private String eip712DomainName = "QnAEscrow";

  @NotBlank private String eip712DomainVersion = "1";

  @Min(0)
  private int signedAtSkewSeconds = 0;

  @Min(60)
  @Max(3600)
  private int sigValidityDuration = 900;
}
