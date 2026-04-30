package momzzangseven.mztkbe.integration.e2e.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * E2E integration tests for the Marketplace Reservation lifecycle.
 *
 * <p>Covers the full happy-path flow (PENDING → APPROVED → SETTLED / REJECTED) and critical failure
 * cases. Web3 escrow calls are transparently handled by the {@link
 * momzzangseven.mztkbe.modules.marketplace.infrastructure.external.web3.EscrowTransactionAdapter}
 * stub, which returns a deterministic fake {@code txHash} — sufficient for verifying DB state
 * transitions without a live blockchain connection.
 *
 * <p>Run with: {@code ./gradlew e2eTest}
 */
@TestPropertySource(
    properties = {"web3.chain-id=1337", "web3.eip712.chain-id=1337", "web3.eip7702.enabled=false"})
@DisplayName("[E2E] Marketplace Reservation lifecycle")
class ReservationLifecycleE2ETest extends E2ETestBase {

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;

  // web3.admin layer — mocked so MarkTransactionSucceededAdapter is
  // replaced without requiring a live transaction module
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  // web3.transaction layer — must be mocked so MarkTransactionSucceededAdapter
  // (which depends on this use case) can be created even when
  // the transaction module's own beans are conditional.
  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      txMarkTransactionSucceededUseCase;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      txTransactionReceiptWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      txTransactionIssuerWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      txSignedRecoveryWorker;

  // ===================================================================
  // Happy-path: PENDING → APPROVED → SETTLED
  // ===================================================================

  @Nested
  @DisplayName("Full lifecycle: PENDING → APPROVED → SETTLED")
  class FullSettlementLifecycle {

    @Test
    @DisplayName("user creates reservation → trainer approves → user completes → DB shows SETTLED")
    void fullSettlementFlow_persistsCorrectStatusSequence() throws Exception {
      // given — trainer with a registered class and slot
      TestUser trainer = signupAndLogin("trainer-full");
      trainer = promoteToTrainer(trainer);
      long storeId = insertStore(trainer.userId());
      long classId = insertClass(trainer.userId(), storeId, "Full PT", 50_000);

      // next Monday at 10:00 (always in future)
      LocalDate sessionDate = nextWeekday(DayOfWeek.MONDAY);
      long slotId = insertSlot(classId, DayOfWeek.MONDAY, LocalTime.of(10, 0), 5, 60);

      TestUser user = signupAndLogin("user-full");

      // when — user creates reservation
      ResponseEntity<String> createResponse =
          restTemplate.exchange(
              baseUrl() + "/marketplace/classes/" + classId + "/reservations",
              HttpMethod.POST,
              new HttpEntity<>(
                  Map.of(
                      "slotId",
                      slotId,
                      "reservationDate",
                      sessionDate.toString(),
                      "reservationTime",
                      "10:00:00",
                      "signedAmount",
                      50_000,
                      "delegationSignature",
                      "0x" + "a".repeat(130),
                      "executionSignature",
                      "0x" + "b".repeat(130)),
                  bearerJsonHeaders(user.accessToken())),
              String.class);

      assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode createRoot = parse(createResponse);
      assertThat(createRoot.at("/status").asText()).isEqualTo("SUCCESS");
      long reservationId = createRoot.at("/data/reservationId").asLong();
      assertThat(createRoot.at("/data/status").asText()).isEqualTo("PENDING");
      assertDbStatus(reservationId, "PENDING");

      // trainer approves
      ResponseEntity<String> approveResponse =
          restTemplate.exchange(
              baseUrl() + "/marketplace/trainer/reservations/" + reservationId + "/approve",
              HttpMethod.PATCH,
              new HttpEntity<>(bearerJsonHeaders(trainer.accessToken())),
              String.class);

      assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(parse(approveResponse).at("/data/status").asText()).isEqualTo("APPROVED");
      assertDbStatus(reservationId, "APPROVED");

      // force session to past so complete is allowed (update reservation_date to
      // yesterday)
      jdbcTemplate.update(
          "UPDATE class_reservations SET reservation_date = ?, reservation_time = ? WHERE id = ?",
          java.sql.Date.valueOf(LocalDate.now().minusDays(1)),
          java.sql.Time.valueOf(LocalTime.of(9, 0)),
          reservationId);

      // user completes
      ResponseEntity<String> completeResponse =
          restTemplate.exchange(
              baseUrl() + "/marketplace/me/reservations/" + reservationId + "/complete",
              HttpMethod.PATCH,
              new HttpEntity<>(bearerJsonHeaders(user.accessToken())),
              String.class);

      assertThat(completeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(parse(completeResponse).at("/data/status").asText()).isEqualTo("SETTLED");
      assertDbStatus(reservationId, "SETTLED");
    }
  }

