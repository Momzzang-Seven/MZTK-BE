package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.treasury;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.MarketplaceServerSigPreimage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.utils.Numeric;

@DisplayName("MarketplaceTypedDataDigestBuilder 단위 테스트")
class MarketplaceTypedDataDigestBuilderTest {

  private static final long CHAIN_ID = 10L;
  private static final String ESCROW = "0x4444444444444444444444444444444444444444";
  private static final String BUYER = "0x1111111111111111111111111111111111111111";
  private static final String TRAINER = "0x2222222222222222222222222222222222222222";
  private static final String TOKEN = "0x3333333333333333333333333333333333333333";
  private static final String ORDER_KEY =
      "0x00000000000000000000000000000000123e4567e89b12d3a456426614174000";
  private static final long SIGNED_AT = 1_000L;

  private final MarketplaceTypedDataDigestBuilder sut = new MarketplaceTypedDataDigestBuilder();

  @Test
  @DisplayName("purchaseClass EIP-712 digest golden vector")
  void buildDigest_purchaseClass_matchesGoldenVector() {
    byte[] digest =
        sut.buildDigest(
            new MarketplaceServerSigPreimage.PurchaseClassPreimage(
                BUYER, ORDER_KEY, TOKEN, TRAINER, BigInteger.valueOf(50_000)),
            SIGNED_AT,
            CHAIN_ID,
            ESCROW,
            "MarketplaceEscrow",
            "1");

    assertThat(Numeric.toHexString(digest))
        .isEqualTo("0x5d6fa02e2c9384c14bc9ba6d42315cbf364b716f85c58d43236ffcbaf7b32d25");
  }

  @Test
  @DisplayName("cancelClass EIP-712 digest golden vector")
  void buildDigest_cancelClass_matchesGoldenVector() {
    byte[] digest =
        sut.buildDigest(
            new MarketplaceServerSigPreimage.CancelClassPreimage(BUYER, ORDER_KEY),
            SIGNED_AT,
            CHAIN_ID,
            ESCROW,
            "MarketplaceEscrow",
            "1");

    assertThat(Numeric.toHexString(digest))
        .isEqualTo("0x1be47c8510327b5698c7a3a757b177de75075dee01e14e83aebfa6abb36b56d4");
  }

  @Test
  @DisplayName("confirmClass EIP-712 digest golden vector")
  void buildDigest_confirmClass_matchesGoldenVector() {
    byte[] digest =
        sut.buildDigest(
            new MarketplaceServerSigPreimage.ConfirmClassPreimage(BUYER, ORDER_KEY),
            SIGNED_AT,
            CHAIN_ID,
            ESCROW,
            "MarketplaceEscrow",
            "1");

    assertThat(Numeric.toHexString(digest))
        .isEqualTo("0xb1a8da1950e93b7ce503098b5463011d76db62b6e18fe82dc0fa9a11ac142deb");
  }

  @Test
  @DisplayName("domain separator는 chainId, verifyingContract, domain name/version을 포함한다")
  void computeDomainSeparator_matchesGoldenVector() {
    byte[] domainSeparator = sut.computeDomainSeparator(CHAIN_ID, ESCROW, "MarketplaceEscrow", "1");

    assertThat(Numeric.toHexString(domainSeparator))
        .isEqualTo("0x48045752a3272623e76e3c8bc89b74cb4d0aa15fd8340d3357c15a1c51783ae3");
  }
}
