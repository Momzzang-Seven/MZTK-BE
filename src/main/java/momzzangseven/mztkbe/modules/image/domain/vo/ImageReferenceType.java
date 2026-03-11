package momzzangseven.mztkbe.modules.image.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents the type of entity that an image belongs to. Each value encapsulates the S3 path
 * prefix for temporary uploads. This class does not have finalPathPrefix. Lambda will let the
 * Spring know the final path, so Spring doesn't need to assemble the final path according to the
 * Reference Type.
 */
@Getter
@RequiredArgsConstructor
public enum ImageReferenceType {
  COMMUNITY_FREE("public/community/free/tmp/"),
  COMMUNITY_QUESTION("public/community/question/tmp/"),
  COMMUNITY_ANSWER("public/community/answer/tmp/"),
  MARKET_THUMB("public/market/thumb/tmp/"),
  MARKET_DETAIL("public/market/detail/tmp/"),
  WORKOUT("private/workout/");

  private final String tmpPathPrefix;

  /** Constructs the full S3 object key for a temporary upload. */
  public String buildTmpObjectKey(String uuid, String extension) {
    return tmpPathPrefix + uuid + "." + extension;
  }
}
