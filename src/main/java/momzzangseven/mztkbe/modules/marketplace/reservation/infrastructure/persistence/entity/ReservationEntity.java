package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "class_reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReservationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "trainer_id", nullable = false)
  private Long trainerId;

  @Column(name = "class_slot_id", nullable = false)
  private Long slotId;

  @Column(name = "reservation_date", nullable = false)
  private LocalDate reservationDate;

  @Column(name = "reservation_time", nullable = false)
  private LocalTime reservationTime;

  @Column(name = "duration_minutes", nullable = false)
  private int durationMinutes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ReservationStatus status;

  @Column(name = "user_request", length = 500)
  private String userRequest;

  @Column(name = "order_id", length = 100)
  private String orderId;

  @Column(name = "tx_hash", length = 100)
  private String txHash;

  @Version
  @Column(nullable = false)
  private Long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public static ReservationEntity fromDomain(Reservation domain) {
    return ReservationEntity.builder()
        .id(domain.getId())
        .userId(domain.getUserId())
        .trainerId(domain.getTrainerId())
        .slotId(domain.getSlotId())
        .reservationDate(domain.getReservationDate())
        .reservationTime(domain.getReservationTime())
        .durationMinutes(domain.getDurationMinutes())
        .status(domain.getStatus())
        .userRequest(domain.getUserRequest())
        .orderId(domain.getOrderId())
        .txHash(domain.getTxHash())
        .version(domain.getVersion())
        .build();
  }

  public Reservation toDomain() {
    return Reservation.builder()
        .id(this.id)
        .userId(this.userId)
        .trainerId(this.trainerId)
        .slotId(this.slotId)
        .reservationDate(this.reservationDate)
        .reservationTime(this.reservationTime)
        .durationMinutes(this.durationMinutes)
        .status(this.status)
        .userRequest(this.userRequest)
        .orderId(this.orderId)
        .txHash(this.txHash)
        .version(this.version)
        .createdAt(this.createdAt)
        .updatedAt(this.updatedAt)
        .build();
  }
}