  // ===================================================================
  // Happy-path: PENDING → REJECTED
  // ===================================================================

  @Nested
  @DisplayName("Trainer rejection: PENDING → REJECTED")
  class TrainerRejectionLifecycle {

    @Test
    @DisplayName("trainer rejects pending reservation → DB shows REJECTED with txHash")
    void trainerReject_persistsRejectedStatusAndTxHash() throws Exception {
      TestUser trainer = signupAndLogin("trainer-reject");
      trainer = promoteToTrainer(trainer);
      long storeId = insertStore(trainer.userId());
      long classId = insertClass(trainer.userId(), storeId, "Reject PT", 30_000);
      LocalDate sessionDate = nextWeekday(DayOfWeek.WEDNESDAY);
      long slotId = insertSlot(classId, DayOfWeek.WEDNESDAY, LocalTime.of(14, 0), 3, 50);

      TestUser user = signupAndLogin("user-reject");

      long reservationId =
          createReservation(user, classId, slotId, sessionDate, "14:00:00", 30_000);
      assertDbStatus(reservationId, "PENDING");

      ResponseEntity<String> rejectResponse =
          restTemplate.exchange(
              baseUrl() + "/marketplace/trainer/reservations/" + reservationId + "/reject",
              HttpMethod.PATCH,
              new HttpEntity<>(
                  Map.of("rejectionReason", "해당 시간에 타 일정이 있습니다."),
                  bearerJsonHeaders(trainer.accessToken())),
              String.class);

      assertThat(rejectResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode rejectRoot = parse(rejectResponse);
      assertThat(rejectRoot.at("/data/status").asText()).isEqualTo("REJECTED");
      assertDbStatus(reservationId, "REJECTED");

      // txHash should have been updated (stub returns deterministic value)
      String txHash =
          jdbcTemplate.queryForObject(
              "SELECT tx_hash FROM class_reservations WHERE id = ?", String.class, reservationId);
      assertThat(txHash).isNotBlank();
    }
  }

  // ===================================================================
  // Happy-path: PENDING → USER_CANCELLED
  // ===================================================================

  @Nested
  @DisplayName("User cancellation: PENDING → USER_CANCELLED")
  class UserCancellationLifecycle {

    @Test
    @DisplayName("user cancels pending reservation → DB shows USER_CANCELLED")
    void userCancel_persistsCancelledStatus() throws Exception {
      TestUser trainer = signupAndLogin("trainer-cancel");
      trainer = promoteToTrainer(trainer);
      long storeId = insertStore(trainer.userId());
      long classId = insertClass(trainer.userId(), storeId, "Cancel PT", 20_000);
      LocalDate sessionDate = nextWeekday(DayOfWeek.FRIDAY);
      long slotId = insertSlot(classId, DayOfWeek.FRIDAY, LocalTime.of(8, 0), 2, 45);

      TestUser user = signupAndLogin("user-cancel");
      long reservationId =
          createReservation(user, classId, slotId, sessionDate, "08:00:00", 20_000);

      ResponseEntity<String> cancelResponse =
          restTemplate.exchange(
              baseUrl() + "/marketplace/me/reservations/" + reservationId + "/cancel",
              HttpMethod.PATCH,
              new HttpEntity<>(bearerJsonHeaders(user.accessToken())),
              String.class);

      assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(parse(cancelResponse).at("/data/status").asText()).isEqualTo("USER_CANCELLED");
      assertDbStatus(reservationId, "USER_CANCELLED");
    }
  }

