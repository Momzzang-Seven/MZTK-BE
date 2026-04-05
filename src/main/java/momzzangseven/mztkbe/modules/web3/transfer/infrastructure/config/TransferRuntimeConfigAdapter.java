package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.config.Eip7702Properties;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferRuntimeConfigPort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferRuntimeConfig;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferRuntimeConfigAdapter implements LoadTransferRuntimeConfigPort {

  private final Eip7702Properties eip7702Properties;
  private final TransferRewardTokenProperties rewardTokenProperties;
  private final TransferCoreProperties web3CoreProperties;

  @Override
  public TransferRuntimeConfig load() {
    return new TransferRuntimeConfig(
        web3CoreProperties.getChainId(),
        rewardTokenProperties.getTokenContractAddress(),
        rewardTokenProperties.getWorker().getRetryBackoffSeconds(),
        eip7702Properties.getDelegation().getBatchImplAddress(),
        eip7702Properties.getDelegation().getDefaultReceiverAddress(),
        eip7702Properties.getSponsor().getWalletAlias(),
        eip7702Properties.getSponsor().getKeyEncryptionKeyB64(),
        eip7702Properties.getSponsor().getMaxGasLimit(),
        eip7702Properties.getSponsor().getMaxTransferAmountEth(),
        eip7702Properties.getSponsor().getPerTxCapEth(),
        eip7702Properties.getSponsor().getPerDayUserCapEth(),
        eip7702Properties.getAuthorization().getTtlSeconds(),
        eip7702Properties.getCleanup().getZone(),
        eip7702Properties.getCleanup().getRetentionDays(),
        eip7702Properties.getCleanup().getBatchSize());
  }
}
