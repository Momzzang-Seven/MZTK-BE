package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Hash;

@DisplayName("MarketplaceEscrowRevertReasonDecoder 단위 테스트")
class MarketplaceEscrowRevertReasonDecoderTest {

  private final MarketplaceEscrowRevertReasonDecoder sut =
      new MarketplaceEscrowRevertReasonDecoder();

  @Test
  @DisplayName("MarketplaceEscrow custom error selector를 정확한 signature 이름으로 디코딩한다")
  void decodeKnownMarketplaceErrors() {
    assertThat(sut.decode(selector("OrderAlreadyExists()"))).contains("OrderAlreadyExists()");
    assertThat(sut.decode(selector("DeadlineExpired()"))).contains("DeadlineExpired()");
    assertThat(sut.decode(selector("DeadlineNotExpired()"))).contains("DeadlineNotExpired()");
    assertThat(sut.decode(selector("InvalidSignature()"))).contains("InvalidSignature()");
    assertThat(sut.decode(selector("SafeERC20FailedOperation(address)")))
        .contains("SafeERC20FailedOperation(address)");
  }

  @Test
  @DisplayName("알 수 없거나 짧은 revert data는 empty를 반환한다")
  void decodeUnknownOrShortData() {
    assertThat(sut.decode("0x12345678")).isEmpty();
    assertThat(sut.decode("0x12")).isEmpty();
    assertThat(sut.decode(null)).isEmpty();
  }

  private static String selector(String signature) {
    return Hash.sha3String(signature).substring(0, 10);
  }
}
