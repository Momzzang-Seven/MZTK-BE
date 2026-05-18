package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;

/** Minimal shared execution intent view needed for marketplace cleanup protection. */
public record MarketplaceExecutionCleanupIntent(
    Long id,
    String publicId,
    String resourceId,
    MarketplaceExecutionActionType actionType,
    Long requesterUserId,
    String payloadSnapshotJson) {

  public MarketplaceExecutionCleanupIntent(
      Long id,
      String publicId,
      String resourceId,
      MarketplaceExecutionActionType actionType,
      Long requesterUserId) {
    this(id, publicId, resourceId, actionType, requesterUserId, null);
  }
}
