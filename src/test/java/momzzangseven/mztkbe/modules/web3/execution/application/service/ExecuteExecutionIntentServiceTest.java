package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.VerifyTreasuryWalletForSignPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests the orchestrator-level preflight + delegate dispatch contract. Per-mode signing logic lives
 * in {@link TransactionalExecuteExecutionIntentDelegateTest}.
 */
@ExtendWith(MockitoExtension.class)
class ExecuteExecutionIntentServiceTest {

  private static final String SPONSOR_ALIAS = "sponsor-treasury";
  private static final String SPONSOR_KMS_KEY_ID = "alias/sponsor-treasury";
  private static final String SPONSOR_ADDRESS = "0x" + "6".repeat(40);

  @Mock private TransactionalExecuteExecutionIntentDelegate delegate;
  @Mock private LoadSponsorTreasuryWalletPort loadSponsorTreasuryWalletPort;
  @Mock private VerifyTreasuryWalletForSignPort verifyTreasuryWalletForSignPort;

  private ExecuteExecutionIntentService service;

  @BeforeEach
  void setUp() {
    service =
        new ExecuteExecutionIntentService(
            delegate, loadSponsorTreasuryWalletPort, verifyTreasuryWalletForSignPort);
  }

  private TreasuryWalletInfo activeSponsorWalletInfo() {
    return new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, SPONSOR_ADDRESS, true);
  }

  private ExecuteExecutionIntentCommand command() {
    return new ExecuteExecutionIntentCommand(7L, "intent-1", "0xauth", "0xsubmit", null);
  }

  @Test
  void execute_throwsSponsorMissing_whenSponsorTreasuryWalletNotRegistered() {
    when(loadSponsorTreasuryWalletPort.load()).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(command()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("sponsor signer key is missing");
    verify(delegate, never()).execute(any(), any());
  }

  @Test
  void execute_throwsSponsorMissing_whenSponsorWalletNotActive() {
    when(loadSponsorTreasuryWalletPort.load())
        .thenReturn(
            Optional.of(
                new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, SPONSOR_ADDRESS, false)));

    assertThatThrownBy(() -> service.execute(command()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("sponsor signer key is missing");
    verify(delegate, never()).execute(any(), any());
  }

  @Test
  void execute_throwsSponsorMissing_whenKmsKeyIdNull() {
    when(loadSponsorTreasuryWalletPort.load())
        .thenReturn(
            Optional.of(new TreasuryWalletInfo(SPONSOR_ALIAS, null, SPONSOR_ADDRESS, true)));

    assertThatThrownBy(() -> service.execute(command()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("sponsor signer key is missing");
    verify(delegate, never()).execute(any(), any());
  }

  @Test
  void execute_throwsSponsorMissing_whenKmsKeyIdBlank() {
    when(loadSponsorTreasuryWalletPort.load())
        .thenReturn(Optional.of(new TreasuryWalletInfo(SPONSOR_ALIAS, "", SPONSOR_ADDRESS, true)));

    assertThatThrownBy(() -> service.execute(command()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("sponsor signer key is missing");
    verify(delegate, never()).execute(any(), any());
  }

  @Test
  void execute_throwsSponsorMissing_whenWalletAddressNull() {
    when(loadSponsorTreasuryWalletPort.load())
        .thenReturn(
            Optional.of(new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, null, true)));

    assertThatThrownBy(() -> service.execute(command()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("sponsor signer key is missing");
    verify(delegate, never()).execute(any(), any());
  }

  @Test
  void execute_throwsSponsorMissing_whenWalletAddressBlank() {
    when(loadSponsorTreasuryWalletPort.load())
        .thenReturn(
            Optional.of(new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, "", true)));

    assertThatThrownBy(() -> service.execute(command()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("sponsor signer key is missing");
    verify(delegate, never()).execute(any(), any());
  }

  @Test
  void execute_propagatesVerifyForSignFailure_withoutCallingDelegate() {
    when(loadSponsorTreasuryWalletPort.load()).thenReturn(Optional.of(activeSponsorWalletInfo()));
    org.mockito.Mockito.doThrow(new TreasuryWalletStateException("KMS key disabled"))
        .when(verifyTreasuryWalletForSignPort)
        .verify(SPONSOR_ALIAS);

    assertThatThrownBy(() -> service.execute(command()))
        .isInstanceOf(TreasuryWalletStateException.class)
        .hasMessageContaining("KMS key disabled");
    verify(delegate, never()).execute(any(), any());
  }

  @Test
  void execute_invokesDelegateWithSponsorWalletGate_whenPreflightPasses() {
    when(loadSponsorTreasuryWalletPort.load()).thenReturn(Optional.of(activeSponsorWalletInfo()));
    ExecuteExecutionIntentResult expected =
        new ExecuteExecutionIntentResult(
            "intent-1",
            ExecutionIntentStatus.PENDING_ONCHAIN,
            42L,
            ExecutionTransactionStatus.PENDING,
            "0xhash");
    ExecuteExecutionIntentCommand command = command();
    ArgumentCaptor<SponsorWalletGate> gateCaptor = ArgumentCaptor.forClass(SponsorWalletGate.class);
    when(delegate.execute(eq(command), gateCaptor.capture())).thenReturn(expected);

    ExecuteExecutionIntentResult actual = service.execute(command);

    assertThat(actual).isSameAs(expected);
    verify(verifyTreasuryWalletForSignPort).verify(SPONSOR_ALIAS);
    SponsorWalletGate gate = gateCaptor.getValue();
    assertThat(gate.walletInfo().walletAlias()).isEqualTo(SPONSOR_ALIAS);
    assertThat(gate.signer().walletAlias()).isEqualTo(SPONSOR_ALIAS);
    assertThat(gate.signer().kmsKeyId()).isEqualTo(SPONSOR_KMS_KEY_ID);
    assertThat(gate.signer().walletAddress()).isEqualTo(SPONSOR_ADDRESS);
  }
}
