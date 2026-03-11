package momzzangseven.mztkbe.modules.image.domain.vo;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Whitelist of image file extensions accepted for upload. Rejects unsupported formats (e.g. .webp)
 * before they reach S3/Lambda.
 */
public enum AllowedImageExtension {
  JPG,
  JPEG,
  PNG,
  GIF,
  HEIF,
  HEIC;

  private static final Set<String> ALLOWED =
      Arrays.stream(values())
          .map(e -> e.name().toLowerCase())
          .collect(Collectors.toUnmodifiableSet());

  /** Returns true if the given filename has an allowed extension. */
  public static boolean isAllowed(String filename) {
    if (filename == null || !filename.contains(".")) return false;
    String ext = extractExtension(filename);
    return ALLOWED.contains(ext);
  }

  /** Extracts the lowercase extension from a filename. */
  public static String extractExtension(String filename) {
    return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
  }
}
