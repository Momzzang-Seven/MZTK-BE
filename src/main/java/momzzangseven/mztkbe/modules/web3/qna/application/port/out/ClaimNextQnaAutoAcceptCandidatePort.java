package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ClaimNextQnaAutoAcceptCandidatePort {

  Optional<QnaAutoAcceptCandidate> claimNextCandidate(LocalDateTime cutoff);
}
