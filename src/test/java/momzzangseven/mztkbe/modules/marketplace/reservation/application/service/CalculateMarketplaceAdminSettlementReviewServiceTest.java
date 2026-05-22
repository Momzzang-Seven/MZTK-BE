package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CalculateMarketplaceAdminSettlementReviewQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionAuthorityView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalculateMarketplaceAdminSettlementReviewServiceTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private LoadReservationEscrowPort loadReservationEscrowPort;
  @Mock private LoadReservationActionStatePort loadReservationActionStatePort;

  private CalculateMarketplaceAdminSettlementReviewService service;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-05-21T12:00:00Z"), ZoneOffset.UTC);
    service =
        new CalculateMarketplaceAdminSettlementReviewService(
            loadReservationPort, loadReservationEscrowPort, loadReservationActionStatePort, clock);
  }

  @Test
  @DisplayName("settlement review separates timeout and manual elevated authority options")
  void settlementReviewReturnsReasonOptions() {
    given(loadReservationPort.findById(1L)).willReturn(Optional.of(approvedEndedMoreThan24hAgo()));
    given(loadReservationEscrowPort.findByReservationId(1L)).willReturn(Optional.empty());
    given(loadReservationActionStatePort.findLatestByReservationId(1L))
        .willReturn(Optional.empty());

    var result = service.execute(new CalculateMarketplaceAdminSettlementReviewQuery(1L, false));

    assertThat(result.processable()).isTrue();
    assertThat(result.pollingEndpoint()).endsWith("/settlement-review");
    assertThat(result.reasonOptions()).hasSize(2);
    assertThat(result.reasonOptions().get(0).reasonCode()).isEqualTo("BUYER_CONFIRMATION_TIMEOUT");
    assertThat(result.reasonOptions().get(0).processable()).isTrue();
    assertThat(result.reasonOptions().get(1).reasonCode()).isEqualTo("ADMIN_MANUAL_SETTLE");
    assertThat(result.reasonOptions().get(1).blockingCode())
        .isEqualTo(MarketplaceAdminReviewValidationCode.ELEVATED_AUTHORITY_REQUIRED);
    assertThat(result.reasonOptions().get(1).authoritySatisfied()).isFalse();
  }

  @Test
  @DisplayName("settlement review carries elevated operator authority into authority summary")
  void settlementReviewCarriesOperatorAuthority() {
    given(loadReservationPort.findById(1L)).willReturn(Optional.of(approvedEndedMoreThan24hAgo()));
    given(loadReservationEscrowPort.findByReservationId(1L)).willReturn(Optional.empty());
    given(loadReservationActionStatePort.findLatestByReservationId(1L))
        .willReturn(Optional.empty());

    var result = service.execute(new CalculateMarketplaceAdminSettlementReviewQuery(1L, true));

    assertThat(result.authority().authorityModel())
        .isEqualTo(MarketplaceAdminExecutionAuthorityView.SERVER_RELAYER_ONLY);
    assertThat(result.authority().canEarlySettle()).isTrue();
    assertThat(result.reasonOptions().get(1).blockingCode())
        .isEqualTo(MarketplaceAdminReviewValidationCode.EARLY_SETTLE_CONFIRMATION_REQUIRED);
  }

  private Reservation approvedEndedMoreThan24hAgo() {
    return Reservation.builder()
        .id(1L)
        .userId(10L)
        .trainerId(20L)
        .slotId(30L)
        .reservationDate(LocalDate.of(2026, 5, 19))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.APPROVED)
        .escrowStatus(ReservationEscrowStatus.LOCKED)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .priceBaseUnits("1000000000000000000")
        .version(8L)
        .build();
  }
}
