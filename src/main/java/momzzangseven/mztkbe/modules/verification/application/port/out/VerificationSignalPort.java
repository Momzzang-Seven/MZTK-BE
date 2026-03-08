package momzzangseven.mztkbe.modules.verification.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationSignal;

/** Outbound port for persisting verification analysis signals. */
public interface VerificationSignalPort {
  VerificationSignal save(VerificationSignal signal);

  List<VerificationSignal> findByVerificationRequestId(Long verificationRequestId);
}
