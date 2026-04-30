package momzzangseven.mztkbe.modules.marketplace.store.application.dto;

/**
 * Command for retrieving a trainer's store.
 *
 * @param trainerId trainer's user ID
 */
public record GetStoreCommand(Long trainerId) {}
