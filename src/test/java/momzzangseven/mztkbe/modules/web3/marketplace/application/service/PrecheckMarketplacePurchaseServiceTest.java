package momzzangseven.mztkbe.modules.web3.marketplace.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigInteger;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.wallet.WalletNotConnectedException;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.PrecheckMarketplacePurchaseCommand;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceActiveWalletPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplacePurchaseConfigPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.PrecheckMarketplacePurchaseFundingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrecheckMarketplacePurchaseService 단위 테스트")
class PrecheckMarketplacePurchaseServiceTest {

  private static final String BUYER_WALLET = "0x1111111111111111111111111111111111111111";
  private static final String TRAINER_WALLET = "0x2222222222222222222222222222222222222222";
  private static final String ESCROW = "0x3333333333333333333333333333333333333333";
  private static final String TOKEN = "0x4444444444444444444444444444444444444444";
  private static final BigInteger PRICE_BASE_UNITS = new BigInteger("50000000000000000000000");

  @Mock private LoadMarketplaceActiveWalletPort loadMarketplaceActiveWalletPort;
  @Mock private LoadMarketplacePurchaseConfigPort loadMarketplacePurchaseConfigPort;
  @Mock private PrecheckMarketplacePurchaseFundingPort precheckMarketplacePurchaseFundingPort;

  private PrecheckMarketplacePurchaseService sut;

  @BeforeEach
  void setUp() {
    sut =
        new PrecheckMarketplacePurchaseService(
            loadMarketplaceActiveWalletPort,
            loadMarketplacePurchaseConfigPort,
            precheckMarketplacePurchaseFundingPort);
  }

  @Test
  @DisplayName("buyer/trainer wallet, config, funding precheck를 durable hold 생성 전에 수행한다")
  void precheck_delegates_wallet_config_and_funding_check() {
    given(loadMarketplaceActiveWalletPort.loadActiveWalletAddress(10L))
        .willReturn(Optional.of(BUYER_WALLET));
    given(loadMarketplaceActiveWalletPort.loadActiveWalletAddress(20L))
        .willReturn(Optional.of(TRAINER_WALLET));
    given(loadMarketplacePurchaseConfigPort.loadPurchaseConfig())
        .willReturn(
            new LoadMarketplacePurchaseConfigPort.MarketplacePurchaseConfig(ESCROW, TOKEN, 18));

    sut.precheck(command(10L, 20L, PRICE_BASE_UNITS, 50_000));

    ArgumentCaptor<PrecheckMarketplacePurchaseFundingPort.PurchaseFundingCheck> captor =
        ArgumentCaptor.forClass(PrecheckMarketplacePurchaseFundingPort.PurchaseFundingCheck.class);
    then(precheckMarketplacePurchaseFundingPort).should().precheck(captor.capture());
    assertThat(captor.getValue().buyerWalletAddress()).isEqualTo(BUYER_WALLET);
    assertThat(captor.getValue().trainerWalletAddress()).isEqualTo(TRAINER_WALLET);
    assertThat(captor.getValue().escrowContractAddress()).isEqualTo(ESCROW);
    assertThat(captor.getValue().tokenAddress()).isEqualTo(TOKEN);
    assertThat(captor.getValue().priceBaseUnits()).isEqualTo(PRICE_BASE_UNITS);
  }

  @Test
  @DisplayName("buyer user와 trainer user가 같으면 funding precheck 전에 차단한다")
  void precheck_blocks_same_user_self_purchase() {
    assertThatThrownBy(() -> sut.precheck(command(10L, 10L, PRICE_BASE_UNITS, 50_000)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getCode())
                    .isEqualTo(ErrorCode.MARKETPLACE_CANNOT_BUY_OWN_CLASS.getCode()));

    then(precheckMarketplacePurchaseFundingPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("buyer와 trainer의 active wallet이 같으면 contract CannotBuyOwnClass 전에 차단한다")
  void precheck_blocks_same_wallet_self_purchase() {
    given(loadMarketplaceActiveWalletPort.loadActiveWalletAddress(10L))
        .willReturn(Optional.of(BUYER_WALLET));
    given(loadMarketplaceActiveWalletPort.loadActiveWalletAddress(20L))
        .willReturn(Optional.of(BUYER_WALLET));

    assertThatThrownBy(() -> sut.precheck(command(10L, 20L, PRICE_BASE_UNITS, 50_000)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getCode())
                    .isEqualTo(ErrorCode.MARKETPLACE_CANNOT_BUY_OWN_CLASS.getCode()));

    then(loadMarketplacePurchaseConfigPort).shouldHaveNoInteractions();
    then(precheckMarketplacePurchaseFundingPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("trainer active wallet이 없으면 contract InvalidAddress 전에 차단한다")
  void precheck_blocks_missing_trainer_wallet() {
    given(loadMarketplaceActiveWalletPort.loadActiveWalletAddress(10L))
        .willReturn(Optional.of(BUYER_WALLET));
    given(loadMarketplaceActiveWalletPort.loadActiveWalletAddress(20L))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> sut.precheck(command(10L, 20L, PRICE_BASE_UNITS, 50_000)))
        .isInstanceOf(WalletNotConnectedException.class);

    then(precheckMarketplacePurchaseFundingPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("signed amount와 클래스 가격 snapshot이 다르면 funding precheck 전에 차단한다")
  void precheck_blocks_price_mismatch() {
    given(loadMarketplaceActiveWalletPort.loadActiveWalletAddress(10L))
        .willReturn(Optional.of(BUYER_WALLET));
    given(loadMarketplaceActiveWalletPort.loadActiveWalletAddress(20L))
        .willReturn(Optional.of(TRAINER_WALLET));
    given(loadMarketplacePurchaseConfigPort.loadPurchaseConfig())
        .willReturn(
            new LoadMarketplacePurchaseConfigPort.MarketplacePurchaseConfig(ESCROW, TOKEN, 18));

    assertThatThrownBy(
            () ->
                sut.precheck(command(10L, 20L, PRICE_BASE_UNITS.subtract(BigInteger.ONE), 50_000)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getCode())
                    .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_PRICE_MISMATCH.getCode()));

    then(precheckMarketplacePurchaseFundingPort).shouldHaveNoInteractions();
  }

  private PrecheckMarketplacePurchaseCommand command(
      Long buyerUserId, Long trainerUserId, BigInteger signedAmount, Integer bookedPriceAmountKrw) {
    return new PrecheckMarketplacePurchaseCommand(
        buyerUserId, trainerUserId, 100L, 200L, signedAmount, bookedPriceAmountKrw);
  }
}
