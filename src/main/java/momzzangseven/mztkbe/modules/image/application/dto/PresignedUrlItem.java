package momzzangseven.mztkbe.modules.image.application.dto;

/** A single presigned URL with its corresponding S3 tmp object key. */
public record PresignedUrlItem(String presignedUrl, String tmpObjectKey) {}
