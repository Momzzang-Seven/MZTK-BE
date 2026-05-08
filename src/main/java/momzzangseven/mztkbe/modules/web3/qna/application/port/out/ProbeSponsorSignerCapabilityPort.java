package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ExecutionSignerCapabilityView;

/**
 * QnA-side bridging port for the sponsor treasury wallet probe. Implemented by an adapter under
 * {@code infrastructure/external/treasury/} that delegates to the treasury module's {@code
 * ProbeTreasuryWalletCapabilityUseCase} with the {@code SPONSOR} alias bound inside the adapter;
 * QnA-layer callers never import treasury domain types directly.
 *
 * <p>Mirrors the {@code transaction.VerifyTreasuryWalletForSignPort} sidecar pattern. The view
 * record itself lives in {@code treasury.application.dto} (the treasury module's published API) and
 * is reused as-is so QnA admin surfaces (health indicator, admin review UI) keep the same {@code
 * slotStatus}/{@code failureReason} enum diagnostics they expose today.
 */
public interface ProbeSponsorSignerCapabilityPort {

  /**
   * Probe the sponsor treasury wallet's current signing readiness.
   *
   * @return diagnostic view of the sponsor signer (never throws — failures land as {@code
   *     failureReason})
   */
  ExecutionSignerCapabilityView probe();
}
