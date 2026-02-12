package momzzangseven.mztkbe.global.security;

import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import org.springframework.stereotype.Component;

/** Shared guard for admin APIs that require authenticated operator identity. */
@Component
public class AdminOperatorGuard {

  public Long requireOperatorId(Long operatorId) {
    if (operatorId == null || operatorId <= 0) {
      throw new UserNotAuthenticatedException();
    }
    return operatorId;
  }
}
