package momzzangseven.mztkbe.modules.web3.transfer.application.resolver;

import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.ResolvedReward;

public interface DomainRewardResolver {
  boolean supports(DomainReferenceType type);

  ResolvedReward resolve(Long requesterId, String referenceId);

  /**
   * Fallback resolvers are only used when no concrete domain resolver is available.
   */
  default boolean isFallback() {
    return false;
  }
}
