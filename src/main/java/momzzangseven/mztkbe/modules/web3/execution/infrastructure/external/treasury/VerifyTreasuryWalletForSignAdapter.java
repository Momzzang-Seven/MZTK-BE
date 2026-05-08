package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.treasury;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.VerifyTreasuryWalletForSignPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.VerifyTreasuryWalletForSignUseCase;
import org.springframework.stereotype.Component;

/**
 * Bridging adapter: implements the execution-side {@link VerifyTreasuryWalletForSignPort} by
 * delegating to the treasury module's {@link VerifyTreasuryWalletForSignUseCase}.
 *
 * <p>Bean name is namespaced ({@code executionVerifyTreasuryWalletForSignAdapter}) to avoid a
 * collision with the transaction module's adapter of the same shape.
 */
@Component("executionVerifyTreasuryWalletForSignAdapter")
@RequiredArgsConstructor
public class VerifyTreasuryWalletForSignAdapter implements VerifyTreasuryWalletForSignPort {

  private final VerifyTreasuryWalletForSignUseCase verifyTreasuryWalletForSignUseCase;

  @Override
  public void verify(String walletAlias) {
    verifyTreasuryWalletForSignUseCase.execute(walletAlias);
  }
}
