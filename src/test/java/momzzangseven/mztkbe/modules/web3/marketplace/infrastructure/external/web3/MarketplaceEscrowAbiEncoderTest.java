package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Hash;

@DisplayName("MarketplaceEscrowAbiEncoder 단위 테스트")
class MarketplaceEscrowAbiEncoderTest {

  private static final String ORDER_KEY =
      "0x00000000000000000000000000000000123e4567e89b12d3a456426614174000";
  private static final String TOKEN = "0x1111111111111111111111111111111111111111";
  private static final String TRAINER = "0x2222222222222222222222222222222222222222";
  private static final byte[] SIGNATURE = new byte[65];

  private final MarketplaceEscrowAbiEncoder sut = new MarketplaceEscrowAbiEncoder();

  @Test
  @DisplayName(
      "purchaseClass calldata는 ABI selector와 bytes32 orderKey/token/trainer/price/signedAt/signature를 포함한다")
  void encodePurchaseClass() {
    String data =
        sut.encode(
            MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
            ORDER_KEY,
            TOKEN,
            TRAINER,
            BigInteger.valueOf(50_000),
            1_000L,
            SIGNATURE);

    assertThat(data)
        .startsWith(selector("purchaseClass(bytes32,address,address,uint256,uint256,bytes)"));
    assertThat(data).contains(ORDER_KEY.substring(2));
    assertThat(data).contains(TOKEN.substring(2));
    assertThat(data).contains(TRAINER.substring(2));
    assertThat(data).contains(String.format("%064x", 50_000));
    assertThat(data).contains(String.format("%064x", 1_000));
  }

  @Test
  @DisplayName("cancelClass/confirmClass/claimExpiredRefund selector를 action별로 매핑한다")
  void encodeSelectorsByAction() {
    assertThat(
            sut.encode(
                MarketplaceExecutionActionType.MARKETPLACE_CLASS_CANCEL,
                ORDER_KEY,
                TOKEN,
                TRAINER,
                BigInteger.valueOf(50_000),
                1_000L,
                SIGNATURE))
        .startsWith(selector("cancelClass(bytes32,uint256,bytes)"));
    assertThat(
            sut.encode(
                MarketplaceExecutionActionType.MARKETPLACE_CLASS_CONFIRM,
                ORDER_KEY,
                TOKEN,
                TRAINER,
                BigInteger.valueOf(50_000),
                1_000L,
                SIGNATURE))
        .startsWith(selector("confirmClass(bytes32,uint256,bytes)"));
    assertThat(
            sut.encode(
                MarketplaceExecutionActionType.MARKETPLACE_CLASS_EXPIRED_REFUND,
                ORDER_KEY,
                TOKEN,
                TRAINER,
                BigInteger.valueOf(50_000),
                null,
                null))
        .startsWith(selector("claimExpiredRefund(bytes32)"));
  }

  @Test
  @DisplayName("server signature action에서 65바이트 signature가 없으면 거부한다")
  void encodeRejectsInvalidSignature() {
    assertThatThrownBy(
            () ->
                sut.encode(
                    MarketplaceExecutionActionType.MARKETPLACE_CLASS_CONFIRM,
                    ORDER_KEY,
                    TOKEN,
                    TRAINER,
                    BigInteger.valueOf(50_000),
                    1_000L,
                    new byte[64]))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("signature");
  }

  @Test
  @DisplayName("user-scope encoder는 admin action type을 거부한다")
  void encodeRejectsAdminActionTypes() {
    assertThatThrownBy(
            () ->
                sut.encode(
                    MarketplaceExecutionActionType.MARKETPLACE_ADMIN_REFUND,
                    ORDER_KEY,
                    TOKEN,
                    TRAINER,
                    BigInteger.valueOf(50_000),
                    1_000L,
                    SIGNATURE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("admin marketplace action");
  }

  @Test
  @DisplayName("adminRefund/adminSettle calldata는 orderKey만 ABI 인자로 사용한다")
  void encodeAdminCalldataUsesOrderKeyOnly() {
    assertThat(sut.encodeAdminRefund(ORDER_KEY))
        .startsWith(selector("adminRefund(bytes32)"))
        .contains(ORDER_KEY.substring(2));
    assertThat(sut.encodeAdminSettle(ORDER_KEY))
        .startsWith(selector("adminSettle(bytes32)"))
        .contains(ORDER_KEY.substring(2));
  }

  private static String selector(String signature) {
    return Hash.sha3String(signature).substring(0, 10);
  }
}