  // ===================================================================
  // 4-week schedule query
  // ===================================================================

  @Nested
  @DisplayName("4-week schedule query")
  class ScheduleQuery {

    @Test
    @DisplayName("GET reservation-info returns available dates within 28-day window")
    void getReservationInfo_returnsAvailableDates() throws Exception {
      TestUser trainer = signupAndLogin("trainer-schedule");
      trainer = promoteToTrainer(trainer);
      long storeId = insertStore(trainer.userId());
      long classId = insertClass(trainer.userId(), storeId, "Schedule PT", 40_000);
      insertSlot(classId, DayOfWeek.TUESDAY, LocalTime.of(11, 0), 5, 60);
      insertSlot(classId, DayOfWeek.THURSDAY, LocalTime.of(15, 0), 3, 60);

      TestUser user = signupAndLogin("user-schedule");

      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl() + "/marketplace/classes/" + classId + "/reservation-info",
              HttpMethod.GET,
              new HttpEntity<>(bearerJsonHeaders(user.accessToken())),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode root = parse(response);
      assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
      assertThat(root.at("/data/classId").asLong()).isEqualTo(classId);
      assertThat(root.at("/data/availableDates").isArray()).isTrue();
      // at least some Tuesday/Thursday dates within 28 days
      assertThat(root.at("/data/availableDates").size()).isGreaterThan(0);
    }
  }

  // ===================================================================
  // Failure cases
  // ===================================================================

  @Nested
  @DisplayName("Failure cases")
  class FailureCases {

    @Test
    @DisplayName("creating reservation with wrong day-of-week returns 400 INVALID_SLOT_DATE")
    void createReservation_wrongDayOfWeek_returns400() throws Exception {
      TestUser trainer = signupAndLogin("trainer-badday");
      trainer = promoteToTrainer(trainer);
      long storeId = insertStore(trainer.userId());
      long classId = insertClass(trainer.userId(), storeId, "BadDay PT", 50_000);
      // slot is on MONDAY but we will send TUESDAY
      insertSlot(classId, DayOfWeek.MONDAY, LocalTime.of(9, 0), 5, 60);

      TestUser user = signupAndLogin("user-badday");
      // find the next TUESDAY (wrong day)
      LocalDate wrongDate = nextWeekday(DayOfWeek.TUESDAY);
      long slotId =
          jdbcTemplate.queryForObject(
              "SELECT id FROM class_slots WHERE class_id = ?", Long.class, classId);

      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl() + "/marketplace/classes/" + classId + "/reservations",
              HttpMethod.POST,
              new HttpEntity<>(
                  Map.of(
                      "slotId",
                      slotId,
                      "reservationDate",
                      wrongDate.toString(),
                      "reservationTime",
                      "09:00:00",
                      "signedAmount",
                      50_000,
                      "delegationSignature",
                      "0x" + "a".repeat(130),
                      "executionSignature",
                      "0x" + "b".repeat(130)),
                  bearerJsonHeaders(user.accessToken())),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(parse(response).at("/code").asText()).isEqualTo("MARKETPLACE_021");
      assertThat(countReservationsByClass(classId)).isZero();
    }

