package momzzangseven.mztkbe.modules.image.domain.vo;

import java.util.List;

/**
 * Represents the type of entity that an image belongs to.
 *
 * <p>Request-facing types (sent by the client): COMMUNITY_FREE, COMMUNITY_QUESTION,
 * COMMUNITY_ANSWER, MARKET_CLASS, MARKET_STORE, WORKOUT.
 *
 * <p>Internal-only (concrete) types stored in DB: MARKET_CLASS_THUMB, MARKET_CLASS_DETAIL,
 * MARKET_STORE_THUMB, MARKET_STORE_DETAIL. When a client sends MARKET_CLASS or MARKET_STORE, the
 * presigned-URL service expands them into their concrete subtypes internally.
 *
 * <p>This class does not have finalPathPrefix. Lambda notifies the backend of the final path, so
 * Spring does not need to assemble it.
 */
public enum ImageReferenceType {

  /** USER */
  USER_PROFILE,

  /** COMMUNITY */
  COMMUNITY_FREE,
  COMMUNITY_QUESTION,
  COMMUNITY_ANSWER,

  /** MARKET */
  /**
   * Request-facing type for marketplace images. Expanded internally into MARKET_THUMB +
   * MARKET_DETAIL. Has no tmpPathPrefix of its own.
   */
  MARKET_CLASS,
  MARKET_CLASS_THUMB,
  MARKET_CLASS_DETAIL,

  /**
   * Request-facing type for marketplace store images. Expanded internally into MARKET_STORE_THUMB +
   * MARKET_STORE_DETAIL. Has no tmpPathPrefix of its own.
   */
  MARKET_STORE,
  MARKET_STORE_THUMB,
  MARKET_STORE_DETAIL,

  /** WORKOUT */
  WORKOUT;

  /**
   * Returns true if this reference type can be sent directly by the client. MARKET_CLASS_THUMB,
   * MARKET_CLASS_DETAIL, MARKET_STORE_THUMB, MARKET_STORE_DETAIL are internal-only types managed by
   * the server.
   */
  public boolean isRequestFacing() {
    return this != MARKET_CLASS_THUMB
        && this != MARKET_CLASS_DETAIL
        && this != MARKET_STORE_THUMB
        && this != MARKET_STORE_DETAIL;
  }

  /**
   * Returns {@code true} if this type is a virtual (request-facing aggregate) that has no direct DB
   * representation and must be expanded via {@link #expand()} before any persistence operation.
   *
   * <p>Currently {@code MARKET_CLASS} and {@code MARKET_STORE} are virtual; all other types
   * (including {@code COMMUNITY_*}) map 1:1 to stored rows and are not virtual.
   */
  public boolean isVirtual() {
    return this == MARKET_CLASS || this == MARKET_STORE;
  }

  /**
   * Expands a request-facing virtual type into its concrete DB-stored subtypes.
   *
   * <ul>
   *   <li>{@code MARKET_CLASS} → {@code [MARKET_CLASS_THUMB, MARKET_CLASS_DETAIL]}
   *   <li>{@code MARKET_STORE} → {@code [MARKET_STORE_THUMB, MARKET_STORE_DETAIL]}
   *   <li>All other types → {@code [this]} (identity)
   * </ul>
   *
   * <p>Use this when querying or mutating images by reference so that THUMB and DETAIL records are
   * always included without the caller having to know the internal representation.
   */
  public List<ImageReferenceType> expand() {
    return switch (this) {
      case MARKET_CLASS -> List.of(MARKET_CLASS_THUMB, MARKET_CLASS_DETAIL);
      case MARKET_STORE -> List.of(MARKET_STORE_THUMB, MARKET_STORE_DETAIL);
      default -> List.of(this);
    };
  }
}
