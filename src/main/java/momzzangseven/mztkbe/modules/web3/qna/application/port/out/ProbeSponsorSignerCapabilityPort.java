package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminServerSignerView;

/**
 * QnA-side bridging port for the sponsor treasury wallet probe. Implemented by an adapter under
 * {@code infrastructure/external/treasury/} that delegates to the treasury module's {@code
 * ProbeTreasuryWalletCapabilityUseCase} with the {@code SPONSOR} alias bound inside the adapter and
 * maps the treasury view onto a QnA-local {@link QnaAdminServerSignerView}; QnA-layer callers never
 * import treasury types directly.
 *
 * <p>Mirrors the {@code transaction.VerifyTreasuryWalletForSignPort} sidecar pattern, with the
 * additional view translation keeping the treasury-module DTO out of the QnA application layer.
 */
public interface ProbeSponsorSignerCapabilityPort {

  /**
   * Probe the sponsor treasury wallet's current signing readiness.
   *
   * @return diagnostic view of the sponsor signer (never throws — failures land as {@code
   *     failureReason})
   */
  QnaAdminServerSignerView probe();
}
