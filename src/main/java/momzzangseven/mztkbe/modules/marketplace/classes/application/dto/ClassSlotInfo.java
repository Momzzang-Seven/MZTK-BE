package momzzangseven.mztkbe.modules.marketplace.classes.application.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * DTO carrying slot information in class detail and trainer-class responses.
 *
 * <p>{@code timeId} is the persistent identifier of the slot row (used for update routing).
 */
public record ClassSlotInfo(
    Long timeId, List<DayOfWeek> daysOfWeek, LocalTime startTime, int capacity) {}