    @Test
    @DisplayName("price mismatch returns 400 PRICE_MISMATCH and no reservation row created")
    void createReservation_priceMismatch_returns400() throws Exception {
      TestUser trainer = signupAndLogin("trainer-price");
      trainer = promoteToTrainer(trainer);
      long storeId = insertStore(trainer.userId());
      long classId = insertClass(trainer.userId(), storeId, "Price PT", 50_000);
      LocalDate sessionDate = nextWeekday(DayOfWeek.MONDAY);
      long slotId = insertSlot(classId, DayOfWeek.MONDAY, LocalTime.of(9, 0), 5, 60);

      TestUser user = signupAndLogin("user-price");

      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl() + "/marketplace/classes/" + classId + "/reservations",
              HttpMethod.POST,
              new HttpEntity<>(
                  Map.of(
                      "slotId",
                      slotId,
                      "reservationDate",
                      sessionDate.toString(),
                      "reservationTime",
                      "09:00:00",
                      "signedAmount",
                      99_999, // wrong amount
                      "delegationSignature",
                      "0x" + "a".repeat(130),
                      "executionSignature",
                      "0x" + "b".repeat(130)),
                  bearerJsonHeaders(user.accessToken())),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(parse(response).at("/code").asText()).isEqualTo("MARKETPLACE_020");
      assertThat(countReservationsByClass(classId)).isZero();
    }

    @Test
    @DisplayName("completing a PENDING reservation (not APPROVED) returns 409 INVALID_STATUS")
    void completeReservation_onPending_returns409() throws Exception {
      TestUser trainer = signupAndLogin("trainer-status");
      trainer = promoteToTrainer(trainer);
      long storeId = insertStore(trainer.userId());
      long classId = insertClass(trainer.userId(), storeId, "Status PT", 20_000);
      LocalDate sessionDate = nextWeekday(DayOfWeek.THURSDAY);
      long slotId = insertSlot(classId, DayOfWeek.THURSDAY, LocalTime.of(16, 0), 3, 40);

      TestUser user = signupAndLogin("user-status");
      long reservationId =
          createReservation(user, classId, slotId, sessionDate, "16:00:00", 20_000);

      // try to complete without approval
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl() + "/marketplace/me/reservations/" + reservationId + "/complete",
              HttpMethod.PATCH,
              new HttpEntity<>(bearerJsonHeaders(user.accessToken())),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(parse(response).at("/code").asText()).isEqualTo("MARKETPLACE_018");
      assertDbStatus(reservationId, "PENDING");
    }

    @Test
    @DisplayName("other user cannot cancel a reservation they do not own → 403")
    void cancelReservation_byOtherUser_returns403() throws Exception {
      TestUser trainer = signupAndLogin("trainer-auth");
      trainer = promoteToTrainer(trainer);
      long storeId = insertStore(trainer.userId());
      long classId = insertClass(trainer.userId(), storeId, "Auth PT", 10_000);
      LocalDate sessionDate = nextWeekday(DayOfWeek.MONDAY);
      long slotId = insertSlot(classId, DayOfWeek.MONDAY, LocalTime.of(7, 0), 2, 30);

      TestUser owner = signupAndLogin("user-owner");
      TestUser intruder = signupAndLogin("user-intruder");
      long reservationId =
          createReservation(owner, classId, slotId, sessionDate, "07:00:00", 10_000);

      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl() + "/marketplace/me/reservations/" + reservationId + "/cancel",
              HttpMethod.PATCH,
              new HttpEntity<>(bearerJsonHeaders(intruder.accessToken())),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
      assertDbStatus(reservationId, "PENDING");
    }

    @Test
    @DisplayName("completing before class start time returns 400 EARLY_COMPLETE")
    void completeReservation_beforeClassStart_returns400() throws Exception {
      TestUser trainer = signupAndLogin("trainer-early");
      trainer = promoteToTrainer(trainer);
      long storeId = insertStore(trainer.userId());
      long classId = insertClass(trainer.userId(), storeId, "Early PT", 15_000);
      LocalDate futureDate = nextWeekday(DayOfWeek.FRIDAY);
      long slotId = insertSlot(classId, DayOfWeek.FRIDAY, LocalTime.of(23, 0), 2, 60);

      TestUser user = signupAndLogin("user-early");
      long reservationId = createReservation(user, classId, slotId, futureDate, "23:00:00", 15_000);

      // approve first
      jdbcTemplate.update(
          "UPDATE class_reservations SET status = 'APPROVED' WHERE id = ?", reservationId);

      // try to complete (session is in the future)
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl() + "/marketplace/me/reservations/" + reservationId + "/complete",
              HttpMethod.PATCH,
              new HttpEntity<>(bearerJsonHeaders(user.accessToken())),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(parse(response).at("/code").asText()).isEqualTo("MARKETPLACE_023");
    }

