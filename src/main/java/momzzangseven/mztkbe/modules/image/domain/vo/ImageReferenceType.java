package momzzangseven.mztkbe.modules.image.domain.vo;

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
public enum ImageReferenceType {
  COMMUNITY_FREE,
  COMMUNITY_QUESTION,
  COMMUNITY_ANSWER,

  /**
   * Request-facing type for marketplace images. Expanded internally into MARKET_THUMB +
   * MARKET_DETAIL. Has no tmpPathPrefix of its own.
   */
  MARKET,

  /** Internal type: thumbnail variant of the first marketplace image. */
  MARKET_THUMB,

  /** Internal type: detail-view variant of marketplace images. */
  MARKET_DETAIL,

  /**
   * Request-facing type for marketplace store images. Expanded internally into MARKET_STORE_THUMB +
   * MARKET_STORE_DETAIL. Has no tmpPathPrefix of its own.
   */
  MARKET_STORE,
  MARKET_STORE_THUMB,
  MARKET_STORE_DETAIL,

  WORKOUT;

  /**
   * Returns true if this reference type can be sent directly by the client. MARKET_THUMB and
   * MARKET_DETAIL, MARKET_STORE_THUMB, MARKET_STORE_DETAIL are internal-only types managed by the
   * server.
   */
  public boolean isRequestFacing() {
    return this != MARKET_THUMB
        && this != MARKET_DETAIL
        && this != MARKET_STORE_THUMB
        && this != MARKET_STORE_DETAIL;
  }
}
