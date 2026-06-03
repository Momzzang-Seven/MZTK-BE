package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionResumeView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionResumePort;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummariesQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetLatestExecutionIntentSummaryUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
@Primary
public class ReservationExecutionResumeAdapter implements LoadReservationExecutionResumePort {

  private final GetLatestExecutionIntentSummaryUseCase getLatestExecutionIntentSummaryUseCase;

  @Override
  public Optional<ReservationExecutionResumeView> loadLatest(Long reservationId) {
    return getLatestExecutionIntentSummaryUseCase
        .execute(
            new GetLatestExecutionIntentSummaryQuery(
                ExecutionResourceTypeCode.ORDER, String.valueOf(reservationId)))
        .map(this::toView);
  }

  @Override
  public Map<Long, ReservationExecutionResumeView> loadLatestBatch(
      Collection<Long> reservationIds) {
    if (reservationIds == null || reservationIds.isEmpty()) {
      return Map.of();
    }
    return getLatestExecutionIntentSummaryUseCase
        .executeBatch(
            new GetLatestExecutionIntentSummariesQuery(
                ExecutionResourceTypeCode.ORDER,
                reservationIds.stream().map(String::valueOf).toList()))
        .entrySet()
        .stream()
        .collect(
            Collectors.toMap(
                entry -> Long.valueOf(entry.getKey()), entry -> toView(entry.getValue())));
  }

  private ReservationExecutionResumeView toView(GetLatestExecutionIntentSummaryResult summary) {
    return new ReservationExecutionResumeView(
        new ReservationExecutionResumeView.Resource(
            summary.resourceType().name(), summary.resourceId(), summary.resourceStatus().name()),
        summary.actionType().name(),
        new ReservationExecutionResumeView.ExecutionIntent(
            summary.executionIntentId(),
            summary.executionIntentStatus().name(),
            summary.expiresAt(),
            summary.expiresAtEpochSeconds()),
        new ReservationExecutionResumeView.Execution(summary.mode().name(), summary.signCount()),
        summary.transactionId() == null
            ? null
            : new ReservationExecutionResumeView.Transaction(
                summary.transactionId(),
                summary.transactionStatus() == null ? null : summary.transactionStatus().name(),
                summary.txHash()));
  }
}
