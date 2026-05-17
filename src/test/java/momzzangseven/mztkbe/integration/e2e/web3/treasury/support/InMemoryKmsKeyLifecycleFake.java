package momzzangseven.mztkbe.integration.e2e.web3.treasury.support;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import momzzangseven.mztkbe.global.error.treasury.KmsAliasAlreadyExistsException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.AliasTargetInfo;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;

/**
 * In-memory test fake of {@link KmsKeyLifecyclePort}.
 *
 * <p>Tracks alias→keyId bindings and per-key {@link KmsKeyState} so E2E tests can assert KMS-side
 * state (alias target, key state transitions) after operations — going beyond the call-verification
 * that a Mockito mock provides. Mirrors the production {@link
 * momzzangseven.mztkbe.modules.web3.treasury.infrastructure.adapter.KmsKeyLifecycleAdapter}
 * contract on the points that matter for treasury provisioning: alias uniqueness (throws {@link
 * KmsAliasAlreadyExistsException} on duplicate {@code createAlias}), {@code describeAlias}
 * returning {@code (UNAVAILABLE, null)} for an unknown alias, and idempotent state transitions on
 * {@code disableKey} / {@code enableKey} / {@code scheduleKeyDeletion}.
 *
 * <p>Tests can additionally register one-shot latches via {@link #gateUpdateAliasFor} / {@link
 * #gateDisableKeyFor} / {@link #gateEnableKeyFor} to force AFTER_COMMIT interleaving scenarios —
 * the matching call blocks on {@code latch.await()} until the test releases it.
 */
public final class InMemoryKmsKeyLifecycleFake implements KmsKeyLifecyclePort {

  // ── State ──
  private final Map<String, KmsKeyState> keyStates = new ConcurrentHashMap<>();
  private final Map<String, String> aliasToKeyId = new ConcurrentHashMap<>();
  private final AtomicInteger keyIdCounter = new AtomicInteger();

  // ── Configuration knobs ──
  private volatile String fixedKeyIdForNextCreate;
  private volatile String dynamicKeyPrefix;
  private volatile RuntimeException nextCreateAliasFailure;

  // ── Latch / gate hooks for AFTER_COMMIT interleaving tests ──
  private final Map<String, CountDownLatch> updateAliasGates = new ConcurrentHashMap<>();
  private final Map<String, CountDownLatch> disableKeyGates = new ConcurrentHashMap<>();
  private final Map<String, CountDownLatch> enableKeyGates = new ConcurrentHashMap<>();

  public void reset() {
    keyStates.clear();
    aliasToKeyId.clear();
    keyIdCounter.set(0);
    updateAliasGates.clear();
    disableKeyGates.clear();
    enableKeyGates.clear();
    fixedKeyIdForNextCreate = null;
    dynamicKeyPrefix = null;
    nextCreateAliasFailure = null;
  }

  // Force the next createKey() to return this id, then revert to dynamic / default.
  public void useFixedKeyIdForNextCreate(String keyId) {
    this.fixedKeyIdForNextCreate = keyId;
  }

  // Enable counter-based dynamic key minting (prefix + atomic counter).
  public void enableDynamicKeyMinting(String prefix) {
    this.dynamicKeyPrefix = prefix;
  }

  // One-shot failure injection for the next createAlias call.
  public void useFailNextCreateAlias(RuntimeException failure) {
    this.nextCreateAliasFailure = failure;
  }

  // Register a one-shot latch the matching call awaits before proceeding.
  public void gateUpdateAliasFor(String alias, CountDownLatch gate) {
    updateAliasGates.put(alias, gate);
  }

  public void gateDisableKeyFor(String keyId, CountDownLatch gate) {
    disableKeyGates.put(keyId, gate);
  }

  public void gateEnableKeyFor(String keyId, CountDownLatch gate) {
    enableKeyGates.put(keyId, gate);
  }

  // ── Assertion API ──

  public String aliasTarget(String alias) {
    return aliasToKeyId.get(alias);
  }

  public KmsKeyState keyState(String keyId) {
    return keyStates.get(keyId);
  }

  public Set<String> allAliases() {
    return Set.copyOf(aliasToKeyId.keySet());
  }

  public Set<String> allKeyIds() {
    return Set.copyOf(keyStates.keySet());
  }

  // ── Port impl ──

  @Override
  public String createKey() {
    String id;
    if (fixedKeyIdForNextCreate != null) {
      id = fixedKeyIdForNextCreate;
      fixedKeyIdForNextCreate = null;
    } else if (dynamicKeyPrefix != null) {
      id = dynamicKeyPrefix + keyIdCounter.incrementAndGet();
    } else {
      id = "fake-kms-key-" + keyIdCounter.incrementAndGet();
    }
    keyStates.put(id, KmsKeyState.ENABLED);
    return id;
  }

  @Override
  public ImportParams getParametersForImport(String kmsKeyId) {
    return new ImportParams(new byte[] {1}, new byte[] {2});
  }

  @Override
  public void importKeyMaterial(String kmsKeyId, byte[] encryptedKeyMaterial, byte[] importToken) {
    // no-op
  }

  @Override
  public void createAlias(String alias, String kmsKeyId) {
    RuntimeException injected = nextCreateAliasFailure;
    if (injected != null) {
      nextCreateAliasFailure = null;
      throw injected;
    }
    if (aliasToKeyId.containsKey(alias)) {
      throw new KmsAliasAlreadyExistsException(
          "alias '" + alias + "' is already bound to a KMS key");
    }
    aliasToKeyId.put(alias, kmsKeyId);
  }

  @Override
  public void updateAlias(String alias, String newKmsKeyId) {
    awaitGate(updateAliasGates, alias);
    if (!keyStates.containsKey(newKmsKeyId)) {
      throw new IllegalStateException(
          "updateAlias target key id is unknown to the fake: " + newKmsKeyId);
    }
    aliasToKeyId.put(alias, newKmsKeyId);
  }

  @Override
  public AliasTargetInfo describeAlias(String alias) {
    String keyId = aliasToKeyId.get(alias);
    if (keyId == null) {
      return new AliasTargetInfo(KmsKeyState.UNAVAILABLE, null);
    }
    return new AliasTargetInfo(keyStates.getOrDefault(keyId, KmsKeyState.UNAVAILABLE), keyId);
  }

  @Override
  public void disableKey(String kmsKeyId) {
    awaitGate(disableKeyGates, kmsKeyId);
    keyStates.computeIfPresent(kmsKeyId, (k, v) -> KmsKeyState.DISABLED);
  }

  @Override
  public void enableKey(String kmsKeyId) {
    awaitGate(enableKeyGates, kmsKeyId);
    keyStates.computeIfPresent(kmsKeyId, (k, v) -> KmsKeyState.ENABLED);
  }

  @Override
  public void scheduleKeyDeletion(String kmsKeyId, int pendingWindowDays) {
    keyStates.computeIfPresent(kmsKeyId, (k, v) -> KmsKeyState.PENDING_DELETION);
  }

  private static void awaitGate(Map<String, CountDownLatch> gates, String key) {
    CountDownLatch latch = gates.remove(key);
    if (latch == null) {
      return;
    }
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }
}
