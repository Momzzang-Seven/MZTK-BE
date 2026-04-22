package momzzangseven.mztkbe.modules.marketplace.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Result carrying 4-week availability information for a single class.
 *
 * @param classId the class ID
 * @param classTitle class title
 * @param trainerId trainer's user ID
 * @param priceAmount class price
 * @param durationMinutes session duration in minutes
 * @param availableDates list of dates with available time slots
 */
public record GetClassReservationInfoResult(
    Long classId,
    String classTitle,
    Long trainerId,
    int priceAmount,
    int durationMinutes,
    List<AvailableDateInfo> availableDates) {

  /**
   * Represents a single date with its available time slots.
   *
   * @param date the session date
   * @param availableTimes list of available slot times on this date
   */
  public record AvailableDateInfo(LocalDate date, List<AvailableTimeInfo> availableTimes) {}

  /**
   * Represents a single time slot entry on a given date.
   *
   * @param slotId slot primary key
   * @param startTime session start time
   * @param capacity total slot capacity
   * @param availableCapacity remaining capacity (capacity - active reservations)
   */
  public record AvailableTimeInfo(
      Long slotId, LocalTime startTime, int capacity, int availableCapacity) {}
}
