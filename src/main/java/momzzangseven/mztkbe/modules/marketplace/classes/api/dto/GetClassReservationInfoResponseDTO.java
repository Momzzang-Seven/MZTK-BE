package momzzangseven.mztkbe.modules.marketplace.classes.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassReservationInfoResult;

public record GetClassReservationInfoResponseDTO(
    Long classId,
    String classTitle,
    Long trainerId,
    int priceAmount,
    int durationMinutes,
    List<AvailableDateResponseDTO> availableDates) {

  public static GetClassReservationInfoResponseDTO from(GetClassReservationInfoResult result) {
    return new GetClassReservationInfoResponseDTO(
        result.classId(),
        result.classTitle(),
        result.trainerId(),
        result.priceAmount(),
        result.durationMinutes(),
        result.availableDates().stream()
            .map(
                d ->
                    new AvailableDateResponseDTO(
                        d.date(),
                        d.availableTimes().stream()
                            .map(
                                t ->
                                    new AvailableTimeResponseDTO(
                                        t.slotId(),
                                        t.startTime(),
                                        t.capacity(),
                                        t.availableCapacity()))
                            .toList()))
            .toList());
  }

  public record AvailableDateResponseDTO(
      LocalDate date, List<AvailableTimeResponseDTO> availableTimes) {}

  public record AvailableTimeResponseDTO(
      Long slotId, LocalTime startTime, int capacity, int availableCapacity) {}
}
