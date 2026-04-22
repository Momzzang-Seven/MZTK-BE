package momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** EIP-7702 transfer configuration bound from web3.eip7702.*. */
@Getter
@Setter
@Component
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
@Validated
@ConfigurationProperties(prefix = "web3.eip7702")
public class Eip7702Properties {

  @NotNull private Boolean enabled;

  @Valid private Delegation delegation = new Delegation();
  @Valid private Sponsor sponsor = new Sponsor();
  @Valid private Authorization authorization = new Authorization();
  @Valid private Execution execution = new Execution();
  @Valid private Cleanup cleanup = new Cleanup();

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
    @NotNull private Boolean enabled;

    @NotBlank private String keyEncryptionKeyB64;

    @NotBlank private String walletAlias;

    @Min(21_000)
    private long maxGasLimit;

    @Min(1)
    private long maxMaxFeeGwei;

    @Min(1)
    private long maxPriorityFeeGwei;

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

    @Min(1)
    private int eip1559TtlSeconds;

    @NotBlank private String noncePolicy;

    @NotNull private Boolean requireChainIdMatch;

    @Min(1)
    private int maxAuthListLength;
  }

  @Getter
  @Setter
  public static class Execution {
    @NotNull private List<String> allowedDelegateTargets = List.of();
    @NotNull private List<String> allowedCallTargets = List.of();
    @NotNull private List<String> allowedTargetContracts = List.of();
    @NotNull private List<String> blockedFunctionSelectors = List.of();
  }

  @Getter
  @Setter
  public static class Cleanup {
    @NotNull private Boolean enabled;

    @Min(1)
    private int retentionDays;

    @Min(1)
    private int batchSize;

    @NotBlank private String cron;

    @NotBlank private String zone;
  }
}