    @Test
    @DisplayName("slot capacity exceeded returns 409 SLOT_FULL")
    void createReservation_slotFull_returns409() throws Exception {
      TestUser trainer = signupAndLogin("trainer-full-cap");
      trainer = promoteToTrainer(trainer);
      long storeId = insertStore(trainer.userId());
      long classId = insertClass(trainer.userId(), storeId, "Full Cap PT", 10_000);
      LocalDate sessionDate = nextWeekday(DayOfWeek.TUESDAY);
      // capacity = 1
      long slotId = insertSlot(classId, DayOfWeek.TUESDAY, LocalTime.of(12, 0), 1, 60);

      TestUser user1 = signupAndLogin("user-cap-1");
      TestUser user2 = signupAndLogin("user-cap-2");

      // first reservation succeeds
      createReservation(user1, classId, slotId, sessionDate, "12:00:00", 10_000);

      // second reservation should fail
      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl() + "/marketplace/classes/" + classId + "/reservations",
              HttpMethod.POST,
              new HttpEntity<>(
                  Map.of(
                      "slotId",
                      slotId,
                      "reservationDate",
                      sessionDate.toString(),
                      "reservationTime",
                      "12:00:00",
                      "signedAmount",
                      10_000,
                      "delegationSignature",
                      "0x" + "a".repeat(130),
                      "executionSignature",
                      "0x" + "b".repeat(130)),
                  bearerJsonHeaders(user2.accessToken())),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(parse(response).at("/code").asText()).isEqualTo("MARKETPLACE_017");
    }
  }

  // ===================================================================
  // Query: reservation list and detail
  // ===================================================================

  @Nested
  @DisplayName("예약 목록 및 상세 조회")
  class ReservationQueryApis {

