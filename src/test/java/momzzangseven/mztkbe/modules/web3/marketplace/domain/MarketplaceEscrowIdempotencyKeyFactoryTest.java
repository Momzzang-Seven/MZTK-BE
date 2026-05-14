package momzzangseven.mztkbe.modules.web3.marketplace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceEscrowIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import org.junit.jupiter.api.Test;

class MarketplaceEscrowIdempotencyKeyFactoryTest {

  @Test
  void create_includesActionReservationIdAndVersion() {
    String key =
        MarketplaceEscrowIdempotencyKeyFactory.create(
            MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE, 123L, 7L);

    assertThat(key).isEqualTo("marketplace:marketplace_class_purchase:123:v7");
  }

  @Test
  void create_isDeterministicForCancelAndConfirm() {
    assertThat(
            MarketplaceEscrowIdempotencyKeyFactory.create(
                MarketplaceExecutionActionType.MARKETPLACE_CLASS_CANCEL, 123L, 7L))
        .isEqualTo("marketplace:marketplace_class_cancel:123:v7");
    assertThat(
            MarketplaceEscrowIdempotencyKeyFactory.create(
                MarketplaceExecutionActionType.MARKETPLACE_CLASS_CONFIRM, 123L, 7L))
        .isEqualTo("marketplace:marketplace_class_confirm:123:v7");
  }

  @Test
  void create_rejectsInvalidInputs() {
    assertThatThrownBy(() -> MarketplaceEscrowIdempotencyKeyFactory.create(null, 123L, 1L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("actionType");
    assertThatThrownBy(
            () ->
                MarketplaceEscrowIdempotencyKeyFactory.create(
                    MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE, 0L, 1L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("reservationId");
    assertThatThrownBy(
            () ->
                MarketplaceEscrowIdempotencyKeyFactory.create(
                    MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE, 123L, -1L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("reservationVersion");
  }
}
