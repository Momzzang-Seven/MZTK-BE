package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;

/** Minimal shared execution intent view needed for marketplace cleanup protection. */
public record MarketplaceExecutionCleanupIntent(
    Long id,
    String publicId,
    String resourceId,
    MarketplaceExecutionActionType actionType,
    Long requesterUserId) {}
