package momzzangseven.mztkbe.modules.image.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Domain policy that defines the maximum number of images allowed per {@link ImageReferenceType}.
 *
 * <p>Each policy entry is mapped to one or more reference types via {@link #of(ImageReferenceType)}
 * and exposes its limit through {@link #getMaxCount()}.
 */
@Getter
@RequiredArgsConstructor
public enum ImageCountPolicy {
  WORKOUT_POLICY(1),

  // So far, we don't have any business rule demonstrating the max count for community, class, or
  // review.
  // If any business rule added, additional entry could be deployed.
  DEFAULT_POLICY(10);

  private final int maxCount;

  /**
   * Returns the applicable count policy for the given reference type.
   *
   * @param referenceType the image reference type from the request
   * @return the matching {@link ImageCountPolicy}
   */
  public static ImageCountPolicy of(ImageReferenceType referenceType) {
    return referenceType == ImageReferenceType.WORKOUT ? WORKOUT_POLICY : DEFAULT_POLICY;
  }
}
