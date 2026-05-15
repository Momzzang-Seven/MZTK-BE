package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WalletApprovalCalldataEncoder unit test")
class WalletApprovalCalldataEncoderTest {

  private final WalletApprovalCalldataEncoder encoder = new WalletApprovalCalldataEncoder();

  @Test
  void encodeApproveMax_encodesQnaEscrowSpenderAndUint256Max() {
    String spender = "0x0000000000000000000000000000000000000002";

    String calldata = encoder.encodeApproveMax(spender);

    assertThat(calldata).isEqualTo(expectedApproveMax(spender));
  }

  @Test
  void encodeApproveMax_encodesMarketplaceEscrowSpenderAndUint256Max() {
    String spender = "0x0000000000000000000000000000000000000003";

    String calldata = encoder.encodeApproveMax(spender);

    assertThat(calldata).isEqualTo(expectedApproveMax(spender));
  }

  private String expectedApproveMax(String spender) {
    return "0x095ea7b3" + "0".repeat(24) + spender.substring(2) + "f".repeat(64);
  }
}
