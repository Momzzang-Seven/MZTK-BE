package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CalculateMarketplaceAdminRefundReviewQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminEscrowReviewResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReasonReviewOption;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminRefundReasonCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminResultPreview;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationItem;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CalculateMarketplaceAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadMarketplaceAdminExecutionAuthorityPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationTerminalResolvedBy;

public class CalculateMarketplaceAdminRefundReviewService
    implements CalculateMarketplaceAdminRefundReviewUseCase {

  private static final String POLLING_ENDPOINT_SUFFIX = "/refund-review";

  private final LoadReservationPort loadReservationPort;
  private final LoadReservationEscrowPort loadReservationEscrowPort;
  private final LoadReservationActionStatePort loadReservationActionStatePort;
  private final LoadReservationExecutionStatePort loadReservationExecutionStatePort;
  private final LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;
  private final LoadMarketplaceAdminExecutionAuthorityPort
      loadMarketplaceAdminExecutionAuthorityPort;
  private final Clock clock;

  public CalculateMarketplaceAdminRefundReviewService(
      LoadReservationPort loadReservationPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      Clock clock) {
    this(
        loadReservationPort,
        loadReservationEscrowPort,
        loadReservationActionStatePort,
        null,
        null,
        null,
        clock);
  }

  public CalculateMarketplaceAdminRefundReviewService(
      LoadReservationPort loadReservationPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      Clock clock) {
    this(
        loadReservationPort,
        loadReservationEscrowPort,
        loadReservationActionStatePort,
        loadReservationExecutionStatePort,
        null,
        null,
        clock);
  }

  public CalculateMarketplaceAdminRefundReviewService(
      LoadReservationPort loadReservationPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      LoadReservationEscrowOrderPort loadReservationEscrowOrderPort,
      LoadMarketplaceAdminExecutionAuthorityPort loadMarketplaceAdminExecutionAuthorityPort,
      Clock clock) {
    this.loadReservationPort = loadReservationPort;
    this.loadReservationEscrowPort = loadReservationEscrowPort;
    this.loadReservationActionStatePort = loadReservationActionStatePort;
    this.loadReservationExecutionStatePort = loadReservationExecutionStatePort;
    this.loadReservationEscrowOrderPort = loadReservationEscrowOrderPort;
    this.loadMarketplaceAdminExecutionAuthorityPort = loadMarketplaceAdminExecutionAuthorityPort;
    this.clock = clock;
  }

  @Override
  public MarketplaceAdminEscrowReviewResult execute(
      CalculateMarketplaceAdminRefundReviewQuery query) {
    query.validate();
    MarketplaceAdminReviewSupport.Context context =
        MarketplaceAdminReviewSupport.load(
            query.reservationId(),
            loadReservationPort,
            loadReservationEscrowPort,
            loadReservationActionStatePort);
    List<MarketplaceAdminReviewValidationItem> baseItems =
        new ArrayList<>(
            MarketplaceAdminReviewSupport.baseItems(context, ReservationStatus.PENDING));
    MarketplaceAdminReviewSupport.PreflightResult preflight =
        MarketplaceAdminReviewSupport.preflight(
            context,
            clock,
            loadReservationEscrowOrderPort,
            loadMarketplaceAdminExecutionAuthorityPort);
    preflight = preflight.withOperatorAuthority(false, query.canManualRefund());
    baseItems.addAll(preflight.validationItems());
    MarketplaceAdminReviewValidationCode baseBlocking =
        MarketplaceAdminReviewSupport.firstBlockingCode(baseItems);
    return MarketplaceAdminReviewSupport.result(
        context,
        clock,
        "/admin/web3/marketplace/reservations/" + query.reservationId() + POLLING_ENDPOINT_SUFFIX,
        reasonOptions(context.reservation(), query.canManualRefund(), baseBlocking),
        List.copyOf(baseItems),
        preflight,
        loadReservationExecutionStatePort);
  }

  private List<MarketplaceAdminReasonReviewOption> reasonOptions(
      Reservation reservation,
      boolean canManualRefund,
      MarketplaceAdminReviewValidationCode baseBlocking) {
    LocalDateTime now = LocalDateTime.now(clock);
    List<MarketplaceAdminReasonReviewOption> options = new ArrayList<>();
    boolean trainerTimeout =
        reservation.getCreatedAt() != null
            && !reservation.getCreatedAt().plusHours(72).isAfter(now);
    options.add(
        option(
            MarketplaceAdminRefundReasonCode.TRAINER_TIMEOUT.name(),
            baseBlocking,
            trainerTimeout
                ? null
                : MarketplaceAdminReviewValidationCode.APPROVAL_TIMEOUT_NOT_REACHED,
            false,
            null,
            "ROLE_ADMIN",
            true));

    boolean sessionWindowReached =
        !reservation
            .sessionEndAt()
            .minusMinutes(reservation.getDurationMinutes())
            .minusHours(1)
            .isAfter(now);
    options.add(
        option(
            MarketplaceAdminRefundReasonCode.SESSION_START_WINDOW_TIMEOUT.name(),
            baseBlocking,
            sessionWindowReached
                ? null
                : MarketplaceAdminReviewValidationCode.SESSION_START_WINDOW_NOT_REACHED,
            false,
            null,
            "ROLE_ADMIN",
            true));

    MarketplaceAdminReviewValidationCode manualBlocking =
        canManualRefund
            ? MarketplaceAdminReviewValidationCode.MANUAL_REFUND_CONFIRMATION_REQUIRED
            : MarketplaceAdminReviewValidationCode.ELEVATED_AUTHORITY_REQUIRED;
    options.add(
        option(
            MarketplaceAdminRefundReasonCode.ADMIN_MANUAL_REFUND.name(),
            baseBlocking,
            manualBlocking,
            true,
            "MANUAL_REFUND",
            "ROLE_ADMIN_SEED",
            canManualRefund));
    return List.copyOf(options);
  }

  private MarketplaceAdminReasonReviewOption option(
      String reasonCode,
      MarketplaceAdminReviewValidationCode baseBlocking,
      MarketplaceAdminReviewValidationCode reasonBlocking,
      boolean requiresConfirmation,
      String confirmationType,
      String requiredAuthority,
      boolean authoritySatisfied) {
    MarketplaceAdminReviewValidationCode blocking =
        baseBlocking == null ? reasonBlocking : baseBlocking;
    boolean confirmationOnly = isConfirmationRequired(blocking);
    boolean processable = blocking == null || (baseBlocking == null && confirmationOnly);
    return new MarketplaceAdminReasonReviewOption(
        reasonCode,
        processable,
        blocking,
        requiresConfirmation,
        confirmationType,
        requiredAuthority,
        authoritySatisfied,
        reasonCode,
        new MarketplaceAdminResultPreview(
            ReservationStatus.TIMEOUT_CANCELLED,
            ReservationEscrowStatus.REFUNDED,
            ReservationTerminalResolvedBy.ADMIN,
            reasonCode),
        blocking == null ? List.of() : List.of(validationItem(blocking, confirmationOnly)));
  }

  private static MarketplaceAdminReviewValidationItem validationItem(
      MarketplaceAdminReviewValidationCode code, boolean confirmationOnly) {
    return confirmationOnly
        ? MarketplaceAdminReviewValidationItem.info(code, code.name())
        : MarketplaceAdminReviewValidationItem.blocking(code, code.name());
  }

  private static boolean isConfirmationRequired(MarketplaceAdminReviewValidationCode code) {
    return code == MarketplaceAdminReviewValidationCode.MANUAL_REFUND_CONFIRMATION_REQUIRED;
  }
}
