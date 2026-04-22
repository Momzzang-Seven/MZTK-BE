package momzzangseven.mztkbe.integration.e2e.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
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
    properties = {
      "web3.chain-id=1337",
      "web3.eip712.chain-id=1337",
      "web3.eip7702.enabled=false",
      "web3.reward-token.enabled=false"
    })
@DisplayName("[E2E] Marketplace Reservation lifecycle")
class ReservationLifecycleE2ETest extends E2ETestBase {

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;

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
      promoteToTrainer(trainer.userId());
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

      // force session to past so complete is allowed (update reservation_date to yesterday)
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
      promoteToTrainer(trainer.userId());
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
      promoteToTrainer(trainer.userId());
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
      promoteToTrainer(trainer.userId());
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
      promoteToTrainer(trainer.userId());
      long storeId = insertStore(trainer.userId());
      long classId = insertClass(trainer.userId(), storeId, "BadDay PT", 50_000);
      // slot is on MONDAY but we will send TUESDAY
      insertSlot(classId, DayOfWeek.MONDAY, LocalTime.of(9, 0), 5, 60);

      TestUser user = signupAndLogin("user-badday");
      // find the next TUESDAY (wrong day)
      LocalDate wrongDate = nextWeekday(DayOfWeek.TUESDAY);
      long slotId =
          jdbcTemplate.queryForObject(
              "SELECT id FROM class_slots WHERE marketplace_class_id = ?", Long.class, classId);

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
      promoteToTrainer(trainer.userId());
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
      promoteToTrainer(trainer.userId());
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
      promoteToTrainer(trainer.userId());
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
      promoteToTrainer(trainer.userId());
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
      promoteToTrainer(trainer.userId());
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
  // Private helpers
  // ===================================================================

  private void promoteToTrainer(Long userId) {
    jdbcTemplate.update("UPDATE users SET role = 'TRAINER' WHERE id = ?", userId);
  }

  private long insertStore(Long trainerId) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        conn -> {
          PreparedStatement ps =
              conn.prepareStatement(
                  "INSERT INTO trainer_stores (trainer_id, store_name, description, created_at, updated_at)"
                      + " VALUES (?, ?, ?, ?, ?)",
                  new String[] {"id"});
          Timestamp now = Timestamp.from(Instant.now());
          ps.setLong(1, trainerId);
          ps.setString(2, "E2E Store " + trainerId);
          ps.setString(3, "E2E test store");
          ps.setTimestamp(4, now);
          ps.setTimestamp(5, now);
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
                      + " (trainer_id, store_id, title, description, category, price_amount, duration_minutes, is_active, created_at, updated_at)"
                      + " VALUES (?, ?, ?, ?, ?, ?, ?, true, ?, ?)",
                  new String[] {"id"});
          Timestamp now = Timestamp.from(Instant.now());
          ps.setLong(1, trainerId);
          ps.setLong(2, storeId);
          ps.setString(3, title);
          ps.setString(4, "E2E test class");
          ps.setString(5, "YOGA"); // ClassCategory.YOGA.name()
          ps.setBigDecimal(6, BigDecimal.valueOf(price));
          ps.setInt(7, 60);
          ps.setTimestamp(8, now);
          ps.setTimestamp(9, now);
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
                      + " (marketplace_class_id, start_time, capacity, duration_minutes, is_active, created_at, updated_at)"
                      + " VALUES (?, ?, ?, ?, true, ?, ?)",
                  new String[] {"id"});
          Timestamp now = Timestamp.from(Instant.now());
          ps.setLong(1, classId);
          ps.setString(2, startTime.toString());
          ps.setInt(3, capacity);
          ps.setInt(4, durationMinutes);
          ps.setTimestamp(5, now);
          ps.setTimestamp(6, now);
          return ps;
        },
        keyHolder);

    long slotId = keyHolder.getKey().longValue();
    // insert days_of_week element (stored in separate collection table)
    jdbcTemplate.update(
        "INSERT INTO class_slot_days (class_slot_id, day_of_week) VALUES (?, ?)",
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
                + " WHERE cs.marketplace_class_id = ?",
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
