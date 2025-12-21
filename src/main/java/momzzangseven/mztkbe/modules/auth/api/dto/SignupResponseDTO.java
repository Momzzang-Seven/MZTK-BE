package momzzangseven.mztkbe.modules.auth.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Builder;
import momzzangseven.mztkbe.modules.auth.application.dto.SignupResult;

@Getter
@Builder
public class SignupResponseDTO {
    /**
     * Newly created user's unique identifier.
     */
    private Long userId;

    /**
     * Convert from Application layer DTO (SignupResult) to API layer DTO.
     *
     * This method maintains layer separation:
     * - Application Layer uses SignupResult
     * - API Layer uses SignupResponseDTO
     *
     * @param result SignupResult from application layer
     * @return SignupResponseDTO for API response
     */
    public static SignupResponseDTO from(SignupResult result) {
        return SignupResponseDTO.builder()
                .userId(result.userId())
                .build();
    }
}
