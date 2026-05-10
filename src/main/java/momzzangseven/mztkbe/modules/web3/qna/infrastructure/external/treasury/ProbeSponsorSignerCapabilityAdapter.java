package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.treasury;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminServerSignerFailureReason;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminServerSignerSlotStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminServerSignerView;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.ProbeSponsorSignerCapabilityPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ExecutionSignerCapabilityView;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ExecutionSignerFailureReason;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ExecutionSignerSlotStatus;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ProbeTreasuryWalletCapabilityUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import org.springframework.stereotype.Component;

/**
 * Bridging adapter: implements the QnA-side {@link ProbeSponsorSignerCapabilityPort} by delegating
 * to the treasury module's {@link ProbeTreasuryWalletCapabilityUseCase} with the canonical {@code
 * SPONSOR} alias and translating the treasury-module diagnostic view onto the QnA-local {@link
 * QnaAdminServerSignerView}.
 *
 * <p>This is the only file in the QnA module that is allowed to import {@code treasury.*} —
 * confining {@link TreasuryRole}, {@link ExecutionSignerCapabilityView}, and the treasury enum pair
 * here is what keeps QnA application-layer callers (health indicator, configuration validator,
 * draft builder, review context, decider) free of any {@code treasury.*} dependency.
 *
 * <p>Enum mapping uses {@code Enum.valueOf(target, source.name())} on the assumption that the QnA
 * mirror enums declare the same constants as their treasury counterparts. If a treasury constant is
 * added without a matching QnA mirror, that call fails with {@code IllegalArgumentException} at
 * runtime, surfacing the drift immediately rather than silently dropping the case.
 */
@Component("qnaProbeSponsorSignerCapabilityAdapter")
@RequiredArgsConstructor
public class ProbeSponsorSignerCapabilityAdapter implements ProbeSponsorSignerCapabilityPort {

  private final ProbeTreasuryWalletCapabilityUseCase probeTreasuryWalletCapabilityUseCase;

  @Override
  public QnaAdminServerSignerView probe() {
    ExecutionSignerCapabilityView source =
        probeTreasuryWalletCapabilityUseCase.probe(TreasuryRole.SPONSOR.toAlias());
    return new QnaAdminServerSignerView(
        source.walletAlias(),
        mapSlotStatus(source.slotStatus()),
        mapFailureReason(source.failureReason()),
        source.signerAddress(),
        source.signable());
  }

  private static QnaAdminServerSignerSlotStatus mapSlotStatus(ExecutionSignerSlotStatus source) {
    return QnaAdminServerSignerSlotStatus.valueOf(source.name());
  }

  private static QnaAdminServerSignerFailureReason mapFailureReason(
      ExecutionSignerFailureReason source) {
    return QnaAdminServerSignerFailureReason.valueOf(source.name());
  }
}
