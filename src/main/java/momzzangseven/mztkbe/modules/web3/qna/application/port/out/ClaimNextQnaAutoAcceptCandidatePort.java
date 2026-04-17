package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAutoAcceptCandidate;

public interface ClaimNextQnaAutoAcceptCandidatePort {

  Optional<QnaAutoAcceptCandidate> claimNextCandidate(LocalDateTime cutoff);
}
