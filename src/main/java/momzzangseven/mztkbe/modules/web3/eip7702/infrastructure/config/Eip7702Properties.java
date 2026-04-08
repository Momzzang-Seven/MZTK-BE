package momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** EIP-7702 transfer configuration bound from web3.eip7702.*. */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "web3.eip7702")
public class Eip7702Properties {

  private boolean enabled = true;

  private Delegation delegation = new Delegation();
  private Sponsor sponsor = new Sponsor();
  private Authorization authorization = new Authorization();
  private Execution execution = new Execution();
  private Cleanup cleanup = new Cleanup();

  @Getter
  @Setter
  public static class Delegation {
    @NotBlank private String contractAddress;
    @NotBlank private String nonceTrackerAddress;
    @NotBlank private String batchImplAddress;
    @NotBlank private String defaultReceiverAddress;
  }

  @Getter
  @Setter
  public static class Sponsor {
    private boolean enabled = true;

    @NotBlank private String keyEncryptionKeyB64;

    @NotBlank private String walletAlias;

    @Min(21_000)
    private long maxGasLimit = 500_000L;

    @Min(1)
    private long maxMaxFeeGwei = 60L;

    @Min(1)
    private long maxPriorityFeeGwei = 2L;

    @DecimalMin("0")
    private BigDecimal maxTransferAmountEth = new BigDecimal("5000");

    @DecimalMin("0")
    private BigDecimal perTxCapEth = new BigDecimal("0.002");

    @DecimalMin("0")
    private BigDecimal perDayUserCapEth = new BigDecimal("0.01");
  }

  @Getter
  @Setter
  public static class Authorization {
    @Min(1)
    private int ttlSeconds = 300;

    @Min(1)
    private int eip1559TtlSeconds = 90;

    @NotBlank private String noncePolicy = "ONCHAIN";

    private boolean requireChainIdMatch = true;

    @Min(1)
    private int maxAuthListLength = 1;
  }

  @Getter
  @Setter
  public static class Execution {
    private List<String> allowedTargetContracts = new ArrayList<>();
    private List<String> blockedFunctionSelectors = new ArrayList<>();
  }

  @Getter
  @Setter
  public static class Cleanup {
    private boolean enabled = true;

    @Min(1)
    private int retentionDays = 180;

    @Min(1)
    private int batchSize = 500;

    @NotBlank private String cron = "0 30 4 * * *";

    @NotBlank private String zone = "Asia/Seoul";
  }
}
