package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferRuntimeConfigPort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferRuntimeConfig;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferRuntimeConfigAdapter implements LoadTransferRuntimeConfigPort {

  private final TransferEip7702Properties transferEip7702Properties;
  private final TransferRewardTokenProperties rewardTokenProperties;
  private final TransferCoreProperties web3CoreProperties;

  @Override
  public TransferRuntimeConfig load() {
    return new TransferRuntimeConfig(
        web3CoreProperties.getChainId(),
        rewardTokenProperties.getTokenContractAddress(),
        rewardTokenProperties.getWorker().getRetryBackoffSeconds(),
        transferEip7702Properties.getDelegation().getBatchImplAddress(),
        transferEip7702Properties.getDelegation().getDefaultReceiverAddress(),
        transferEip7702Properties.getSponsor().getWalletAlias(),
        transferEip7702Properties.getSponsor().getKeyEncryptionKeyB64(),
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
