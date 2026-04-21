package momzzangseven.mztkbe.modules.marketplace.application.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * Command object carrying the time-slot data for class registration and update.
 *
 * <p>When {@code timeId} is non-null, this represents an existing slot to update. When null, a new
 * slot is created.
 */
public record ClassTimeCommand(
    Long timeId, List<DayOfWeek> daysOfWeek, LocalTime startTime, int capacity) {}
