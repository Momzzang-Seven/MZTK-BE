package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

/** Output port for loading EIP-7702 authorization TTL policy. */
public interface LoadEip7702AuthorizationTtlPort {

  /** Returns the minimum remaining seconds required before exposing an EIP-7702 sign request. */
  long loadMinimumRemainingSeconds();
}
