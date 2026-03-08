package momzzangseven.mztkbe.modules.verification.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.verification.domain.vo.SignalType;

/** Audit signal emitted during verification analysis. */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class VerificationSignal {
  private Long id;
  private Long verificationRequestId;
  private SignalType signalType;
  private String signalKey;
  private String signalValue;
  private BigDecimal confidence;
  private LocalDateTime createdAt;
}
