package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CalculateMarketplaceAdminSettlementReviewQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminEscrowReviewResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReasonReviewOption;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminResultPreview;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationItem;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminSettleReasonCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CalculateMarketplaceAdminSettlementReviewUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadMarketplaceAdminExecutionAuthorityPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCandidatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationTerminalResolvedBy;

public class CalculateMarketplaceAdminSettlementReviewService
    implements CalculateMarketplaceAdminSettlementReviewUseCase {

  private static final String POLLING_ENDPOINT_SUFFIX = "/settlement-review";

  private final LoadReservationPort loadReservationPort;
  private final LoadReservationEscrowPort loadReservationEscrowPort;
  private final LoadReservationActionStatePort loadReservationActionStatePort;
  private final LoadReservationExecutionStatePort loadReservationExecutionStatePort;
  private final LoadReservationExecutionCandidatePort loadReservationExecutionCandidatePort;
  private final LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;
  private final LoadMarketplaceAdminExecutionAuthorityPort
      loadMarketplaceAdminExecutionAuthorityPort;
  private final Clock clock;

  public CalculateMarketplaceAdminSettlementReviewService(
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

  public CalculateMarketplaceAdminSettlementReviewService(
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
        null,
        clock);
  }

  public CalculateMarketplaceAdminSettlementReviewService(
      LoadReservationPort loadReservationPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      LoadReservationEscrowOrderPort loadReservationEscrowOrderPort,
      LoadMarketplaceAdminExecutionAuthorityPort loadMarketplaceAdminExecutionAuthorityPort,
      Clock clock) {
    this(
        loadReservationPort,
        loadReservationEscrowPort,
        loadReservationActionStatePort,
        loadReservationExecutionStatePort,
        null,
        loadReservationEscrowOrderPort,
        loadMarketplaceAdminExecutionAuthorityPort,
        clock);
  }

  public CalculateMarketplaceAdminSettlementReviewService(
      LoadReservationPort loadReservationPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      LoadReservationExecutionCandidatePort loadReservationExecutionCandidatePort,
      LoadReservationEscrowOrderPort loadReservationEscrowOrderPort,
      LoadMarketplaceAdminExecutionAuthorityPort loadMarketplaceAdminExecutionAuthorityPort,
      Clock clock) {
    this.loadReservationPort = loadReservationPort;
    this.loadReservationEscrowPort = loadReservationEscrowPort;
    this.loadReservationActionStatePort = loadReservationActionStatePort;
    this.loadReservationExecutionStatePort = loadReservationExecutionStatePort;
    this.loadReservationExecutionCandidatePort = loadReservationExecutionCandidatePort;
    this.loadReservationEscrowOrderPort = loadReservationEscrowOrderPort;
    this.loadMarketplaceAdminExecutionAuthorityPort = loadMarketplaceAdminExecutionAuthorityPort;
    this.clock = clock;
  }

  @Override
  public MarketplaceAdminEscrowReviewResult execute(
      CalculateMarketplaceAdminSettlementReviewQuery query) {
    query.validate();
    MarketplaceAdminReviewSupport.Context context =
        MarketplaceAdminReviewSupport.load(
            query.reservationId(),
            loadReservationPort,
            loadReservationEscrowPort,
            loadReservationActionStatePort);
    List<MarketplaceAdminReviewValidationItem> baseItems =
        new ArrayList<>(
            MarketplaceAdminReviewSupport.baseItems(context, ReservationStatus.APPROVED));
    List<MarketplaceAdminReviewValidationItem> unresolvedExecutionItems =
        MarketplaceAdminReviewSupport.unresolvedExecutionItems(
            context, loadReservationExecutionStatePort, loadReservationExecutionCandidatePort);
    if (!unresolvedExecutionItems.isEmpty()) {
      baseItems.removeIf(item -> item.code() == MarketplaceAdminReviewValidationCode.OK);
      baseItems.addAll(unresolvedExecutionItems);
    }
    MarketplaceAdminReviewSupport.PreflightResult preflight =
        MarketplaceAdminReviewSupport.preflight(
            context,
            clock,
            loadReservationEscrowOrderPort,
            loadMarketplaceAdminExecutionAuthorityPort);
    preflight = preflight.withOperatorAuthority(query.canEarlySettle(), false);
    baseItems.addAll(preflight.validationItems());
    MarketplaceAdminReviewValidationCode baseBlocking =
        MarketplaceAdminReviewSupport.firstBlockingCode(baseItems);
    return MarketplaceAdminReviewSupport.result(
        context,
        clock,
        "/admin/web3/marketplace/reservations/" + query.reservationId() + POLLING_ENDPOINT_SUFFIX,
        reasonOptions(context.reservation(), query.canEarlySettle(), baseBlocking),
        List.copyOf(baseItems),
        preflight,
        loadReservationExecutionStatePort);
  }

  private List<MarketplaceAdminReasonReviewOption> reasonOptions(
      Reservation reservation,
      boolean canEarlySettle,
      MarketplaceAdminReviewValidationCode baseBlocking) {
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDateTime sessionEndAt = reservation.sessionEndAt();
    List<MarketplaceAdminReasonReviewOption> options = new ArrayList<>();
    boolean cutoffReached = !sessionEndAt.plusHours(24).isAfter(now);
    options.add(
        option(
            MarketplaceAdminSettleReasonCode.BUYER_CONFIRMATION_TIMEOUT.name(),
            baseBlocking,
            cutoffReached ? null : MarketplaceAdminReviewValidationCode.CLASS_NOT_ENDED,
            false,
            null,
            "ROLE_ADMIN",
            true));

    MarketplaceAdminReviewValidationCode manualBlocking = null;
    if (sessionEndAt.isAfter(now)) {
      manualBlocking = MarketplaceAdminReviewValidationCode.CLASS_NOT_ENDED;
    } else if (!canEarlySettle) {
      manualBlocking = MarketplaceAdminReviewValidationCode.ELEVATED_AUTHORITY_REQUIRED;
    } else {
      manualBlocking = MarketplaceAdminReviewValidationCode.EARLY_SETTLE_CONFIRMATION_REQUIRED;
    }
    options.add(
        option(
            MarketplaceAdminSettleReasonCode.ADMIN_MANUAL_SETTLE.name(),
            baseBlocking,
            manualBlocking,
            true,
            "EARLY_SETTLE",
            "ROLE_ADMIN_SEED",
            canEarlySettle));
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
        confirmationOnly ? null : blocking,
        requiresConfirmation,
        confirmationType,
        requiredAuthority,
        authoritySatisfied,
        reasonCode,
        new MarketplaceAdminResultPreview(
            ReservationStatus.AUTO_SETTLED,
            ReservationEscrowStatus.SETTLED,
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
    return code == MarketplaceAdminReviewValidationCode.EARLY_SETTLE_CONFIRMATION_REQUIRED;
  }
}
