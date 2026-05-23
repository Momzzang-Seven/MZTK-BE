package momzzangseven.mztkbe.modules.web3.marketplace.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminExecutionAuthorityStatus;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminSignerWalletView;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.CheckMarketplaceAdminRelayerRegistrationPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceAdminSignerWalletPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.VerifyMarketplaceAdminSignerWalletPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoadMarketplaceAdminExecutionAuthorityServiceTest {

  private static final String SIGNER = "0x5555555555555555555555555555555555555555";

  @Mock private LoadMarketplaceAdminSignerWalletPort loadWalletPort;
  @Mock private VerifyMarketplaceAdminSignerWalletPort verifyWalletPort;
  @Mock private CheckMarketplaceAdminRelayerRegistrationPort checkRelayerPort;

  private LoadMarketplaceAdminExecutionAuthorityService service;

  @BeforeEach
  void setUp() {
    service =
        new LoadMarketplaceAdminExecutionAuthorityService(
            loadWalletPort, verifyWalletPort, checkRelayerPort);
  }

  @Test
  @DisplayName("signer wallet이 없으면 server relayer only unavailable 상태를 반환한다")
  void executeReturnsUnavailableWhenWalletMissing() {
    given(loadWalletPort.load()).willReturn(Optional.empty());

    MarketplaceAdminExecutionAuthorityStatus result = service.execute();

    assertThat(result.serverSignerAvailable()).isFalse();
    assertThat(result.serverSignerAddress()).isNull();
    assertThat(result.relayerRegistered()).isFalse();
    assertThat(result.relayerRegistrationStatus())
        .isEqualTo(MarketplaceAdminExecutionAuthorityStatus.RELAYER_REGISTRATION_UNCHECKED);
    then(verifyWalletPort).shouldHaveNoInteractions();
    then(checkRelayerPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("wallet address가 blank이면 signer unavailable로 처리한다")
  void executeTreatsBlankWalletAddressAsUnavailable() {
    given(loadWalletPort.load()).willReturn(Optional.of(wallet(" ")));

    MarketplaceAdminExecutionAuthorityStatus result = service.execute();

    assertThat(result.serverSignerAvailable()).isFalse();
    assertThat(result.relayerRegistrationStatus())
        .isEqualTo(MarketplaceAdminExecutionAuthorityStatus.RELAYER_REGISTRATION_UNCHECKED);
    then(verifyWalletPort).shouldHaveNoInteractions();
    then(checkRelayerPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("wallet address가 EVM 주소가 아니면 signer unavailable로 처리한다")
  void executeTreatsInvalidWalletAddressAsUnavailable() {
    given(loadWalletPort.load()).willReturn(Optional.of(wallet("invalid-address")));

    MarketplaceAdminExecutionAuthorityStatus result = service.execute();

    assertThat(result.serverSignerAvailable()).isFalse();
    assertThat(result.relayerRegistrationStatus())
        .isEqualTo(MarketplaceAdminExecutionAuthorityStatus.RELAYER_REGISTRATION_UNCHECKED);
    then(verifyWalletPort).shouldHaveNoInteractions();
    then(checkRelayerPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("inactive wallet은 signer unavailable로 처리한다")
  void executeTreatsInactiveWalletAsUnavailable() {
    given(loadWalletPort.load())
        .willReturn(
            Optional.of(new MarketplaceAdminSignerWalletView("alias", "kms", SIGNER, false)));

    MarketplaceAdminExecutionAuthorityStatus result = service.execute();

    assertThat(result.serverSignerAvailable()).isFalse();
    assertThat(result.serverSignerAddress()).isNull();
    assertThat(result.relayerRegistrationStatus())
        .isEqualTo(MarketplaceAdminExecutionAuthorityStatus.RELAYER_REGISTRATION_UNCHECKED);
    then(checkRelayerPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("signer verify 실패는 signer unavailable로 처리한다")
  void executeTreatsVerifyFailureAsUnavailable() {
    given(loadWalletPort.load()).willReturn(Optional.of(wallet(SIGNER)));
    willThrow(new IllegalStateException("kms disabled")).given(verifyWalletPort).verify("alias");

    MarketplaceAdminExecutionAuthorityStatus result = service.execute();

    assertThat(result.serverSignerAvailable()).isFalse();
    assertThat(result.serverSignerAddress()).isNull();
    assertThat(result.relayerRegistrationStatus())
        .isEqualTo(MarketplaceAdminExecutionAuthorityStatus.RELAYER_REGISTRATION_UNCHECKED);
    then(checkRelayerPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("relayer 미등록이면 NOT_REGISTERED 상태를 반환한다")
  void executeReturnsNotRegisteredWhenRelayerMissing() {
    given(loadWalletPort.load()).willReturn(Optional.of(wallet(SIGNER)));
    given(checkRelayerPort.isRegistered(SIGNER)).willReturn(false);

    MarketplaceAdminExecutionAuthorityStatus result = service.execute();

    assertThat(result.serverSignerAvailable()).isTrue();
    assertThat(result.serverSignerAddress()).isEqualTo(SIGNER);
    assertThat(result.relayerRegistered()).isFalse();
    assertThat(result.relayerRegistrationStatus())
        .isEqualTo(MarketplaceAdminExecutionAuthorityStatus.RELAYER_REGISTRATION_NOT_REGISTERED);
  }

  @Test
  @DisplayName("relayer 조회 실패이면 CHECK_FAILED 상태를 반환한다")
  void executeReturnsCheckFailedWhenRelayerLookupFails() {
    given(loadWalletPort.load()).willReturn(Optional.of(wallet(SIGNER)));
    given(checkRelayerPort.isRegistered(SIGNER)).willThrow(new IllegalStateException("rpc down"));

    MarketplaceAdminExecutionAuthorityStatus result = service.execute();

    assertThat(result.serverSignerAvailable()).isTrue();
    assertThat(result.relayerRegistered()).isFalse();
    assertThat(result.relayerRegistrationStatus())
        .isEqualTo(MarketplaceAdminExecutionAuthorityStatus.RELAYER_REGISTRATION_CHECK_FAILED);
  }

  @Test
  @DisplayName("signer와 relayer가 모두 유효하면 REGISTERED 상태를 반환한다")
  void executeReturnsRegisteredWhenSignerAndRelayerReady() {
    given(loadWalletPort.load()).willReturn(Optional.of(wallet(SIGNER)));
    given(checkRelayerPort.isRegistered(SIGNER)).willReturn(true);

    MarketplaceAdminExecutionAuthorityStatus result = service.execute();

    assertThat(result.requiresUserSignature()).isFalse();
    assertThat(result.authorityModel())
        .isEqualTo(MarketplaceAdminExecutionAuthorityStatus.SERVER_RELAYER_ONLY);
    assertThat(result.serverSignerAvailable()).isTrue();
    assertThat(result.serverSignerAddress()).isEqualTo(SIGNER);
    assertThat(result.relayerRegistered()).isTrue();
    assertThat(result.relayerRegistrationStatus())
        .isEqualTo(MarketplaceAdminExecutionAuthorityStatus.RELAYER_REGISTRATION_REGISTERED);
  }

  private MarketplaceAdminSignerWalletView wallet(String address) {
    return new MarketplaceAdminSignerWalletView("alias", "kms", address, true);
  }
}
