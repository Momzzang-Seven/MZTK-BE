package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "web3.eip7702")
public class TransferEip7702Properties {

  @Valid private Delegation delegation = new Delegation();
  @Valid private Sponsor sponsor = new Sponsor();
  @Valid private Authorization authorization = new Authorization();
  @Valid private Cleanup cleanup = new Cleanup();

  @Getter
  @Setter
  public static class Delegation {
    @NotBlank private String batchImplAddress;
    @NotBlank private String defaultReceiverAddress;
  }

  @Getter
  @Setter
  public static class Sponsor {
    @Min(21_000)
    private long maxGasLimit;

    @NotNull
    @DecimalMin("0")
    private BigDecimal maxTransferAmountEth;

    @NotNull
    @DecimalMin("0")
    private BigDecimal perTxCapEth;

    @NotNull
    @DecimalMin("0")
    private BigDecimal perDayUserCapEth;
  }

  @Getter
  @Setter
  public static class Authorization {
    @Min(1)
    private int ttlSeconds;
  }

  @Getter
  @Setter
  public static class Cleanup {
    @Min(1)
    private int retentionDays;

    @Min(1)
    private int batchSize;

    @NotBlank private String zone;
  }
}
