package momzzangseven.mztkbe.global.pagination;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class CursorScope {

  private CursorScope() {}

  public static String posts(String type, String tag, String search) {
    return hash(
        "posts|type="
            + normalize(type)
            + "|tag="
            + normalize(tag)
            + "|search="
            + normalize(search)
            + "|sort=POST_CREATED_DESC");
  }

  public static String rootComments(Long postId) {
    return hash("root-comments|postId=" + postId + "|sort=COMMENT_CREATED_ASC");
  }

  public static String replies(Long parentCommentId) {
    return hash("replies|parentCommentId=" + parentCommentId + "|sort=COMMENT_CREATED_ASC");
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.trim().toLowerCase();
  }

  private static String hash(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        builder.append(String.format("%02x", b));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
