package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.treasury;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletNotProvisionedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.MarketplaceServerSigPreimage;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.MarketplaceServerSigResult;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestResult;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignDigestUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletByRoleUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.utils.Numeric;

@ExtendWith(MockitoExtension.class)
@DisplayName("SignMarketplaceServerSigAdapter 단위 테스트")
class SignMarketplaceServerSigAdapterTest {

  private static final String ESCROW = "0x4444444444444444444444444444444444444444";
  private static final String SIGNER = "0x5555555555555555555555555555555555555555";
  private static final String KMS_KEY_ID = "arn:aws:kms:::key/test-marketplace-signer";
  private static final String BUYER = "0x1111111111111111111111111111111111111111";
  private static final String TRAINER = "0x2222222222222222222222222222222222222222";
  private static final String TOKEN = "0x3333333333333333333333333333333333333333";
  private static final String ORDER_KEY =
      "0x00000000000000000000000000000000123e4567e89b12d3a456426614174000";
  private static final byte[] R = filled((byte) 0x11);
  private static final byte[] S = filled((byte) 0x22);
  private static final byte V = 27;

  @Mock private SignDigestUseCase signDigestUseCase;
  @Mock private LoadTreasuryWalletByRoleUseCase loadTreasuryWalletByRoleUseCase;

  private SignMarketplaceServerSigAdapter sut;

  @BeforeEach
  void setUp() {
    sut =
        new SignMarketplaceServerSigAdapter(
            Clock.fixed(Instant.ofEpochSecond(1_007), ZoneOffset.UTC),
            signDigestUseCase,
            loadTreasuryWalletByRoleUseCase,
            new MarketplaceTypedDataDigestBuilder(),
            10L,
            ESCROW,
            "MarketplaceEscrow",
            "1",
            7);
  }

  @Test
  @DisplayName("MARKETPLACE_SIGNER로 purchaseClass digest를 서명하고 signedAt skew와 signingInstant를 분리한다")
  void sign_purchaseClass_usesMarketplaceSignerAndGoldenDigest() {
    given(loadTreasuryWalletByRoleUseCase.execute(TreasuryRole.MARKETPLACE_SIGNER))
        .willReturn(Optional.of(activeMarketplaceSigner()));
    given(signDigestUseCase.execute(any(SignDigestCommand.class)))
        .willReturn(new SignDigestResult(R, S, V));

    MarketplaceServerSigResult result =
        sut.sign(
            new MarketplaceServerSigPreimage.PurchaseClassPreimage(
                BUYER, ORDER_KEY, TOKEN, TRAINER, BigInteger.valueOf(50_000)));

    ArgumentCaptor<SignDigestCommand> captor = ArgumentCaptor.forClass(SignDigestCommand.class);
    then(signDigestUseCase).should().execute(captor.capture());
    SignDigestCommand command = captor.getValue();
    assertThat(command.kmsKeyId()).isEqualTo(KMS_KEY_ID);
    assertThat(command.expectedAddress()).isEqualTo(SIGNER);
    assertThat(Numeric.toHexString(command.digest()))
        .isEqualTo("0x5d6fa02e2c9384c14bc9ba6d42315cbf364b716f85c58d43236ffcbaf7b32d25");
    then(loadTreasuryWalletByRoleUseCase).should().execute(TreasuryRole.MARKETPLACE_SIGNER);

    assertThat(result.signedAt()).isEqualTo(1_000L);
    assertThat(result.signingInstant()).isEqualTo(Instant.ofEpochSecond(1_007));
    assertThat(result.signatureBytes()).hasSize(65);
    assertThat(result.signatureBytes()[64]).isEqualTo(V);
  }

  @Test
  @DisplayName("preimage가 null이면 digest 생성 전에 거부한다")
  void sign_rejectsNullPreimage() {
    assertThatThrownBy(() -> sut.sign(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("preimage");

    then(loadTreasuryWalletByRoleUseCase).shouldHaveNoInteractions();
    then(signDigestUseCase).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("MARKETPLACE_SIGNER가 없으면 서명하지 않는다")
  void sign_rejectsMissingMarketplaceSigner() {
    given(loadTreasuryWalletByRoleUseCase.execute(TreasuryRole.MARKETPLACE_SIGNER))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> sut.sign(purchasePreimage()))
        .isInstanceOf(TreasuryWalletNotProvisionedException.class)
        .hasMessageContaining("MARKETPLACE_SIGNER");

    then(signDigestUseCase).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("MARKETPLACE_SIGNER가 ACTIVE가 아니면 서명하지 않는다")
  void sign_rejectsInactiveMarketplaceSigner() {
    given(loadTreasuryWalletByRoleUseCase.execute(TreasuryRole.MARKETPLACE_SIGNER))
        .willReturn(Optional.of(marketplaceSigner(TreasuryWalletStatus.DISABLED)));

    assertThatThrownBy(() -> sut.sign(purchasePreimage()))
        .isInstanceOf(TreasuryWalletNotProvisionedException.class)
        .hasMessageContaining("DISABLED");

    then(signDigestUseCase).shouldHaveNoInteractions();
  }

  private static TreasuryWalletView activeMarketplaceSigner() {
    return marketplaceSigner(TreasuryWalletStatus.ACTIVE);
  }

  private static TreasuryWalletView marketplaceSigner(TreasuryWalletStatus status) {
    return new TreasuryWalletView(
        TreasuryRole.MARKETPLACE_SIGNER.toAlias(),
        TreasuryRole.MARKETPLACE_SIGNER,
        KMS_KEY_ID,
        SIGNER,
        status,
        TreasuryKeyOrigin.IMPORTED,
        null,
        null);
  }

  private static MarketplaceServerSigPreimage purchasePreimage() {
    return new MarketplaceServerSigPreimage.PurchaseClassPreimage(
        BUYER, ORDER_KEY, TOKEN, TRAINER, BigInteger.valueOf(50_000));
  }

  private static byte[] filled(byte value) {
    byte[] out = new byte[32];
    java.util.Arrays.fill(out, value);
    return out;
  }
}
