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

  /**
   * Validate is the filename has appropriate file extension
   *
   * @param filename
   * @return false when the file name is null, OR it doesn't contain '.', OR '.' comes first of file
   *     name(hidden file).
   */
  public static boolean isAllowed(String filename) {
    if (filename == null || !filename.contains(".") || filename.lastIndexOf('.') == 0) return false;
    String ext = extractExtension(filename);
    return ALLOWED.contains(ext);
  }

  /** Extracts the lowercase extension from a filename. */
  public static String extractExtension(String filename) {
    return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
  }
}
