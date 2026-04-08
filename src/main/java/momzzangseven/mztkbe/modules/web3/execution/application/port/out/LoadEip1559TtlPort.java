package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

/** Output port for loading fallback EIP-1559 intent TTL seconds. */
public interface LoadEip1559TtlPort {

  /** Returns TTL in seconds used when creating EIP-1559 execution intents. */
  long loadTtlSeconds();
}
