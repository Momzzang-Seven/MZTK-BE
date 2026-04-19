package momzzangseven.mztkbe.modules.web3.qna.application.port.in;

import java.time.Instant;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.ScheduleNextQnaAutoAcceptResult;

public interface ScheduleNextQnaAutoAcceptUseCase {

  ScheduleNextQnaAutoAcceptResult scheduleNext(Instant now);
}
