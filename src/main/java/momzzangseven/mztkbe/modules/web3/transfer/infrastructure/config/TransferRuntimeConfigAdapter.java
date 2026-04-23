package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadSponsorTreasurySignerConfigPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferRuntimeConfigPort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferRuntimeConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class TransferRuntimeConfigAdapter implements LoadTransferRuntimeConfigPort {

  private final TransferEip7702Properties transferEip7702Properties;
  private final TransferRewardTokenProperties rewardTokenProperties;
  private final TransferCoreProperties web3CoreProperties;
  private final LoadSponsorTreasurySignerConfigPort loadSponsorTreasurySignerConfigPort;

  @Override
  public TransferRuntimeConfig load() {
    var sponsorSignerConfig = loadSponsorTreasurySignerConfigPort.load();
    return new TransferRuntimeConfig(
        web3CoreProperties.getChainId(),
        rewardTokenProperties.getTokenContractAddress(),
        rewardTokenProperties.getWorker().getRetryBackoffSeconds(),
        transferEip7702Properties.getDelegation().getBatchImplAddress(),
        transferEip7702Properties.getDelegation().getDefaultReceiverAddress(),
        sponsorSignerConfig.walletAlias(),
        sponsorSignerConfig.keyEncryptionKeyB64(),
        transferEip7702Properties.getSponsor().getMaxGasLimit(),
        transferEip7702Properties.getSponsor().getMaxTransferAmountEth(),
        transferEip7702Properties.getSponsor().getPerTxCapEth(),
        transferEip7702Properties.getSponsor().getPerDayUserCapEth(),
        transferEip7702Properties.getAuthorization().getTtlSeconds(),
        transferEip7702Properties.getCleanup().getZone(),
        transferEip7702Properties.getCleanup().getRetentionDays(),
        transferEip7702Properties.getCleanup().getBatchSize());
  }
}
