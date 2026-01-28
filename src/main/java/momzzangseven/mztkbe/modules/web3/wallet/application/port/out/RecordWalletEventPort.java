package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletEvent;

/** Port for recording wallet events */
public interface RecordWalletEventPort {
  /**
   * Record single wallet event
   *
   * @param event wallet event to record
   */
  void record(WalletEvent event);

  /**
   * Record multiple wallet events in batch
   *
   * @param events wallet events to record
   */
  void recordBatch(java.util.List<WalletEvent> events);
}
