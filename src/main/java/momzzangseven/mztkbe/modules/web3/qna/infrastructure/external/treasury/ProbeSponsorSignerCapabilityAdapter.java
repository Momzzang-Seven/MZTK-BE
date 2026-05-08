package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.treasury;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.ProbeSponsorSignerCapabilityPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ExecutionSignerCapabilityView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ProbeTreasuryWalletCapabilityUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import org.springframework.stereotype.Component;

/**
 * Bridging adapter: implements the QnA-side {@link ProbeSponsorSignerCapabilityPort} by delegating
 * to the treasury module's {@link ProbeTreasuryWalletCapabilityUseCase} with the canonical {@code
 * SPONSOR} alias.
 *
 * <p>The {@link TreasuryRole} import is intentionally confined to this bridging adapter — that is
 * what keeps QnA-layer callers (health indicator, configuration validator, draft builder, review
 * context) free of {@code treasury.domain} dependencies.
 */
@Component("qnaProbeSponsorSignerCapabilityAdapter")
@RequiredArgsConstructor
public class ProbeSponsorSignerCapabilityAdapter implements ProbeSponsorSignerCapabilityPort {

  private final ProbeTreasuryWalletCapabilityUseCase probeTreasuryWalletCapabilityUseCase;

  @Override
  public ExecutionSignerCapabilityView probe() {
    return probeTreasuryWalletCapabilityUseCase.probe(TreasuryRole.SPONSOR.toAlias());
  }
}