    @Test
    @DisplayName("GET /me/reservations - 유저 예약 목록 조회: 본인 예약만 반환")
    void getMyReservations_returnsOnlyOwnReservations() throws Exception {
      TestUser trainer = signupAndLogin("trainer-qlist");
      trainer = promoteToTrainer(trainer);
      long storeId = insertStore(trainer.userId());
      long classId = insertClass(trainer.userId(), storeId, "Query List PT", 10_000);
      LocalDate sessionDate = nextWeekday(DayOfWeek.MONDAY);
      long slotId = insertSlot(classId, DayOfWeek.MONDAY, LocalTime.of(8, 0), 5, 50);

      TestUser owner = signupAndLogin("user-qlist-owner");
      TestUser other = signupAndLogin("user-qlist-other");

      long reservationId =
          createReservation(owner, classId, slotId, sessionDate, "08:00:00", 10_000);

      // owner can see their own reservation
      ResponseEntity<String> ownerRes =
          restTemplate.exchange(
              baseUrl() + "/marketplace/me/reservations",
              HttpMethod.GET,
              new HttpEntity<>(bearerJsonHeaders(owner.accessToken())),
              String.class);

      assertThat(ownerRes.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode ownerData = parse(ownerRes).at("/data");
      assertThat(ownerData.isArray()).isTrue();
      assertThat(ownerData.size()).isGreaterThanOrEqualTo(1);
      boolean found = false;
      for (JsonNode node : ownerData) {
        if (node.at("/reservationId").asLong() == reservationId) {
          found = true;
          break;
        }
      }
      assertThat(found).as("owner's reservation should appear in their list").isTrue();

      // other user's list should NOT contain owner's reservation
      ResponseEntity<String> otherRes =
          restTemplate.exchange(
              baseUrl() + "/marketplace/me/reservations",
              HttpMethod.GET,
              new HttpEntity<>(bearerJsonHeaders(other.accessToken())),
              String.class);

      assertThat(otherRes.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode otherData = parse(otherRes).at("/data");
      for (JsonNode node : otherData) {
        assertThat(node.at("/reservationId").asLong())
            .as("other user must not see owner's reservation")
            .isNotEqualTo(reservationId);
      }
    }

    @Test
    @DisplayName("GET /me/reservations?status=PENDING - 상태 필터 적용")
    void getMyReservations_withStatusFilter_returnsFilteredList() throws Exception {
      TestUser trainer = signupAndLogin("trainer-qfilter");
      trainer = promoteToTrainer(trainer);
      long storeId = insertStore(trainer.userId());
      long classId = insertClass(trainer.userId(), storeId, "Filter PT", 12_000);
      LocalDate sessionDate = nextWeekday(DayOfWeek.WEDNESDAY);
      long slotId = insertSlot(classId, DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), 5, 50);

      TestUser user = signupAndLogin("user-qfilter");
      long reservationId =
          createReservation(user, classId, slotId, sessionDate, "09:00:00", 12_000);

      // status=PENDING → should include the new reservation
      ResponseEntity<String> pendingRes =
          restTemplate.exchange(
              baseUrl() + "/marketplace/me/reservations?status=PENDING",
              HttpMethod.GET,
              new HttpEntity<>(bearerJsonHeaders(user.accessToken())),
              String.class);

      assertThat(pendingRes.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode data = parse(pendingRes).at("/data");
      boolean found = false;
      for (JsonNode node : data) {
        if (node.at("/reservationId").asLong() == reservationId) {
          assertThat(node.at("/status").asText()).isEqualTo("PENDING");
          found = true;
        }
      }
      assertThat(found).as("PENDING reservation should appear in filtered result").isTrue();

      // status=APPROVED → should NOT include it (still PENDING)
      ResponseEntity<String> approvedRes =
          restTemplate.exchange(
              baseUrl() + "/marketplace/me/reservations?status=APPROVED",
              HttpMethod.GET,
              new HttpEntity<>(bearerJsonHeaders(user.accessToken())),
              String.class);

      assertThat(approvedRes.getStatusCode()).isEqualTo(HttpStatus.OK);
      for (JsonNode node : parse(approvedRes).at("/data")) {
        assertThat(node.at("/reservationId").asLong())
            .as("PENDING reservation must not appear in APPROVED filter")
            .isNotEqualTo(reservationId);
      }
    }

    @Test
    @DisplayName("GET /marketplace/reservations/{id} - 예약 상세 조회: 소유 유저가 조회하면 성공")
    void getReservationDetail_byOwnerUser_returnsFullDetail() throws Exception {
      TestUser trainer = signupAndLogin("trainer-qdetail");
      trainer = promoteToTrainer(trainer);
      long storeId = insertStore(trainer.userId());
      long classId = insertClass(trainer.userId(), storeId, "Detail PT", 15_000);
      LocalDate sessionDate = nextWeekday(DayOfWeek.THURSDAY);
      long slotId = insertSlot(classId, DayOfWeek.THURSDAY, LocalTime.of(11, 0), 5, 60);

      TestUser user = signupAndLogin("user-qdetail");
      long reservationId =
          createReservation(user, classId, slotId, sessionDate, "11:00:00", 15_000);

      ResponseEntity<String> detailRes =
          restTemplate.exchange(
              baseUrl() + "/marketplace/reservations/" + reservationId,
              HttpMethod.GET,
              new HttpEntity<>(bearerJsonHeaders(user.accessToken())),
              String.class);

      assertThat(detailRes.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode data = parse(detailRes).at("/data");
      assertThat(data.at("/reservationId").asLong()).isEqualTo(reservationId);
      assertThat(data.at("/status").asText()).isEqualTo("PENDING");
      assertThat(data.at("/orderId").asText()).isNotBlank();
      assertThat(data.at("/txHash").asText()).isNotBlank();
    }

    @Test
    @DisplayName("GET /marketplace/trainer/reservations/{id} - 담당 트레이너가 예약 상세 조회하면 성공")
    void getReservationDetail_byTrainer_returnsFullDetail() throws Exception {
      TestUser trainer = signupAndLogin("trainer-qdetail2");
      trainer = promoteToTrainer(trainer);
      long storeId = insertStore(trainer.userId());
      long classId = insertClass(trainer.userId(), storeId, "Detail PT2", 20_000);
      LocalDate sessionDate = nextWeekday(DayOfWeek.FRIDAY);
      long slotId = insertSlot(classId, DayOfWeek.FRIDAY, LocalTime.of(14, 0), 5, 60);

      TestUser user = signupAndLogin("user-qdetail2");
      long reservationId =
          createReservation(user, classId, slotId, sessionDate, "14:00:00", 20_000);

      ResponseEntity<String> detailRes =
          restTemplate.exchange(
              baseUrl() + "/marketplace/trainer/reservations/" + reservationId,
              HttpMethod.GET,
              new HttpEntity<>(bearerJsonHeaders(trainer.accessToken())),
              String.class);

      assertThat(detailRes.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode data = parse(detailRes).at("/data");
      assertThat(data.at("/reservationId").asLong()).isEqualTo(reservationId);
    }

    @Test
    @DisplayName("GET /marketplace/reservations/{id} - 제3자가 상세 조회 시 403")
    void getReservationDetail_byThirdParty_returns403() throws Exception {
      TestUser trainer = signupAndLogin("trainer-qauth");
      trainer = promoteToTrainer(trainer);
      long storeId = insertStore(trainer.userId());
      long classId = insertClass(trainer.userId(), storeId, "Auth Detail PT", 10_000);
      LocalDate sessionDate = nextWeekday(DayOfWeek.MONDAY);
      long slotId = insertSlot(classId, DayOfWeek.MONDAY, LocalTime.of(7, 0), 5, 50);

      TestUser owner = signupAndLogin("user-qauth-owner");
      TestUser intruder = signupAndLogin("user-qauth-intruder");
      long reservationId =
          createReservation(owner, classId, slotId, sessionDate, "07:00:00", 10_000);

      ResponseEntity<String> res =
          restTemplate.exchange(
              baseUrl() + "/marketplace/reservations/" + reservationId,
              HttpMethod.GET,
              new HttpEntity<>(bearerJsonHeaders(intruder.accessToken())),
              String.class);

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("GET /marketplace/trainer/reservations - 트레이너 수강 신청 목록 조회")
    void getTrainerReservations_returnsOwnReservations() throws Exception {
      TestUser trainer = signupAndLogin("trainer-qtlist");
      trainer = promoteToTrainer(trainer);
      long storeId = insertStore(trainer.userId());
      long classId = insertClass(trainer.userId(), storeId, "Trainer List PT", 10_000);
      LocalDate sessionDate = nextWeekday(DayOfWeek.TUESDAY);
      long slotId = insertSlot(classId, DayOfWeek.TUESDAY, LocalTime.of(10, 0), 5, 60);

      TestUser user = signupAndLogin("user-qtlist");
      long reservationId =
          createReservation(user, classId, slotId, sessionDate, "10:00:00", 10_000);

      ResponseEntity<String> res =
          restTemplate.exchange(
              baseUrl() + "/marketplace/trainer/reservations",
              HttpMethod.GET,
              new HttpEntity<>(bearerJsonHeaders(trainer.accessToken())),
              String.class);

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode data = parse(res).at("/data");
      assertThat(data.isArray()).isTrue();
      boolean found = false;
      for (JsonNode node : data) {
        if (node.at("/reservationId").asLong() == reservationId) {
          found = true;
          break;
        }
      }
      assertThat(found).as("trainer should see the reservation in their list").isTrue();
    }
  }

  // ===================================================================
  // Private helpers
  // ===================================================================

  private TestUser promoteToTrainer(TestUser user) {
    jdbcTemplate.update("UPDATE users SET role = 'TRAINER' WHERE id = ?", user.userId());
    String newAccessToken = loginUser(user.email(), user.password());
    return new TestUser(user.userId(), user.email(), user.password(), newAccessToken);
  }

  private long insertStore(Long trainerId) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        conn -> {
          PreparedStatement ps =
              conn.prepareStatement(
                  "INSERT INTO trainer_stores (user_id, store_name, address, detail_address, phone_number, created_at, updated_at)"
                      + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                  new String[] {"id"});
          Timestamp now = Timestamp.from(Instant.now());
          ps.setLong(1, trainerId);
          ps.setString(2, "E2E Store " + trainerId);
          ps.setString(3, "Test Address");
          ps.setString(4, "Test Detail Address");
          ps.setString(5, "010-1234-5678");
          ps.setTimestamp(6, now);
          ps.setTimestamp(7, now);
          return ps;
        },
        keyHolder);
    return keyHolder.getKey().longValue();
  }

  private long insertClass(Long trainerId, long storeId, String title, long price) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        conn -> {
          PreparedStatement ps =
              conn.prepareStatement(
                  "INSERT INTO marketplace_classes"
                      + " (trainer_id, title, description, category, price_amount, duration_minutes, active, created_at, updated_at)"
                      + " VALUES (?, ?, ?, ?, ?, ?, true, ?, ?)",
                  new String[] {"id"});
          Timestamp now = Timestamp.from(Instant.now());
          ps.setLong(1, trainerId);
          ps.setString(2, title);
          ps.setString(3, "E2E test class");
          ps.setString(4, "YOGA"); // ClassCategory.YOGA.name()
          ps.setInt(5, (int) price);
          ps.setInt(6, 60);
          ps.setTimestamp(7, now);
          ps.setTimestamp(8, now);
          return ps;
        },
        keyHolder);
    return keyHolder.getKey().longValue();
  }

