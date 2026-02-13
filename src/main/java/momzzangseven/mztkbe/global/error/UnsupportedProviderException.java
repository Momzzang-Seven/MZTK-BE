package momzzangseven.mztkbe.global.error;

import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;

/** Thrown when an authentication provider is not supported. */
public class UnsupportedProviderException extends BusinessException {
  /** Construct exception for provider that has no strategy. */
  public UnsupportedProviderException(AuthProvider provider) {
    super(
        ErrorCode.UNSUPPORTED_PROVIDER,
        ErrorCode.UNSUPPORTED_PROVIDER.getMessage() + ": " + provider);
  }
}
