package momzzangseven.mztkbe.global.secret.application.port.out;

import java.util.Optional;

/**
 * Shared boundary for future secret-store access.
 *
 * <p>The legacy config-side secret contract has since been removed, but the secret-store
 * integration that this port is meant to front (AWS Secrets Manager / SSM) has not yet landed. The
 * port remains a scaffold so consumers can be wired against the package boundary today and the
 * implementation can be filled in by the follow-up integration step.
 */
public interface LoadSecretValuePort {

  Optional<String> load(String handle);
}
