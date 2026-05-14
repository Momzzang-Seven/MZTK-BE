package momzzangseven.mztkbe.modules.web3.marketplace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceEscrowIdCodec;
import org.junit.jupiter.api.Test;

class MarketplaceEscrowIdCodecTest {

  @Test
  void orderKey_encodesReservationIdAsBytes32Hex() {
    assertThat(MarketplaceEscrowIdCodec.orderKey(123L))
        .isEqualTo("0x000000000000000000000000000000000000000000000000000000000000007b");
  }

  @Test
  void zeroBytes32_returnsAllZeroBytes32() {
    assertThat(MarketplaceEscrowIdCodec.zeroBytes32())
        .isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000000");
  }

  @Test
  void orderKey_rejectsNullZeroAndNegativeReservationId() {
    assertThatThrownBy(() -> MarketplaceEscrowIdCodec.orderKey(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("reservationId");
    assertThatThrownBy(() -> MarketplaceEscrowIdCodec.orderKey(0L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("reservationId");
    assertThatThrownBy(() -> MarketplaceEscrowIdCodec.orderKey(-1L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("reservationId");
  }
}
