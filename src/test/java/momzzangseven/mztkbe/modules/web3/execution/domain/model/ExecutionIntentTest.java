package momzzangseven.mztkbe.modules.web3.execution.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import org.junit.jupiter.api.Test;

class ExecutionIntentTest {

  private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 4, 7, 12, 0);

  @Test
  void eip7702IntentTransitionsFromAwaitingSignatureToSignedAndPendingOnchain() {
    ExecutionIntent intent =
        ExecutionIntent.create(
            "intent-public-id",
            "root-key",
            1,
            ExecutionResourceType.TRANSFER,
            "resource-id",
            ExecutionActionType.TRANSFER_SEND,
            1L,
            2L,
            ExecutionMode.EIP7702,
            "0xpayload",
            "{\"amount\":\"1\"}",
            "0xauthority",
            3L,
            "0xdelegate",
            FIXED_NOW.plusMinutes(5),
            "0xauth-hash",
            "0xexecution-digest",
            null,
            null,
            BigInteger.TEN,
            LocalDate.of(2026, 4, 6),
            FIXED_NOW);

    assertTrue(intent.shouldExposeSignRequest());
    assertTrue(intent.hasSamePayload("0xpayload"));
    assertTrue(intent.isActiveForReuse());

    ExecutionIntent signed = intent.markSigned(11L, FIXED_NOW.plusSeconds(1));
    assertEquals(ExecutionIntentStatus.SIGNED, signed.getStatus());
    assertEquals(11L, signed.getSubmittedTxId());
    assertFalse(signed.shouldExposeSignRequest());

    ExecutionIntent pending = signed.markPendingOnchain(11L, FIXED_NOW.plusSeconds(2));
    assertEquals(ExecutionIntentStatus.PENDING_ONCHAIN, pending.getStatus());
    assertEquals(11L, pending.getSubmittedTxId());
  }

  @Test
  void eip1559IntentCanBecomeNonceStaleAndAllowNewAttempt() {
    ExecutionIntent intent =
        ExecutionIntent.create(
            "intent-public-id",
            "root-key",
            1,
            ExecutionResourceType.TRANSFER,
            "resource-id",
            ExecutionActionType.TRANSFER_SEND,
            1L,
            2L,
            ExecutionMode.EIP1559,
            "0xpayload",
            "{\"amount\":\"1\"}",
            null,
            null,
            null,
            FIXED_NOW.plusMinutes(1),
            null,
            null,
            new UnsignedTxSnapshot(
                11155111L,
                "0xfrom",
                "0xto",
                BigInteger.ZERO,
                "0xa9059cbb",
                7L,
                BigInteger.valueOf(50_000),
                BigInteger.valueOf(2_000_000_000L),
                BigInteger.valueOf(30_000_000_000L)),
            "0xfingerprint",
            BigInteger.ZERO,
            LocalDate.of(2026, 4, 6),
            FIXED_NOW);

    ExecutionIntent stale =
        intent.markNonceStale("NONCE_STALE", "nonce moved", FIXED_NOW.plusSeconds(1));
    assertEquals(ExecutionIntentStatus.NONCE_STALE, stale.getStatus());
    assertTrue(stale.canStartNewAttempt());
    assertFalse(stale.isActiveForReuse());
    assertTrue(stale.getStatus().isTerminal());
  }

  @Test
  void resolveSponsorUsageDateFallsBackToCreatedAtDate_whenFieldMissing() {
    ExecutionIntent intent =
        ExecutionIntent.builder()
            .publicId("intent-public-id")
            .rootIdempotencyKey("root-key")
            .attemptNo(1)
            .resourceType(ExecutionResourceType.TRANSFER)
            .resourceId("resource-id")
            .actionType(ExecutionActionType.TRANSFER_SEND)
            .requesterUserId(1L)
            .mode(ExecutionMode.EIP7702)
            .status(ExecutionIntentStatus.AWAITING_SIGNATURE)
            .payloadHash("0xpayload")
            .expiresAt(FIXED_NOW.plusMinutes(5))
            .createdAt(LocalDateTime.of(2026, 4, 7, 0, 3))
            .build();

    assertEquals(LocalDate.of(2026, 4, 7), intent.resolveSponsorUsageDateKst());
  }
}
