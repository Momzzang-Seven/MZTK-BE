package momzzangseven.mztkbe.modules.image.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents the type of entity that an image belongs to.
 *
 * <p>Request-facing types (sent by the client): COMMUNITY_FREE, COMMUNITY_QUESTION,
 * COMMUNITY_ANSWER, MARKET, WORKOUT.
 *
 * <p>Internal-only types (used for DB storage, never sent by client): MARKET_THUMB, MARKET_DETAIL.
 * When a client sends MARKET, the service expands it into MARKET_THUMB (for the first image's
 * thumbnail) and MARKET_DETAIL (for all detail images) internally.
 *
 * <p>This class does not have finalPathPrefix. Lambda notifies the backend of the final path, so
 * Spring does not need to assemble it.
 */
@Getter
@RequiredArgsConstructor
public enum ImageReferenceType {
  COMMUNITY_FREE("public/community/free/tmp/"),
  COMMUNITY_QUESTION("public/community/question/tmp/"),
  COMMUNITY_ANSWER("public/community/answer/tmp/"),

  /**
   * Request-facing type for marketplace images. Expanded internally into MARKET_THUMB +
   * MARKET_DETAIL. Has no tmpPathPrefix of its own.
   */
  MARKET(null),

  /** Internal type: thumbnail variant of the first marketplace image. */
  MARKET_THUMB("public/market/thumb/tmp/"),

  /** Internal type: detail-view variant of marketplace images. */
  MARKET_DETAIL("public/market/detail/tmp/"),

  WORKOUT("private/workout/");

  private final String tmpPathPrefix;

  /**
   * Returns true if this reference type can be sent directly by the client. MARKET_THUMB and
   * MARKET_DETAIL are internal-only types managed by the server.
   */
  public boolean isRequestFacing() {
    return this != MARKET_THUMB && this != MARKET_DETAIL;
  }

  /**
   * Constructs the full S3 object key for a temporary upload.
   *
   * @throws IllegalStateException if called on a virtual type (e.g. MARKET) that has no path prefix
   */
  public String buildTmpObjectKey(String uuid, String extension) {
    if (tmpPathPrefix == null) {
      throw new IllegalStateException(
          this + " is a virtual reference type and cannot build object keys directly.");
    }
    return tmpPathPrefix + uuid + "." + extension;
  }
}
