package momzzangseven.mztkbe.modules.marketplace.application.dto;

/**
 * Command for creating or updating a trainer store.
 *
 * <p><b>Validation strategy:</b> This command intentionally contains NO validation logic.
 * Input format validation is handled by {@code UpsertStoreRequestDTO} (Bean Validation),
 * and business invariant validation is handled by {@code TrainerStore.create()} (Domain layer).
 * Adding validation here would create triple-redundancy with divergent error types.
 *
 * @param trainerId trainer's user ID (from authentication)
 * @param storeName store name
 * @param address store address
 * @param detailAddress detailed address
 * @param latitude latitude coordinate
 * @param longitude longitude coordinate
 * @param phoneNumber phone number
 * @param homepageUrl homepage URL
 * @param instagramUrl Instagram URL
 * @param xUrl X (Twitter) URL
 */
public record UpsertStoreCommand(
    Long trainerId,
    String storeName,
    String address,
    String detailAddress,
    Double latitude,
    Double longitude,
    String phoneNumber,
    String homepageUrl,
    String instagramUrl,
    String xUrl) {}
