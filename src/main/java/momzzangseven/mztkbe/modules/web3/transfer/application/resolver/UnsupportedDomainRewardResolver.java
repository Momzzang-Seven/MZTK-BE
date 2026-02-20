package momzzangseven.mztkbe.modules.web3.transfer.application.resolver;

import java.util.EnumSet;
import java.util.Set;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.ResolvedReward;
import org.springframework.stereotype.Component;

/**
 * Explicitly blocks domain types that are declared in API but not yet wired to source-of-truth
 * data.
 */
@Component
public class UnsupportedDomainRewardResolver implements DomainRewardResolver {

  private static final Set<DomainReferenceType> UNSUPPORTED_TYPES =
      EnumSet.of(
          DomainReferenceType.QUESTION_REWARD,
          DomainReferenceType.POST_SPONSOR,
          DomainReferenceType.ITEM_PURCHASE,
          DomainReferenceType.LEVEL_UP_REWARD);

  @Override
  public boolean supports(DomainReferenceType type) {
    return UNSUPPORTED_TYPES.contains(type);
  }

  @Override
  public boolean isFallback() {
    return true;
  }

  @Override
  public ResolvedReward resolve(Long requesterId, String referenceId) {
    throw new Web3InvalidInputException(
        "domain resolver is not available for this endpoint/domainType");
  }
}
