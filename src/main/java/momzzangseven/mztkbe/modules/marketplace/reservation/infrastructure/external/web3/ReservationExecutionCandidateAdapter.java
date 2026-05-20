package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionCandidateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCandidatePort;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentCandidateResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentCandidatesQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentCandidatesUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Cross-module adapter for reservation-owned execution candidate decisions. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
@ConditionalOnBean(GetExecutionIntentCandidatesUseCase.class)
@Primary
public class ReservationExecutionCandidateAdapter implements LoadReservationExecutionCandidatePort {

  private static final int CANDIDATE_LIMIT = 20;

  private final GetExecutionIntentCandidatesUseCase getExecutionIntentCandidatesUseCase;
  private final ObjectMapper objectMapper;

  @Override
  public List<ReservationExecutionCandidateView> findByReservationResource(Long reservationId) {
    return findByReservationResource(reservationId, null);
  }

  @Override
  public List<ReservationExecutionCandidateView> findByReservationResource(
      Long reservationId, String orderKey) {
    if (reservationId == null) {
      return List.of();
    }
    Map<String, ReservationExecutionCandidateView> byIntentId = new LinkedHashMap<>();
    loadByResourceId(String.valueOf(reservationId))
        .forEach(candidate -> byIntentId.putIfAbsent(candidate.executionIntentId(), candidate));
    if (orderKey != null
        && !orderKey.isBlank()
        && !orderKey.equals(String.valueOf(reservationId))) {
      loadByResourceId(orderKey)
          .forEach(candidate -> byIntentId.putIfAbsent(candidate.executionIntentId(), candidate));
    }
    return List.copyOf(byIntentId.values());
  }

  private List<ReservationExecutionCandidateView> loadByResourceId(String resourceId) {
    return getExecutionIntentCandidatesUseCase
        .execute(
            new GetExecutionIntentCandidatesQuery(
                ExecutionResourceTypeCode.ORDER, resourceId, CANDIDATE_LIMIT))
        .stream()
        .map(this::toView)
        .toList();
  }

  private ReservationExecutionCandidateView toView(GetExecutionIntentCandidateResult result) {
    ReservationExecutionCandidateView.PayloadEvidence evidence =
        payloadEvidence(result.payloadSnapshotJson());
    return new ReservationExecutionCandidateView(
        result.executionIntentId(),
        result.executionIntentStatus().name(),
        result.actionType().name(),
        result.requesterUserId(),
        result.transactionId(),
        result.transactionStatus() == null ? null : result.transactionStatus().name(),
        result.txHash(),
        evidence,
        evidence != null);
  }

  private ReservationExecutionCandidateView.PayloadEvidence payloadEvidence(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      JsonNode root = objectMapper.readTree(json);
      return new ReservationExecutionCandidateView.PayloadEvidence(
          integerValue(root, "payloadVersion"),
          longValue(root, "reservationId"),
          longValue(root, "escrowId"),
          longValue(root, "actionStateId"),
          textValue(root, "pendingAttemptToken"),
          textValue(root, "orderKey"),
          textValue(root, "actionType"));
    } catch (Exception e) {
      return null;
    }
  }

  private static String textValue(JsonNode root, String fieldName) {
    JsonNode node = root.get(fieldName);
    return node == null || node.isNull() ? null : node.asText();
  }

  private static Long longValue(JsonNode root, String fieldName) {
    JsonNode node = root.get(fieldName);
    return node == null || node.isNull() || !node.canConvertToLong() ? null : node.asLong();
  }

  private static Integer integerValue(JsonNode root, String fieldName) {
    JsonNode node = root.get(fieldName);
    return node == null || node.isNull() || !node.canConvertToInt() ? null : node.asInt();
  }
}
