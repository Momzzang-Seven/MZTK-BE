package momzzangseven.mztkbe.modules.marketplace.classes.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * Application-layer DTO representing a single reservation summary for list/detail views.
 *
 * @param reservationId reservation primary key
 * @param classId the class that was reserved
 * @param classTitle class title (denormalised for display)
 * @param status current reservation status
 * @param priceAmount class price (denormalised for display)
 * @param userRequest optional user note
 * @param reservationDate the scheduled session date
 * @param reservationTime the scheduled session start time
 */
public record ReservationItem(
    Long reservationId,
    Long classId,
    String classTitle,
    ReservationStatus status,
    int priceAmount,
    String userRequest,
    LocalDate reservationDate,
    LocalTime reservationTime) {}
