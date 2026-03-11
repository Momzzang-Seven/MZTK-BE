package momzzangseven.mztkbe.modules.image.domain.vo;

/** Lifecycle status of an image record. */
public enum ImageStatus {
  /** Lambda has not yet processed the image. */
  PENDING,
  /** Lambda successfully converted and stored the final image. */
  COMPLETED,
  /** Lambda processing failed. */
  FAILED
}
