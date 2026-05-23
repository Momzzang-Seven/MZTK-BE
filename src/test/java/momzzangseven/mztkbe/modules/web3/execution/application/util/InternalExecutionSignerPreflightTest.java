package momzzangseven.mztkbe.modules.web3.execution.application.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.InternalExecutionSignerGates;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionSignerWalletPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.VerifyTreasuryWalletForSignPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalExecutionSignerPreflightTest {

  private static final String SIGNER = "0x5555555555555555555555555555555555555555";

  @Mock private LoadInternalExecutionSignerWalletPort loadWalletPort;
  @Mock private VerifyTreasuryWalletForSignPort verifyWalletPort;

  private InternalExecutionSignerPreflight preflight;

  @BeforeEach
  void setUp() {
    preflight = new InternalExecutionSignerPreflight(loadWalletPort, verifyWalletPort);
  }

  @Test
  @DisplayName("action type 목록이 null 또는 empty이면 차단한다")
  void preflightRejectsMissingActionTypes() {
    assertThatThrownBy(() -> preflight.preflight(null))
        .isInstanceOf(Web3InvalidInputException.class);
    assertThatThrownBy(() -> preflight.preflight(List.of()))
        .isInstanceOf(Web3InvalidInputException.class);
  }

  @Test
  @DisplayName("action type 목록에 null element가 있으면 차단한다")
  void preflightRejectsNullActionTypeElement() {
    assertThatThrownBy(() -> preflight.preflight(Collections.singletonList(null)))
        .isInstanceOf(Web3InvalidInputException.class);
  }

  @Test
  @DisplayName("signer wallet이 없으면 차단한다")
  void preflightRejectsMissingWallet() {
    given(loadWalletPort.load(ExecutionActionType.MARKETPLACE_ADMIN_REFUND))
        .willReturn(Optional.empty());

    assertThatThrownBy(
            () -> preflight.preflight(List.of(ExecutionActionType.MARKETPLACE_ADMIN_REFUND)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("internal execution signer key is missing");
  }

  @Test
  @DisplayName("inactive wallet이면 차단한다")
  void preflightRejectsInactiveWallet() {
    given(loadWalletPort.load(ExecutionActionType.MARKETPLACE_ADMIN_REFUND))
        .willReturn(Optional.of(wallet(false, "kms", SIGNER)));

    assertThatThrownBy(
            () -> preflight.preflight(List.of(ExecutionActionType.MARKETPLACE_ADMIN_REFUND)))
        .isInstanceOf(Web3InvalidInputException.class);
  }

  @Test
  @DisplayName("kms key id가 blank이면 차단한다")
  void preflightRejectsBlankKmsKeyId() {
    given(loadWalletPort.load(ExecutionActionType.MARKETPLACE_ADMIN_REFUND))
        .willReturn(Optional.of(wallet(true, " ", SIGNER)));

    assertThatThrownBy(
            () -> preflight.preflight(List.of(ExecutionActionType.MARKETPLACE_ADMIN_REFUND)))
        .isInstanceOf(Web3InvalidInputException.class);
  }

  @Test
  @DisplayName("wallet address가 blank이면 차단한다")
  void preflightRejectsBlankWalletAddress() {
    given(loadWalletPort.load(ExecutionActionType.MARKETPLACE_ADMIN_REFUND))
        .willReturn(Optional.of(wallet(true, "kms", " ")));

    assertThatThrownBy(
            () -> preflight.preflight(List.of(ExecutionActionType.MARKETPLACE_ADMIN_REFUND)))
        .isInstanceOf(Web3InvalidInputException.class);
  }

  @Test
  @DisplayName("wallet address가 EVM 주소가 아니면 차단한다")
  void preflightRejectsInvalidWalletAddress() {
    given(loadWalletPort.load(ExecutionActionType.MARKETPLACE_ADMIN_REFUND))
        .willReturn(Optional.of(wallet(true, "kms", "invalid-address")));

    assertThatThrownBy(
            () -> preflight.preflight(List.of(ExecutionActionType.MARKETPLACE_ADMIN_REFUND)))
        .isInstanceOf(Web3InvalidInputException.class);
  }

  @Test
  @DisplayName("wallet verify 실패를 전파한다")
  void preflightPropagatesVerifyFailure() {
    given(loadWalletPort.load(ExecutionActionType.MARKETPLACE_ADMIN_REFUND))
        .willReturn(Optional.of(wallet(true, "kms", SIGNER)));
    willThrow(new IllegalStateException("kms disabled")).given(verifyWalletPort).verify("alias");

    assertThatThrownBy(
            () -> preflight.preflight(List.of(ExecutionActionType.MARKETPLACE_ADMIN_REFUND)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("kms disabled");
  }

  @Test
  @DisplayName("valid wallet이면 action별 signer gate를 반환한다")
  void preflightReturnsSignerGate() {
    given(loadWalletPort.load(ExecutionActionType.MARKETPLACE_ADMIN_REFUND))
        .willReturn(Optional.of(wallet(true, "kms", SIGNER)));

    InternalExecutionSignerGates result =
        preflight.preflight(List.of(ExecutionActionType.MARKETPLACE_ADMIN_REFUND));

    var gate = result.gateFor(ExecutionActionType.MARKETPLACE_ADMIN_REFUND);
    assertThat(gate.walletInfo().walletAlias()).isEqualTo("alias");
    assertThat(gate.signer().walletAlias()).isEqualTo("alias");
    assertThat(gate.signer().kmsKeyId()).isEqualTo("kms");
    assertThat(gate.signer().walletAddress()).isEqualTo(SIGNER);
    then(verifyWalletPort).should().verify("alias");
  }

  private TreasuryWalletInfo wallet(boolean active, String kmsKeyId, String address) {
    return new TreasuryWalletInfo("alias", kmsKeyId, address, active);
  }
}
