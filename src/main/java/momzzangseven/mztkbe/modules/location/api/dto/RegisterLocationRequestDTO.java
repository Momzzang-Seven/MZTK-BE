package momzzangseven.mztkbe.modules.location.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Register Location Request DTO It must contain either address info or GPS coordinate info
 *
 * @param locationName
 * @param postalCode
 * @param address
 * @param detailAddress
 * @param latitude
 * @param longitude
 */
public record RegisterLocationRequestDTO(
    @NotBlank(message = "Location name is required")
        @Size(max = 100, message = "Location name cannot exceed 100 chars")
        String locationName,
    @Size(max = 10, message = "postalCode cannot exceed 10 chars")
        String postalCode, // conditional nullable
    @Size(max = 255, message = "Address cannot exceed 255 chars")
        String address, // conditional nullable
    @Size(max = 255, message = "Detailed address cannot exceed 255 chars")
        String detailAddress, // nullable
    Double latitude, // conditional nullable
    Double longitude // conditional nullable
    ) {}
