package momzzangseven.mztkbe.global.error;

import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;

public class UnsupportedProviderException extends BusinessException {
  public UnsupportedProviderException(AuthProvider provider) {
    super(
        ErrorCode.UNSUPPORTED_PROVIDER,
        ErrorCode.UNSUPPORTED_PROVIDER.getMessage() + ": " + provider);
  }
}
