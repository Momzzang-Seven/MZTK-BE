package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.external.treasury;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.VerifyTreasuryWalletForSignUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.stereotype.Component;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerifyTreasuryWalletForSignAdapter")
class VerifyTreasuryWalletForSignAdapterTest {

  private static final String WALLET_ALIAS = "reward-treasury";

  @Mock private VerifyTreasuryWalletForSignUseCase verifyTreasuryWalletForSignUseCase;

  @InjectMocks private VerifyTreasuryWalletForSignAdapter adapter;

  @Test
  @DisplayName("[M-160] verify — alias 그대로 use case 에 전달")
  void verify_passesAliasThroughToUseCase() {
    doNothing().when(verifyTreasuryWalletForSignUseCase).execute(WALLET_ALIAS);

    assertThatCode(() -> adapter.verify(WALLET_ALIAS)).doesNotThrowAnyException();

    verify(verifyTreasuryWalletForSignUseCase).execute(WALLET_ALIAS);
  }

  @Test
  @DisplayName("[M-161] verify — TreasuryWalletStateException 그대로 throw (wrapping 없음)")
  void verify_propagatesTreasuryWalletStateExceptionUnchanged() {
    TreasuryWalletStateException expected = new TreasuryWalletStateException("KMS key disabled");
    doThrow(expected).when(verifyTreasuryWalletForSignUseCase).execute(WALLET_ALIAS);

    assertThatThrownBy(() -> adapter.verify(WALLET_ALIAS))
        .isExactlyInstanceOf(TreasuryWalletStateException.class)
        .isSameAs(expected);
  }

  @Test
  @DisplayName("[M-162] @Component 빈 이름이 transactionVerifyTreasuryWalletForSignAdapter")
  void componentBeanName_isNamespacedToTransactionModule() {
    Component component = VerifyTreasuryWalletForSignAdapter.class.getAnnotation(Component.class);

    assertThat(component).isNotNull();
    assertThat(component.value()).isEqualTo("transactionVerifyTreasuryWalletForSignAdapter");
  }
}
