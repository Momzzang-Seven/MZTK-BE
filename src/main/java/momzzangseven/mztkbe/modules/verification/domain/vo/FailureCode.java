package momzzangseven.mztkbe.modules.verification.domain.vo;

/** System failure codes used for retry and terminal failure states. */
public enum FailureCode {
  EXTERNAL_AI_TIMEOUT,
  EXTERNAL_AI_UNAVAILABLE,
  STORAGE_READ_ERROR,
  TEMP_OBJECT_MISSING,
  AI_RESPONSE_SCHEMA_INVALID,
  ANALYSIS_RESULT_CORRUPTED
}
