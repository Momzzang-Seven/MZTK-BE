package momzzangseven.mztkbe.global.secret.application.port.out;

import java.util.Optional;

/**
 * Shared boundary for future secret-store access.
 *
 * <p>This port is introduced as part of the ASM-readiness refactor to lock down the package
 * boundary before the actual KEK/secret source migration happens. The current web3 runtime still
 * relies on the existing raw KEK config contract, so this port is intentionally a scaffold for the
 * follow-up integration step.
 */
public interface LoadSecretValuePort {

  Optional<String> load(String handle);
}
