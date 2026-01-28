package momzzangseven.mztkbe.modules.web3.wallet.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for wallet hard-delete policy
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "web3.wallet.hard-delete")
public class WalletHardDeleteProperties {

    /** Retention window in days before hard delete */
    private int retentionDays = 30;

    /** Batch size for selecting hard-delete targets */
    private int batchSize = 200;

    /** Cron expression for hard-delete job */
    private String cron;

    /** Time zone for hard-delete cron */
    private String zone;
}
