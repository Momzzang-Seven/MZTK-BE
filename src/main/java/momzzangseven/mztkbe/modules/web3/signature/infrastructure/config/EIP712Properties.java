package momzzangseven.mztkbe.modules.web3.signature.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * EIP-712 Domain Configuration Properties
 *
 * <p>Provides identical Domain configuration as frontend.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "web3.eip712")
public class EIP712Properties {
    /** Domain name (default: "MomzzangSeven") */
    private String domainName = "MomzzangSeven";

    /** Domain version (default: "1") */
    private String domainVersion = "1";

    /** Chain ID (default: 11155111 for Sepolia testnet) */
    private Long chainId = 11155111L;

    /** Verifying contract address */
    private String verifyingContract = "0xCcCCccccCCCCccccCCCCccccCCCCccccCCCCcccc";
}
