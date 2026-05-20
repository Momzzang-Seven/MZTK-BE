package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.PrecheckMarketplacePurchaseFundingPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketplacePurchaseFundingPrecheckAdapter 단위 테스트")
class MarketplacePurchaseFundingPrecheckAdapterTest {

  private static final String BUYER = "0x1111111111111111111111111111111111111111";
  private static final String TRAINER = "0x2222222222222222222222222222222222222222";
  private static final String ESCROW = "0x3333333333333333333333333333333333333333";
  private static final String TOKEN = "0x4444444444444444444444444444444444444444";
  private static final BigInteger PRICE = BigInteger.valueOf(50_000);

  @Mock private MarketplaceContractCallSupport marketplaceContractCallSupport;

  @Test
  @DisplayName("supported token, balance, allowance가 충분하면 통과한다")
  void precheck_passes_when_funded_and_allowed() {
    MarketplacePurchaseFundingPrecheckAdapter adapter =
        new MarketplacePurchaseFundingPrecheckAdapter(marketplaceContractCallSupport);
    given(marketplaceContractCallSupport.isSupportedToken(ESCROW, TOKEN)).willReturn(true);
    given(marketplaceContractCallSupport.loadBalance(BUYER, TOKEN)).willReturn(PRICE);
    given(marketplaceContractCallSupport.loadAllowance(BUYER, ESCROW, TOKEN)).willReturn(PRICE);

    adapter.precheck(check());

    then(marketplaceContractCallSupport).should().isSupportedToken(ESCROW, TOKEN);
    then(marketplaceContractCallSupport).should().loadBalance(BUYER, TOKEN);
    then(marketplaceContractCallSupport).should().loadAllowance(BUYER, ESCROW, TOKEN);
  }

  @Test
  @DisplayName("configured token이 escrow에서 지원되지 않으면 balance/allowance 조회 전에 차단한다")
  void precheck_blocks_unsupported_token() {
    MarketplacePurchaseFundingPrecheckAdapter adapter =
        new MarketplacePurchaseFundingPrecheckAdapter(marketplaceContractCallSupport);
    given(marketplaceContractCallSupport.isSupportedToken(ESCROW, TOKEN)).willReturn(false);

    assertThatThrownBy(() -> adapter.precheck(check()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("does not support");

    then(marketplaceContractCallSupport)
        .should(org.mockito.Mockito.never())
        .loadBalance(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("token balance가 부족하면 MARKETPLACE_INSUFFICIENT_TOKEN_BALANCE로 차단한다")
  void precheck_blocks_insufficient_balance() {
    MarketplacePurchaseFundingPrecheckAdapter adapter =
        new MarketplacePurchaseFundingPrecheckAdapter(marketplaceContractCallSupport);
    given(marketplaceContractCallSupport.isSupportedToken(ESCROW, TOKEN)).willReturn(true);
    given(marketplaceContractCallSupport.loadBalance(BUYER, TOKEN))
        .willReturn(PRICE.subtract(BigInteger.ONE));

    assertThatThrownBy(() -> adapter.precheck(check()))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getCode())
                    .isEqualTo(ErrorCode.MARKETPLACE_INSUFFICIENT_TOKEN_BALANCE.getCode()));

    then(marketplaceContractCallSupport)
        .should(org.mockito.Mockito.never())
        .loadAllowance(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("allowance가 부족하면 MARKETPLACE_INSUFFICIENT_ALLOWANCE로 차단한다")
  void precheck_blocks_insufficient_allowance() {
    MarketplacePurchaseFundingPrecheckAdapter adapter =
        new MarketplacePurchaseFundingPrecheckAdapter(marketplaceContractCallSupport);
    given(marketplaceContractCallSupport.isSupportedToken(ESCROW, TOKEN)).willReturn(true);
    given(marketplaceContractCallSupport.loadBalance(BUYER, TOKEN)).willReturn(PRICE);
    given(marketplaceContractCallSupport.loadAllowance(BUYER, ESCROW, TOKEN))
        .willReturn(PRICE.subtract(BigInteger.ONE));

    assertThatThrownBy(() -> adapter.precheck(check()))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getCode())
                    .isEqualTo(ErrorCode.MARKETPLACE_INSUFFICIENT_ALLOWANCE.getCode()));
  }

  private PrecheckMarketplacePurchaseFundingPort.PurchaseFundingCheck check() {
    return new PrecheckMarketplacePurchaseFundingPort.PurchaseFundingCheck(
        BUYER, TRAINER, ESCROW, TOKEN, PRICE);
  }
}
