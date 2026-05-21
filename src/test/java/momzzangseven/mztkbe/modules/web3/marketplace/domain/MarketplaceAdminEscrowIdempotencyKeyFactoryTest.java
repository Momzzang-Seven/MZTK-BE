package momzzangseven.mztkbe.modules.web3.marketplace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceAdminEscrowIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceAdminExecutionRequestSource;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MarketplaceAdminEscrowIdempotencyKeyFactory")
class MarketplaceAdminEscrowIdempotencyKeyFactoryTest {

  @Test
  @DisplayName("admin key는 action/reservation/source/reason만 포함한다")
  void create_includesOnlyExecutionIdentityMaterial() {
    String key =
        MarketplaceAdminEscrowIdempotencyKeyFactory.create(
            MarketplaceExecutionActionType.MARKETPLACE_ADMIN_REFUND,
            123L,
            MarketplaceAdminExecutionRequestSource.MANUAL_ADMIN,
            "TRAINER_TIMEOUT");

    assertThat(key)
        .isEqualTo("marketplace-admin:marketplace_admin_refund:123:manual_admin:trainer_timeout");
  }

  @Test
  @DisplayName("source나 reason이 다르면 다른 root key가 된다")
  void create_differsBySourceAndReason() {
    String manual =
        MarketplaceAdminEscrowIdempotencyKeyFactory.create(
            MarketplaceExecutionActionType.MARKETPLACE_ADMIN_SETTLE,
            123L,
            MarketplaceAdminExecutionRequestSource.MANUAL_ADMIN,
            "ADMIN_MANUAL_SETTLE");
    String scheduler =
        MarketplaceAdminEscrowIdempotencyKeyFactory.create(
            MarketplaceExecutionActionType.MARKETPLACE_ADMIN_SETTLE,
            123L,
            MarketplaceAdminExecutionRequestSource.SCHEDULER,
            "ADMIN_MANUAL_SETTLE");
    String timeout =
        MarketplaceAdminEscrowIdempotencyKeyFactory.create(
            MarketplaceExecutionActionType.MARKETPLACE_ADMIN_SETTLE,
            123L,
            MarketplaceAdminExecutionRequestSource.MANUAL_ADMIN,
            "BUYER_CONFIRMATION_TIMEOUT");

    assertThat(manual).isNotEqualTo(scheduler);
    assertThat(manual).isNotEqualTo(timeout);
  }

  @Test
  @DisplayName("user action 또는 action/reason mismatch는 거부한다")
  void create_rejectsInvalidActionOrReason() {
    assertThatThrownBy(
            () ->
                MarketplaceAdminEscrowIdempotencyKeyFactory.create(
                    MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
                    123L,
                    MarketplaceAdminExecutionRequestSource.MANUAL_ADMIN,
                    "TRAINER_TIMEOUT"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("admin actionType");
    assertThatThrownBy(
            () ->
                MarketplaceAdminEscrowIdempotencyKeyFactory.create(
                    MarketplaceExecutionActionType.MARKETPLACE_ADMIN_REFUND,
                    123L,
                    MarketplaceAdminExecutionRequestSource.MANUAL_ADMIN,
                    "BUYER_CONFIRMATION_TIMEOUT"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("reasonCode");
  }
}
