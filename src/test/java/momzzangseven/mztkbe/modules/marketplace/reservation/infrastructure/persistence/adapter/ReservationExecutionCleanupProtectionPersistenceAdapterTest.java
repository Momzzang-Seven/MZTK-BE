package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.Collection;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionCleanupProtectionQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.MarketplaceReservationActionStateJpaRepository;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.ReservationJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationExecutionCleanupProtectionPersistenceAdapter")
class ReservationExecutionCleanupProtectionPersistenceAdapterTest {

  @Mock private ReservationJpaRepository reservationJpaRepository;
  @Mock private MarketplaceReservationActionStateJpaRepository actionStateJpaRepository;

  @Test
  @DisplayName(
      "reservation pointer, active action-state pointer, unbound pending action intent를 보호한다")
  void findProtectedExecutionIntentPublicIds_returnsReferencedAndOrphanFallbackIntents() {
    given(reservationJpaRepository.findCurrentExecutionIntentPublicIdsIn(anyCollection()))
        .willReturn(List.of("intent-current"));
    given(
            actionStateJpaRepository.findExecutionIntentPublicIdsInByStatusIn(
                anyCollection(), anyCollection()))
        .willReturn(List.of("intent-create"));
    given(
            reservationJpaRepository.countUnboundPendingAction(
                anyLong(), anyCollection(), anyCollection()))
        .willReturn(0L);
    given(
            reservationJpaRepository.countUnboundPendingAction(
                eq(30L), anyCollection(), anyCollection()))
        .willReturn(1L);
    ReservationExecutionCleanupProtectionPersistenceAdapter adapter =
        new ReservationExecutionCleanupProtectionPersistenceAdapter(
            reservationJpaRepository, actionStateJpaRepository);

    List<String> result =
        adapter.findProtectedExecutionIntentPublicIds(
            List.of(
                intent("intent-current", "10", "MARKETPLACE_CLASS_PURCHASE"),
                intent("intent-create", "20", "MARKETPLACE_CLASS_PURCHASE"),
                intent("intent-orphan", "30", "MARKETPLACE_CLASS_CANCEL"),
                intent("intent-free", "40", "MARKETPLACE_CLASS_CONFIRM")));

    assertThat(result).containsExactly("intent-current", "intent-create", "intent-orphan");
  }

  @ParameterizedTest
  @CsvSource({
    "MARKETPLACE_CLASS_PURCHASE,PURCHASE_PREPARING,PURCHASE",
    "MARKETPLACE_CLASS_CONFIRM,CONFIRM_PENDING,BUYER_CONFIRM",
    "MARKETPLACE_CLASS_EXPIRED_REFUND,DEADLINE_REFUND_PENDING,DEADLINE_REFUND"
  })
  @DisplayName("action type별 unbound pending action/status 매핑으로 보호 여부를 조회한다")
  void findProtectedExecutionIntentPublicIds_mapsActionTypes(
      String actionType, String expectedStatus, String expectedAction) {
    given(reservationJpaRepository.findCurrentExecutionIntentPublicIdsIn(anyCollection()))
        .willReturn(List.of());
    given(
            actionStateJpaRepository.findExecutionIntentPublicIdsInByStatusIn(
                anyCollection(), anyCollection()))
        .willReturn(List.of());
    given(
            reservationJpaRepository.countUnboundPendingAction(
                anyLong(), anyCollection(), anyCollection()))
        .willReturn(1L);
    ReservationExecutionCleanupProtectionPersistenceAdapter adapter =
        new ReservationExecutionCleanupProtectionPersistenceAdapter(
            reservationJpaRepository, actionStateJpaRepository);

    List<String> result =
        adapter.findProtectedExecutionIntentPublicIds(List.of(intent("intent", "30", actionType)));

    assertThat(result).containsExactly("intent");
    ArgumentCaptor<Collection<String>> actionCaptor = ArgumentCaptor.forClass(Collection.class);
    ArgumentCaptor<Collection<String>> statusCaptor = ArgumentCaptor.forClass(Collection.class);
    then(reservationJpaRepository)
        .should()
        .countUnboundPendingAction(eq(30L), actionCaptor.capture(), statusCaptor.capture());
    assertThat(statusCaptor.getValue()).contains(expectedStatus);
    assertThat(actionCaptor.getValue()).contains(expectedAction);
  }

