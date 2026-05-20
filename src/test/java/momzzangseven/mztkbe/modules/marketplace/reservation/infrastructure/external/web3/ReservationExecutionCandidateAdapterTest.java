package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentCandidateResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentCandidatesQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentCandidatesUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationExecutionCandidateAdapterTest {

  @Mock private GetExecutionIntentCandidatesUseCase getExecutionIntentCandidatesUseCase;

  private ReservationExecutionCandidateAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter =
        new ReservationExecutionCandidateAdapter(
            getExecutionIntentCandidatesUseCase, new ObjectMapper());
  }

  @Test
  void findByReservationResource_mapsPayloadEvidence() {
    when(getExecutionIntentCandidatesUseCase.execute(
            new GetExecutionIntentCandidatesQuery(ExecutionResourceTypeCode.ORDER, "77", 20)))
        .thenReturn(
            List.of(
                new GetExecutionIntentCandidateResult(
                    "intent-1",
                    ExecutionIntentStatus.SIGNED,
                    ExecutionResourceType.ORDER,
                    "77",
                    ExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
                    1L,
                    9L,
                    ExecutionTransactionStatus.PENDING,
                    "0xhash",
                    """
                    {
                      "payloadVersion": 1,
                      "reservationId": 77,
                      "escrowId": 88,
                      "actionStateId": 99,
                      "pendingAttemptToken": "attempt-1",
                      "orderKey": "0xabc",
                      "actionType": "MARKETPLACE_CLASS_PURCHASE"
                    }
                    """)));

    var results = adapter.findByReservationResource(77L);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().payloadEvidenceValid()).isTrue();
    assertThat(results.getFirst().payloadEvidence().reservationId()).isEqualTo(77L);
    assertThat(results.getFirst().payloadEvidence().actionStateId()).isEqualTo(99L);
    assertThat(results.getFirst().transactionStatus()).isEqualTo("PENDING");
  }

  @Test
  void findByReservationResource_marksMalformedPayloadAsInvalidEvidence() {
    when(getExecutionIntentCandidatesUseCase.execute(
            new GetExecutionIntentCandidatesQuery(ExecutionResourceTypeCode.ORDER, "77", 20)))
        .thenReturn(
            List.of(
                new GetExecutionIntentCandidateResult(
                    "intent-1",
                    ExecutionIntentStatus.AWAITING_SIGNATURE,
                    ExecutionResourceType.ORDER,
                    "77",
                    ExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
                    1L,
                    null,
                    null,
                    null,
                    "{not-json")));

    var results = adapter.findByReservationResource(77L);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().payloadEvidenceValid()).isFalse();
    assertThat(results.getFirst().payloadEvidence()).isNull();
  }

  @Test
  void findByReservationResource_loadsReservationIdAndOrderKeyResources() {
    var reservationCandidate =
        new GetExecutionIntentCandidateResult(
            "intent-reservation",
            ExecutionIntentStatus.EXPIRED,
            ExecutionResourceType.ORDER,
            "77",
            ExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
            1L,
            null,
            null,
            null,
            null);
    var orderKeyCandidate =
        new GetExecutionIntentCandidateResult(
            "intent-order-key",
            ExecutionIntentStatus.SIGNED,
            ExecutionResourceType.ORDER,
            "0xabc",
            ExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
            1L,
            null,
            null,
            null,
            null);
    when(getExecutionIntentCandidatesUseCase.execute(
            new GetExecutionIntentCandidatesQuery(ExecutionResourceTypeCode.ORDER, "77", 20)))
        .thenReturn(List.of(reservationCandidate));
    when(getExecutionIntentCandidatesUseCase.execute(
            new GetExecutionIntentCandidatesQuery(ExecutionResourceTypeCode.ORDER, "0xabc", 20)))
        .thenReturn(List.of(orderKeyCandidate));

    var results = adapter.findByReservationResource(77L, "0xabc");

    assertThat(results)
        .extracting("executionIntentId")
        .containsExactly("intent-reservation", "intent-order-key");
  }
}
