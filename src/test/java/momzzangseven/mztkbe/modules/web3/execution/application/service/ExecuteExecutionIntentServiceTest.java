package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteTransactionalExecutionIntentDelegatePort;
import momzzangseven.mztkbe.modules.web3.execution.application.util.SponsorWalletPreflight;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests the orchestrator-level preflight + delegate dispatch contract.
 *
 * <p>Per-mode signing logic lives in {@link TransactionalExecuteExecutionIntentDelegateTest}.
 * Sponsor-wallet structural fail-fast cases live in {@code SponsorWalletPreflightTest}.
 */
@ExtendWith(MockitoExtension.class)
class ExecuteExecutionIntentServiceTest {

  private static final String SPONSOR_ALIAS = "sponsor-treasury";
  private static final String SPONSOR_KMS_KEY_ID = "alias/sponsor-treasury";
  private static final String SPONSOR_ADDRESS = "0x" + "6".repeat(40);

  @Mock private ExecuteTransactionalExecutionIntentDelegatePort delegate;
  @Mock private SponsorWalletPreflight sponsorWalletPreflight;

  private ExecuteExecutionIntentService service;

  @BeforeEach
  void setUp() {
    service = new ExecuteExecutionIntentService(delegate, sponsorWalletPreflight);
  }

  private SponsorWalletGate activeGate() {
    return new SponsorWalletGate(
        new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, SPONSOR_ADDRESS, true),
        new TreasurySigner(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, SPONSOR_ADDRESS));
  }

  private ExecuteExecutionIntentCommand command() {
    return new ExecuteExecutionIntentCommand(7L, "intent-1", "0xauth", "0xsubmit", null);
  }

  @Test
  void execute_propagatesPreflightWeb3InvalidInput_withoutCallingDelegate() {
    when(sponsorWalletPreflight.preflight())
        .thenThrow(new Web3InvalidInputException("sponsor signer key is missing"));

    assertThatThrownBy(() -> service.execute(command()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("sponsor signer key is missing");
    verify(delegate, never()).execute(any(), any());
  }

  @Test
  void execute_propagatesTreasuryWalletStateException_withoutCallingDelegate() {
    when(sponsorWalletPreflight.preflight())
        .thenThrow(new TreasuryWalletStateException("KMS key disabled"));

    assertThatThrownBy(() -> service.execute(command()))
        .isInstanceOf(TreasuryWalletStateException.class)
        .hasMessageContaining("KMS key disabled");
    verify(delegate, never()).execute(any(), any());
  }

  @Test
  void execute_invokesDelegateWithSponsorWalletGate_whenPreflightPasses() {
    SponsorWalletGate gate = activeGate();
    when(sponsorWalletPreflight.preflight()).thenReturn(gate);
    ExecuteExecutionIntentResult expected =
        new ExecuteExecutionIntentResult(
            "intent-1",
            ExecutionIntentStatus.PENDING_ONCHAIN,
            42L,
            ExecutionTransactionStatus.PENDING,
            "0xhash");
    ExecuteExecutionIntentCommand command = command();
    when(delegate.execute(eq(command), eq(gate))).thenReturn(expected);

    ExecuteExecutionIntentResult actual = service.execute(command);

    assertThat(actual).isSameAs(expected);
    verify(delegate).execute(command, gate);
  }
}
