package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.FinalizeWalletRegistrationCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.MarkWalletRegistrationApprovalSubmittedCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.MarkWalletRegistrationApprovalTerminatedCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ValidateWalletRegistrationApprovalExecutionCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionPayload;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.FinalizeWalletRegistrationUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.MarkWalletRegistrationApprovalSubmittedUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.MarkWalletRegistrationApprovalTerminatedUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.ValidateWalletRegistrationApprovalExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.domain.vo.WalletApprovalExecutionActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletApprovalExecutionActionHandlerAdapterTest {

  private static final String REGISTRATION_ID = "registration-1";
  private static final String INTENT_ID = "intent-1";
  private static final Long USER_ID = 7L;

  @Mock private ValidateWalletRegistrationApprovalExecutionUseCase validateUseCase;
  @Mock private MarkWalletRegistrationApprovalSubmittedUseCase submittedUseCase;
  @Mock private FinalizeWalletRegistrationUseCase finalizeUseCase;
  @Mock private MarkWalletRegistrationApprovalTerminatedUseCase terminatedUseCase;

  private WalletApprovalExecutionActionHandlerAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter =
        new WalletApprovalExecutionActionHandlerAdapter(
            new ObjectMapper(),
            validateUseCase,
            submittedUseCase,
            finalizeUseCase,
            terminatedUseCase);
  }

  @Test
  void buildActionPlan_rebuildsApprovalCallsFromPayloadSnapshot() {
    ExecutionActionPlan plan = adapter.buildActionPlan(intent());

    assertThat(plan.calls()).hasSize(2);
    assertThat(plan.amountWei()).isZero();
    assertThat(plan.calls().get(0).toAddress()).isEqualTo("0x" + "1".repeat(40));
    assertThat(plan.calls().get(0).valueWei()).isZero();
    assertThat(plan.calls().get(0).data()).isEqualTo("0x095ea7b3");
  }

  @Test
  void supportsActionType_onlySupportsWalletEscrowApprove() {
    assertThat(adapter.supports(ExecutionActionType.WALLET_ESCROW_APPROVE)).isTrue();
    assertThat(adapter.supports(ExecutionActionType.TRANSFER_SEND)).isFalse();
  }

  @Test
  void supportsIntent_whenWalletRegistrationPayloadMatches_returnsTrue() {
    assertThat(adapter.supports(intent())).isTrue();
  }

  @Test
  void supportsIntent_whenActionTypeDiffers_returnsFalse() {
    assertThat(
            adapter.supports(
                intent(
                    ExecutionActionType.TRANSFER_SEND,
                    ExecutionResourceType.WALLET_REGISTRATION,
                    REGISTRATION_ID,
                    payloadJson())))
        .isFalse();
  }

  @Test
  void supportsIntent_whenResourceTypeDiffers_returnsFalse() {
    assertThat(
            adapter.supports(
                intent(
                    ExecutionActionType.WALLET_ESCROW_APPROVE,
                    ExecutionResourceType.TRANSFER,
                    REGISTRATION_ID,
                    payloadJson())))
        .isFalse();
  }

  @Test
  void supportsIntent_whenPayloadRegistrationIdDiffers_returnsFalse() {
    assertThat(
            adapter.supports(
                intent(
                    ExecutionActionType.WALLET_ESCROW_APPROVE,
                    ExecutionResourceType.WALLET_REGISTRATION,
                    REGISTRATION_ID,
                    payloadJson("other-registration"))))
        .isFalse();
  }

  @Test
  void supportsIntent_whenPayloadRegistrationIdIsNull_returnsFalse() {
    assertThat(
            adapter.supports(
                intent(
                    ExecutionActionType.WALLET_ESCROW_APPROVE,
                    ExecutionResourceType.WALLET_REGISTRATION,
                    REGISTRATION_ID,
                    payloadJson(null))))
        .isFalse();
  }

  @Test
  void supportsIntent_whenPayloadIsInvalid_returnsFalse() {
    assertThat(
            adapter.supports(
                intent(
                    ExecutionActionType.WALLET_ESCROW_APPROVE,
                    ExecutionResourceType.WALLET_REGISTRATION,
                    REGISTRATION_ID,
                    "{")))
        .isFalse();
  }

  @Test
  void beforeExecute_validatesRegistrationOwnershipAndLatestIntent() {
    adapter.beforeExecute(intent(), adapter.buildActionPlan(intent()));

    ArgumentCaptor<ValidateWalletRegistrationApprovalExecutionCommand> captor =
        ArgumentCaptor.forClass(ValidateWalletRegistrationApprovalExecutionCommand.class);
    verify(validateUseCase).execute(captor.capture());
    assertThat(captor.getValue().registrationId()).isEqualTo(REGISTRATION_ID);
    assertThat(captor.getValue().executionIntentId()).isEqualTo(INTENT_ID);
    assertThat(captor.getValue().requesterUserId()).isEqualTo(USER_ID);
  }

  @Test
  void afterTransactionSubmitted_callsWalletUseCaseAndSwallowsFailures() {
    org.mockito.Mockito.doThrow(new IllegalStateException("sync failed"))
        .when(submittedUseCase)
        .execute(any());

    assertThatCode(
            () ->
                adapter.afterTransactionSubmitted(
                    intent(),
                    adapter.buildActionPlan(intent()),
                    ExecutionTransactionStatus.PENDING))
        .doesNotThrowAnyException();

    ArgumentCaptor<MarkWalletRegistrationApprovalSubmittedCommand> captor =
        ArgumentCaptor.forClass(MarkWalletRegistrationApprovalSubmittedCommand.class);
    verify(submittedUseCase).execute(captor.capture());
    assertThat(captor.getValue().submittedTransactionStatus()).isEqualTo("PENDING");
  }

  @Test
  void afterExecutionConfirmed_callsFinalizationUseCase() {
    adapter.afterExecutionConfirmed(intent(), adapter.buildActionPlan(intent()));

    ArgumentCaptor<FinalizeWalletRegistrationCommand> captor =
        ArgumentCaptor.forClass(FinalizeWalletRegistrationCommand.class);
    verify(finalizeUseCase).execute(captor.capture());
    assertThat(captor.getValue().registrationId()).isEqualTo(REGISTRATION_ID);
    assertThat(captor.getValue().executionIntentId()).isEqualTo(INTENT_ID);
  }

  @Test
  void afterExecutionTerminated_callsTerminationUseCase() {
    adapter.afterExecutionTerminated(
        intent(),
        adapter.buildActionPlan(intent()),
        ExecutionIntentStatus.CANCELED,
        "user canceled");

    ArgumentCaptor<MarkWalletRegistrationApprovalTerminatedCommand> captor =
        ArgumentCaptor.forClass(MarkWalletRegistrationApprovalTerminatedCommand.class);
    verify(terminatedUseCase).execute(captor.capture());
    assertThat(captor.getValue().terminalExecutionStatus()).isEqualTo("CANCELED");
    assertThat(captor.getValue().failureReason()).isEqualTo("user canceled");
  }

  private static ExecutionIntent intent() {
    return intent(
        ExecutionActionType.WALLET_ESCROW_APPROVE,
        ExecutionResourceType.WALLET_REGISTRATION,
        REGISTRATION_ID,
        payloadJson());
  }

  private static ExecutionIntent intent(
      ExecutionActionType actionType,
      ExecutionResourceType resourceType,
      String resourceId,
      String payloadJson) {
    return ExecutionIntent.builder()
        .publicId(INTENT_ID)
        .resourceType(resourceType)
        .resourceId(resourceId)
        .actionType(actionType)
        .requesterUserId(USER_ID)
        .payloadSnapshotJson(payloadJson)
        .build();
  }

  private static String payloadJson() {
    return payloadJson(REGISTRATION_ID);
  }

  private static String payloadJson(String registrationId) {
    try {
      return new ObjectMapper()
          .writeValueAsString(
              new WalletApprovalExecutionPayload(
                  WalletApprovalExecutionActionType.WALLET_ESCROW_APPROVE,
                  registrationId,
                  USER_ID,
                  "0x" + "a".repeat(40),
                  "0x" + "1".repeat(40),
                  List.of(
                      new WalletApprovalExecutionPayload.ApprovalCall(
                          "0x" + "2".repeat(40),
                          BigInteger.TEN,
                          "0x" + "1".repeat(40),
                          "0x095ea7b3"),
                      new WalletApprovalExecutionPayload.ApprovalCall(
                          "0x" + "3".repeat(40),
                          BigInteger.TEN,
                          "0x" + "1".repeat(40),
                          "0x095ea7b3"))));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }
}
