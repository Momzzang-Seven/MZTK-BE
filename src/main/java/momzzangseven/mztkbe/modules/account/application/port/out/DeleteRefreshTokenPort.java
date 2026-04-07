package momzzangseven.mztkbe.modules.account.application.port.out;

import java.util.List;

/** Output port for deleting refresh tokens. */
public interface DeleteRefreshTokenPort {

  /** Delete all refresh tokens for a user. */
  void deleteByUserId(Long userId);

  /** Delete a refresh token by its ID. */
  void deleteById(Long refreshTokenId);

  /** Delete all refresh tokens for users in the list. */
  void deleteByUserIdIn(List<Long> userIds);
}
