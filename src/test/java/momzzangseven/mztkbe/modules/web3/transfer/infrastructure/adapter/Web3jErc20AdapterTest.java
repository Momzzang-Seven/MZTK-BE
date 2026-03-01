package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import org.junit.jupiter.api.Test;

class Web3jErc20AdapterTest {

  @Test
  void classifyBroadcastFailureMessage_shouldReturnTreasuryEthLowOnInsufficientFundsMessage() {
    Web3TxFailureReason reason =
        Web3jErc20Adapter.classifyBroadcastFailureMessage(
            "insufficient funds for gas * price + value");

    assertThat(reason).isEqualTo(Web3TxFailureReason.TREASURY_ETH_BELOW_CRITICAL);
  }

  @Test
  void classifyBroadcastFailureMessage_shouldReturnBroadcastFailedOnUnknownMessage() {
    Web3TxFailureReason reason = Web3jErc20Adapter.classifyBroadcastFailureMessage("nonce too low");

    assertThat(reason).isEqualTo(Web3TxFailureReason.BROADCAST_FAILED);
  }

  @Test
  void classifyBroadcastFailureMessage_shouldReturnBroadcastFailedOnBlankMessage() {
    Web3TxFailureReason reason = Web3jErc20Adapter.classifyBroadcastFailureMessage("  ");

    assertThat(reason).isEqualTo(Web3TxFailureReason.BROADCAST_FAILED);
  }
}