  private long insertSlot(
      long classId, DayOfWeek dayOfWeek, LocalTime startTime, int capacity, int durationMinutes) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        conn -> {
          PreparedStatement ps =
              conn.prepareStatement(
                  "INSERT INTO class_slots"
                      + " (class_id, start_time, capacity, active)"
                      + " VALUES (?, ?::time, ?, true)",
                  new String[] {"id"});
          ps.setLong(1, classId);
          ps.setString(2, startTime.toString());
          ps.setInt(3, capacity);
          return ps;
        },
        keyHolder);

    long slotId = keyHolder.getKey().longValue();
    // insert days_of_week element (stored in separate collection table)
    jdbcTemplate.update(
        "INSERT INTO class_slot_days (slot_id, day_of_week) VALUES (?, ?)",
        slotId,
        dayOfWeek.name());
    return slotId;
  }

  private long createReservation(
      TestUser user, long classId, long slotId, LocalDate date, String time, long amount) {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/marketplace/classes/" + classId + "/reservations",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "slotId",
                    slotId,
                    "reservationDate",
                    date.toString(),
                    "reservationTime",
                    time,
                    "signedAmount",
                    amount,
                    "delegationSignature",
                    "0x" + "a".repeat(130),
                    "executionSignature",
                    "0x" + "b".repeat(130)),
                bearerJsonHeaders(user.accessToken())),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    try {
      return parse(response).at("/data/reservationId").asLong();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse reservationId: " + response.getBody(), e);
    }
  }

  private void assertDbStatus(long reservationId, String expectedStatus) {
    String actual =
        jdbcTemplate.queryForObject(
            "SELECT status FROM class_reservations WHERE id = ?", String.class, reservationId);
    assertThat(actual).isEqualTo(expectedStatus);
  }

  private int countReservationsByClass(long classId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM class_reservations cr"
                + " JOIN class_slots cs ON cr.class_slot_id = cs.id"
                + " WHERE cs.class_id = ?",
            Integer.class,
            classId);
    return count == null ? 0 : count;
  }

  private LocalDate nextWeekday(DayOfWeek dayOfWeek) {
    return LocalDate.now().plusDays(1).with(TemporalAdjusters.nextOrSame(dayOfWeek));
  }

  private JsonNode parse(ResponseEntity<String> response) throws Exception {
    return objectMapper.readTree(response.getBody());
  }
}
