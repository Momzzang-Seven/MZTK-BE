package momzzangseven.mztkbe.modules.web3.qna.application.port.in;

import java.time.Instant;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.RunQnaAutoAcceptBatchResult;

public interface RunQnaAutoAcceptBatchUseCase {

  RunQnaAutoAcceptBatchResult runBatch(Instant now);
}
