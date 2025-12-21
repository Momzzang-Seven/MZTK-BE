package momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.domain.model.UserStatus;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * JPA Entity for User table.
 *
 * <p>Infrastructure Layer: - This is a PERSISTENCE MODEL, not a DOMAIN MODEL - Contains JPA
 * annotations and database-specific concerns - Should be converted to Domain Model (User) when
 * crossing layer boundaries
 */
@Entity
@Table(
    name = "users",
    indexes = {
      @Index(name = "idx_email", columnList = "email"),
      @Index(name = "idx_kakao_id", columnList = "kakao_id"),
      @Index(name = "idx_google_id", columnList = "google_id"),
      @Index(name = "idx_wallet_address", columnList = "wallet_address")
    },
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_email", columnNames = "email"),
      @UniqueConstraint(
          name = "uk_provider_user",
          columnNames = {"provider", "provider_user_id"}),
      @UniqueConstraint(name = "uk_wallet_address", columnNames = "wallet_address")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "email", length = 255)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false, length = 20)
  private AuthProvider provider;

  @Column(name = "provider_user_id", unique = true)
  private String providerUserId;

  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  @Column(name = "nickname", nullable = false, length = 100)
  private String nickname;

  @Column(name = "profile_image_url", length = 500)
  private String profileImageUrl;

  @Column(name = "wallet_address", length = 255, unique = true)
  private String walletAddress;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private UserStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private UserRole role;

  @Column(name = "last_login_at")
  private LocalDateTime lastLoginAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