  @ParameterizedTest
  @CsvSource({"MARKETPLACE_CLASS_UNKNOWN,30", "MARKETPLACE_CLASS_CANCEL,not-a-number"})
  @DisplayName("unknown action 또는 invalid resourceId는 unbound pending 조회 없이 보호하지 않는다")
  void findProtectedExecutionIntentPublicIds_skipsUnsupportedFallbacks(
      String actionType, String resourceId) {
    given(reservationJpaRepository.findCurrentExecutionIntentPublicIdsIn(anyCollection()))
        .willReturn(List.of());
    given(
            actionStateJpaRepository.findExecutionIntentPublicIdsInByStatusIn(
                anyCollection(), anyCollection()))
        .willReturn(List.of());
    ReservationExecutionCleanupProtectionPersistenceAdapter adapter =
        new ReservationExecutionCleanupProtectionPersistenceAdapter(
            reservationJpaRepository, actionStateJpaRepository);

    List<String> result =
        adapter.findProtectedExecutionIntentPublicIds(
            List.of(intent("intent", resourceId, actionType)));

    assertThat(result).isEmpty();
    then(reservationJpaRepository)
        .should(never())
        .countUnboundPendingAction(anyLong(), anyCollection(), anyCollection());
  }

  @Test
  @DisplayName("marketplace payload evidence가 없거나 깨진 cleanup candidate는 fail-closed 보호한다")
  void findProtectedExecutionIntentPublicIds_protectsMissingOrMalformedMarketplacePayload() {
    given(reservationJpaRepository.findCurrentExecutionIntentPublicIdsIn(anyCollection()))
        .willReturn(List.of());
    given(
            actionStateJpaRepository.findExecutionIntentPublicIdsInByStatusIn(
                anyCollection(), anyCollection()))
        .willReturn(List.of());
    ReservationExecutionCleanupProtectionPersistenceAdapter adapter =
        new ReservationExecutionCleanupProtectionPersistenceAdapter(
            reservationJpaRepository, actionStateJpaRepository);

    List<String> result =
        adapter.findProtectedExecutionIntentPublicIds(
            List.of(
                new ReservationExecutionCleanupProtectionQuery(
                    "intent-missing", "30", "MARKETPLACE_CLASS_PURCHASE", null),
                new ReservationExecutionCleanupProtectionQuery(
                    "intent-malformed", "31", "MARKETPLACE_CLASS_PURCHASE", "{not-json")));

    assertThat(result).containsExactly("intent-missing", "intent-malformed");
    then(reservationJpaRepository)
        .should(never())
        .countUnboundPendingAction(anyLong(), anyCollection(), anyCollection());
  }

  @Test
  @DisplayName("payload evidence가 active action-state와 일치하면 orphan candidate를 보호한다")
  void findProtectedExecutionIntentPublicIds_protectsActivePayloadEvidence() {
    given(reservationJpaRepository.findCurrentExecutionIntentPublicIdsIn(anyCollection()))
        .willReturn(List.of());
    given(
            actionStateJpaRepository.findExecutionIntentPublicIdsInByStatusIn(
                anyCollection(), anyCollection()))
        .willReturn(List.of());
    given(
            actionStateJpaRepository.countActiveByPayloadEvidence(
                eq(200L), eq(30L), eq(100L), anyCollection(), eq("attempt-token"), anyCollection()))
        .willReturn(1L);
    ReservationExecutionCleanupProtectionPersistenceAdapter adapter =
        new ReservationExecutionCleanupProtectionPersistenceAdapter(
            reservationJpaRepository, actionStateJpaRepository);

    List<String> result =
        adapter.findProtectedExecutionIntentPublicIds(
            List.of(intent("intent-evidence", "30", "MARKETPLACE_CLASS_PURCHASE")));

    assertThat(result).containsExactly("intent-evidence");
    then(reservationJpaRepository)
        .should(never())
        .countUnboundPendingAction(anyLong(), anyCollection(), anyCollection());
  }

  private ReservationExecutionCleanupProtectionQuery intent(
      String publicId, String resourceId, String actionType) {
    return new ReservationExecutionCleanupProtectionQuery(
        publicId, resourceId, actionType, payload(resourceId, actionType));
  }

  private String payload(String resourceId, String actionType) {
    return """
        {
          "payloadVersion": 1,
          "reservationId": %s,
          "escrowId": 100,
          "actionStateId": 200,
          "pendingAttemptToken": "attempt-token",
          "actionType": "%s"
        }
        """
        .formatted(resourceId.matches("\\d+") ? resourceId : "30", actionType);
  }
}
