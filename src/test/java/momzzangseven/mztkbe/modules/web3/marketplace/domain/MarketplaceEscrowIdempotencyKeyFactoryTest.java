package momzzangseven.mztkbe.modules.web3.marketplace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceActorType;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceEscrowIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import org.junit.jupiter.api.Test;

class MarketplaceEscrowIdempotencyKeyFactoryTest {

  @Test
  void create_includesActionRequesterAndReservationId() {
    String key =
        MarketplaceEscrowIdempotencyKeyFactory.create(
            MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE, 7L, 123L);

    assertThat(key).isEqualTo("marketplace:marketplace_class_purchase:buyer:7:123");
  }

  @Test
  void create_isDeterministicForCancelAndConfirm() {
    assertThat(
            MarketplaceEscrowIdempotencyKeyFactory.create(
                MarketplaceExecutionActionType.MARKETPLACE_CLASS_CANCEL, 7L, 123L))
        .isEqualTo("marketplace:marketplace_class_cancel:buyer:7:123");
    assertThat(
            MarketplaceEscrowIdempotencyKeyFactory.create(
                MarketplaceExecutionActionType.MARKETPLACE_CLASS_CANCEL,
                MarketplaceActorType.TRAINER,
                9L,
                123L))
        .isEqualTo("marketplace:marketplace_class_cancel:trainer:9:123");
    assertThat(
            MarketplaceEscrowIdempotencyKeyFactory.create(
                MarketplaceExecutionActionType.MARKETPLACE_CLASS_CONFIRM, 7L, 123L))
        .isEqualTo("marketplace:marketplace_class_confirm:buyer:7:123");
  }

  @Test
  void create_rejectsInvalidInputs() {
    assertThatThrownBy(() -> MarketplaceEscrowIdempotencyKeyFactory.create(null, 7L, 123L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("actionType");
    assertThatThrownBy(
            () ->
                MarketplaceEscrowIdempotencyKeyFactory.create(
                    MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE, 0L, 123L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("authorityUserId");
    assertThatThrownBy(
            () ->
                MarketplaceEscrowIdempotencyKeyFactory.create(
                    MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE, 7L, 0L))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("reservationId");
  }
}
