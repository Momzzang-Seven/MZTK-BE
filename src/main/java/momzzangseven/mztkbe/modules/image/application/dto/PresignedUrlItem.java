package momzzangseven.mztkbe.modules.image.application.dto;

/** A single presigned URL with its corresponding DB image ID and S3 tmp object key. */
public record PresignedUrlItem(Long imageId, String presignedUrl, String tmpObjectKey) {}
