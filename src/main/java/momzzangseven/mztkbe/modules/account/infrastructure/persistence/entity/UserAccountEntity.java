package momzzangseven.mztkbe.modules.account.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.hibernate.annotations.ColumnDefault;

/** JPA entity for the {@code users_account} table. */
@Entity
@Table(name = "users_account")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserAccountEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, unique = true)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AuthProvider provider;

  @Column(name = "provider_user_id")
  private String providerUserId;

  @Column(name = "password_hash")
  private String passwordHash;

  @Column(name = "google_refresh_token", length = 2048)
  private String googleRefreshToken;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @ColumnDefault("'ACTIVE'")
  private AccountStatus status;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
    if (this.status == null) {
      this.status = AccountStatus.ACTIVE;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
