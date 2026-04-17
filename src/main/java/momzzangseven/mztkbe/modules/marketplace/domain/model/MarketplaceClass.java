package momzzangseven.mztkbe.modules.marketplace.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidCategoryException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidDescriptionException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidDurationException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidFeatureException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidPriceException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidTagException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidTitleException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidTrainerIdException;
import momzzangseven.mztkbe.modules.marketplace.domain.vo.ClassCategory;

/**
 * Domain model representing a marketplace PT class listed by a trainer.
 *
 * <p>All fields are {@code private final} to enforce immutability. New instances are created
 * exclusively via {@link #create} (for new classes) or via {@link #update} (for modifications).
 * Both methods enforce all domain invariants.
 *
 * <p>State-transition methods (e.g., {@link #toggleStatus}) always return a new instance.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MarketplaceClass {

  // ============================================
  // Field Length Constants
  // ============================================

  public static final int MAX_TITLE_LENGTH = 100;
  public static final int MAX_TAGS = 3;
  public static final int MAX_TAG_LENGTH = 30;
  public static final int MAX_FEATURES = 10;
  public static final int MAX_FEATURE_LENGTH = 100;
  public static final int MIN_DURATION_MINUTES = 1;
  public static final int MAX_DURATION_MINUTES = 1440;

  // ============================================
  // Fields (all private final for immutability)
  // ============================================

  private final Long id;
  private final Long trainerId;
  private final String title;
  private final ClassCategory category;
  private final String description;
  private final int priceAmount;
  private final int durationMinutes;
  private final List<String> tags;
  private final List<String> features;
  private final String personalItems;
  private final boolean active;

  /** Optimistic lock version — null for new (unsaved) instances, populated after first save. */
  private final Long version;

  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  // ============================================
  // Factory Methods
  // ============================================

  /**
   * Create a new MarketplaceClass with full field validation.
   *
   * @param trainerId trainer's user ID
   * @param title class title
   * @param category class category
   * @param description class description
   * @param priceAmount price in KRW (must be positive)
   * @param durationMinutes session duration in minutes (1-1440)
   * @param tags optional list of tags (max 3)
   * @param features optional list of features (max 10)
   * @param personalItems optional personal items note
   * @return new, active MarketplaceClass instance
   */
  public static MarketplaceClass create(
      Long trainerId,
      String title,
      ClassCategory category,
      String description,
      int priceAmount,
      int durationMinutes,
      List<String> tags,
      List<String> features,
      String personalItems) {

    validateTrainerId(trainerId);
    validateTitle(title);
    validateCategory(category);
    validateDescription(description);
    validatePrice(priceAmount);
    validateDuration(durationMinutes);
    validateTags(tags);
    validateFeatures(features);

    return MarketplaceClass.builder()
        .trainerId(trainerId)
        .title(title)
        .category(category)
        .description(description)
        .priceAmount(priceAmount)
        .durationMinutes(durationMinutes)
        .tags(tags == null ? List.of() : List.copyOf(tags))
        .features(features == null ? List.of() : List.copyOf(features))
        .personalItems(personalItems)
        .active(true)
        .build();
  }

  /**
   * Update this class with new values while preserving identity (id, trainerId, createdAt, active).
   *
   * <p>All domain validations are re-applied to ensure business invariants are never bypassed.
   *
   * @param title new class title
   * @param category new category
   * @param description new description
   * @param priceAmount new price
   * @param durationMinutes new duration
   * @param tags new tag list
   * @param features new feature list
   * @param personalItems new personal items note
   * @return new MarketplaceClass instance with updated values and preserved identity
   */
  public MarketplaceClass update(
      String title,
      ClassCategory category,
      String description,
      int priceAmount,
      int durationMinutes,
      List<String> tags,
      List<String> features,
      String personalItems) {

    validateTitle(title);
    validateCategory(category);
    validateDescription(description);
    validatePrice(priceAmount);
    validateDuration(durationMinutes);
    validateTags(tags);
    validateFeatures(features);

    return toBuilder()
        .title(title)
        .category(category)
        .description(description)
        .priceAmount(priceAmount)
        .durationMinutes(durationMinutes)
        .tags(tags == null ? List.of() : List.copyOf(tags))
        .features(features == null ? List.of() : List.copyOf(features))
        .personalItems(personalItems)
        .build();
  }

  /**
   * Toggle the class status between active and inactive.
   *
   * @return new MarketplaceClass instance with the toggled active flag
   */
  public MarketplaceClass toggleStatus() {
    return toBuilder().active(!this.active).build();
  }

  // ============================================
  // Domain Query Methods
  // ============================================

  /**
   * Returns true if this class is owned by the given trainer.
   *
   * @param targetTrainerId trainer ID to check
   * @return true if the trainer is the owner
   */
  public boolean isOwnedBy(Long targetTrainerId) {
    return this.trainerId != null && this.trainerId.equals(targetTrainerId);
  }

  // ============================================
  // Validation Methods
  // ============================================

  private static void validateTrainerId(Long trainerId) {
    if (trainerId == null || trainerId <= 0) {
      throw new MarketplaceInvalidTrainerIdException();
    }
  }

  private static void validateTitle(String title) {
    if (title == null || title.isBlank()) {
      throw new MarketplaceInvalidTitleException("Title must not be null or blank");
    }
    if (title.length() > MAX_TITLE_LENGTH) {
      throw new MarketplaceInvalidTitleException(
          "Title must not exceed " + MAX_TITLE_LENGTH + " characters");
    }
  }

  private static void validateCategory(ClassCategory category) {
    if (category == null) {
      throw new MarketplaceInvalidCategoryException();
    }
  }

  private static void validateDescription(String description) {
    if (description == null || description.isBlank()) {
      throw new MarketplaceInvalidDescriptionException("Description must not be null or blank");
    }
  }

  private static void validatePrice(int priceAmount) {
    if (priceAmount <= 0) {
      throw new MarketplaceInvalidPriceException(
          "Price must be a positive number, got: " + priceAmount);
    }
  }

  private static void validateDuration(int durationMinutes) {
    if (durationMinutes < MIN_DURATION_MINUTES || durationMinutes > MAX_DURATION_MINUTES) {
      throw new MarketplaceInvalidDurationException(
          "Duration must be between "
              + MIN_DURATION_MINUTES
              + " and "
              + MAX_DURATION_MINUTES
              + " minutes, got: "
              + durationMinutes);
    }
  }

  private static void validateTags(List<String> tags) {
    if (tags == null) {
      return;
    }
    if (tags.size() > MAX_TAGS) {
      throw new MarketplaceInvalidTagException("Tags must not exceed " + MAX_TAGS + " items");
    }
    for (String tag : tags) {
      if (tag == null || tag.isBlank()) {
        throw new MarketplaceInvalidTagException("Each tag must not be null or blank");
      }
      if (tag.length() > MAX_TAG_LENGTH) {
        throw new MarketplaceInvalidTagException(
            "Each tag must not exceed " + MAX_TAG_LENGTH + " characters");
      }
    }
  }

  private static void validateFeatures(List<String> features) {
    if (features == null) {
      return;
    }
    if (features.size() > MAX_FEATURES) {
      throw new MarketplaceInvalidFeatureException(
          "Features must not exceed " + MAX_FEATURES + " items");
    }
    for (String feature : features) {
      if (feature == null || feature.isBlank()) {
        throw new MarketplaceInvalidFeatureException("Each feature must not be null or blank");
      }
      if (feature.length() > MAX_FEATURE_LENGTH) {
        throw new MarketplaceInvalidFeatureException(
            "Each feature must not exceed " + MAX_FEATURE_LENGTH + " characters");
      }
    }
  }

  // ============================================
  // Builder override for mutable list safety
  // ============================================

  /**
   * Returns an unmodifiable view of the tags list. Never null.
   *
   * @return unmodifiable list of tags
   */
  public List<String> getTags() {
    return tags == null ? List.of() : Collections.unmodifiableList(tags);
  }

  /**
   * Returns an unmodifiable view of the features list. Never null.
   *
   * @return unmodifiable list of features
   */
  public List<String> getFeatures() {
    return features == null ? List.of() : Collections.unmodifiableList(features);
  }

  /** Lombok-generated builder supplemented with validation-safe list initialization. */
  public static class MarketplaceClassBuilder {
    private List<String> tags = new ArrayList<>();
    private List<String> features = new ArrayList<>();
  }
}
