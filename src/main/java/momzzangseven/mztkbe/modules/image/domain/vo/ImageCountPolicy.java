package momzzangseven.mztkbe.modules.image.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Domain policy that defines the maximum number of images allowed per {@link ImageReferenceType}.
 *
 * <p>Each policy entry is mapped to one or more reference types via {@link #of(ImageReferenceType)}
 * and exposes its limit through {@link #getMaxCount()}.
 *
 * <p>Note: For MARKET, the limit applies to the number of filenames the client submits. The server
 * internally generates n+1 image records (thumbnail + detail for the first image, detail-only for
 * the rest).
 */
@Getter
@RequiredArgsConstructor
public enum ImageCountPolicy {
  WORKOUT_POLICY(1),

  /** Marketplace images: client may submit up to 5 filenames (resulting in up to 6 DB rows). */
  MARKET_POLICY(5),

  // So far, we don't have any business rule demonstrating the max count for community, class, or
  // review. If any business rule is added, a new entry should be deployed here.
  DEFAULT_POLICY(10);

  private final int maxCount;

  /**
   * Returns the applicable count policy for the given reference type.
   *
   * @param referenceType the image reference type from the request
   * @return the matching {@link ImageCountPolicy}
   */
  public static ImageCountPolicy of(ImageReferenceType referenceType) {
    return switch (referenceType) {
      case WORKOUT -> WORKOUT_POLICY;
      case MARKET -> MARKET_POLICY;
      default -> DEFAULT_POLICY;
    };
  }
}
