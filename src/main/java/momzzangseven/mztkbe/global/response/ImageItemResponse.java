package momzzangseven.mztkbe.global.response;

/** Shared image item payload used across post/answer-style API responses. */
public record ImageItemResponse(Long imageId, String imageUrl) {}
