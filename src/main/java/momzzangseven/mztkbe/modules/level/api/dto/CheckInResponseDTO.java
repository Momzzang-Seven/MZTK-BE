package momzzangseven.mztkbe.modules.level.api.dto;

import java.time.LocalDate;
import momzzangseven.mztkbe.modules.level.application.dto.CheckInResult;

public record CheckInResponseDTO(
        boolean success,
        LocalDate attendedDate,
        int grantedXp,
        int bonusXp,
        int streakDays,
        String message
) {
    public static CheckInResponseDTO from(CheckInResult result) {
        return new CheckInResponseDTO(
                result.success(),
                result.attendedDate(),
                result.grantedXp(),
                result.bonusXp(),
                result.streakDays(),
                result.message()
        );
    }
}
