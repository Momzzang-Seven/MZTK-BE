package momzzangseven.mztkbe.modules.level.application.port.in;

import java.time.LocalDateTime;

/** Purges old level data from live tables based on retention. */
public interface PurgeLevelDataUseCase {
  int execute(LocalDateTime now);
}
