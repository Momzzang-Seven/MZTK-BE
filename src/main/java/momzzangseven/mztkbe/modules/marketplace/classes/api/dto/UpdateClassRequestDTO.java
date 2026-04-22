package momzzangseven.mztkbe.modules.marketplace.classes.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassTimeCommand;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.UpdateClassCommand;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.vo.ClassCategory;

/**
 * HTTP request DTO for {@code PUT /marketplace/trainer/classes/{classId}}.
 *
 * <p>Same shape as {@link RegisterClassRequestDTO} but time entries may carry an optional {@code
 * timeId} for routing existing slot updates.
 */
public record UpdateClassRequestDTO(
    @NotBlank @Size(max = 100) String title,
    @NotNull ClassCategory category,
    @NotBlank String description,
    @Positive int priceAmount,
    @Min(1) @Max(1440) int durationMinutes,
    @Size(max = 3) List<@NotBlank @Size(max = 30) String> tags,
    @Size(max = 10) List<@NotBlank @Size(max = 100) String> features,
    String personalItems,
    @Size(max = 6) List<Long> imageIds,
    @NotEmpty @Valid List<ClassTimeRequestDTO> classTimes) {

  /** Nested DTO for a single class time entry (timeId is optional for updates). */
  public record ClassTimeRequestDTO(
      Long timeId,
      @NotEmpty List<DayOfWeek> daysOfWeek,
      @NotNull LocalTime startTime,
      @Min(1) int capacity) {

    public ClassTimeCommand toCommand() {
      return new ClassTimeCommand(timeId, daysOfWeek, startTime, capacity);
    }
  }

  /**
   * Converts this request DTO to an application-layer command.
   *
   * @param trainerId authenticated trainer ID
   * @param classId path variable class ID
   * @return the command
   */
  public UpdateClassCommand toCommand(Long trainerId, Long classId) {
    List<ClassTimeCommand> classTimes =
        this.classTimes.stream().map(ClassTimeRequestDTO::toCommand).toList();
    return new UpdateClassCommand(
        trainerId,
        classId,
        title,
        category,
        description,
        priceAmount,
        durationMinutes,
        tags,
        features,
        personalItems,
        imageIds,
        classTimes);
  }
}
