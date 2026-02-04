package momzzangseven.mztkbe.modules.location.application.dto;

/**
 * Command for Location Registration Input parameter of Service
 *
 * @param userId
 * @param locationName
 * @param postalCode
 * @param address
 * @param detailAddress
 * @param latitude
 * @param longitude
 */
public record RegisterLocationCommand(
    Long userId,
    String locationName,
    String postalCode,
    String address,
    String detailAddress,
    Double latitude, // nullable
    Double longitude // nullable
    ) {}
