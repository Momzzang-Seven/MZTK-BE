package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.wallet.WalletNotConnectedException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaEscrowProperties;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaRewardTokenProperties;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetActiveWalletAddressUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QnaQuestionFundingPrecheckAdapter unit test")
class QnaQuestionFundingPrecheckAdapterTest {

  private static final String WALLET = "0x" + "1".repeat(40);
  private static final String TOKEN = "0x" + "2".repeat(40);
  private static final String ESCROW = "0x" + "3".repeat(40);

  @Mock private GetActiveWalletAddressUseCase getActiveWalletAddressUseCase;
  @Mock private QnaContractCallSupport qnaContractCallSupport;

  private QnaRewardTokenProperties rewardTokenProperties;
  private QnaEscrowProperties escrowProperties;
  private QnaQuestionFundingPrecheckAdapter adapter;

  @BeforeEach
  void setUp() {
    rewardTokenProperties = new QnaRewardTokenProperties();
    rewardTokenProperties.setEnabled(true);
    rewardTokenProperties.setTokenContractAddress(TOKEN);
    rewardTokenProperties.setDecimals(18);

    escrowProperties = new QnaEscrowProperties();
    escrowProperties.setQnaContractAddress(ESCROW);

    adapter =
        new QnaQuestionFundingPrecheckAdapter(
            getActiveWalletAddressUseCase,
            rewardTokenProperties,
            escrowProperties,
            qnaContractCallSupport);
  }

  @Test
  @DisplayName("precheck passes when wallet active, token supported, and allowance sufficient")
  void precheck_passes_whenAllowanceSufficient() {
    when(getActiveWalletAddressUseCase.execute(7L)).thenReturn(Optional.of(WALLET));
    when(qnaContractCallSupport.isSupportedToken(ESCROW, TOKEN)).thenReturn(true);
    BigInteger expectedAmountWei = BigInteger.valueOf(50L).multiply(BigInteger.TEN.pow(18));
    when(qnaContractCallSupport.loadAllowance(WALLET, ESCROW, TOKEN))
        .thenReturn(expectedAmountWei);

    adapter.precheck(new PrecheckQuestionCreateCommand(7L, 50L));

    ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
    verify(qnaContractCallSupport)
        .loadAllowance(ownerCaptor.capture(), org.mockito.ArgumentMatchers.eq(ESCROW),
            org.mockito.ArgumentMatchers.eq(TOKEN));
    assertThat(ownerCaptor.getValue()).isEqualTo(WALLET);
  }

  @Test
  @DisplayName("precheck throws when command is invalid")
  void precheck_throws_whenCommandInvalid() {
    assertThatThrownBy(() -> adapter.precheck(new PrecheckQuestionCreateCommand(null, 50L)))
        .isInstanceOf(Web3InvalidInputException.class);

    verify(getActiveWalletAddressUseCase, never()).execute(org.mockito.ArgumentMatchers.any());
    verify(qnaContractCallSupport, never())
        .isSupportedToken(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    verify(qnaContractCallSupport, never())
        .loadAllowance(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("precheck throws WalletNotConnectedException when wallet is missing")
  void precheck_throws_whenWalletMissing() {
    when(getActiveWalletAddressUseCase.execute(7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adapter.precheck(new PrecheckQuestionCreateCommand(7L, 50L)))
        .isInstanceOf(WalletNotConnectedException.class);

    verify(qnaContractCallSupport, never())
        .isSupportedToken(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    verify(qnaContractCallSupport, never())
        .loadAllowance(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("precheck throws Web3InvalidInputException when escrow does not support the token")
  void precheck_throws_whenTokenNotSupported() {
    when(getActiveWalletAddressUseCase.execute(7L)).thenReturn(Optional.of(WALLET));
    when(qnaContractCallSupport.isSupportedToken(ESCROW, TOKEN)).thenReturn(false);

    assertThatThrownBy(() -> adapter.precheck(new PrecheckQuestionCreateCommand(7L, 50L)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("does not support configured reward token");

    verify(qnaContractCallSupport, never())
        .loadAllowance(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("precheck throws Web3InvalidInputException when allowance is insufficient")
  void precheck_throws_whenAllowanceInsufficient() {
    when(getActiveWalletAddressUseCase.execute(7L)).thenReturn(Optional.of(WALLET));
    when(qnaContractCallSupport.isSupportedToken(ESCROW, TOKEN)).thenReturn(true);
    when(qnaContractCallSupport.loadAllowance(WALLET, ESCROW, TOKEN)).thenReturn(BigInteger.ZERO);

    assertThatThrownBy(() -> adapter.precheck(new PrecheckQuestionCreateCommand(7L, 50L)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("insufficient allowance");
  }
}
