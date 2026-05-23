package momzzangseven.mztkbe.modules.marketplace.classes.application.dto;

/**
 * Class fields required by the reservation module when creating a booking snapshot.
 *
 * @param classId primary key
 * @param trainerId owning trainer ID
 * @param priceAmount current class price
 * @param durationMinutes session duration in minutes
 * @param title current class title
 * @param active whether the class is currently reservable
 */
public record ClassReservationProjection(
    Long classId,
    Long trainerId,
    int priceAmount,
    int durationMinutes,
    String title,
    boolean active) {}
