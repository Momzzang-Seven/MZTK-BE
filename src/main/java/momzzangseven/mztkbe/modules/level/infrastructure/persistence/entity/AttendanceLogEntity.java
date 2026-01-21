package momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
        name = "attendance_logs",
        indexes = {@Index(name = "idx_attendance_user_date", columnList = "user_id, attended_date DESC")},
        uniqueConstraints = {@UniqueConstraint(name = "uk_attendance_user_date", columnNames = {"user_id", "attended_date"})})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AttendanceLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "attended_date", nullable = false)
    private LocalDate attendedDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
