package momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;

@Entity
@Table(
    name = "users",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"provider", "provider_user_id"}),
      @UniqueConstraint(columnNames = {"email"})
    })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 로그인 제공자 (LOCAL / KAKAO / GOOGLE)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AuthProvider provider;

  // 소셜 로그인 제공자 고유 ID
  @Column(name = "provider_user_id", nullable = false)
  private String providerUserId;

  // 계정 식별자 (UNIQUE)
  @Column(nullable = false, unique = true)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserRole role;

  private String nickname;

  @Column(name = "profile_image_url")
  private String profileImageUrl;

  // 지갑 주소
  @Column(name = "wallet_address", unique = true, length = 42)
  private String walletAddress;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Column(name = "last_login_at")
  private LocalDateTime lastLoginAt;

  @PrePersist
  protected void onCreate() {
    this.createdAt = LocalDateTime.now();
    this.updatedAt = this.createdAt;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }

  @Column(name = "password_hash")
  private String passwordHash;

  //  public momzzangseven.mztkbe.modules.user.domain.model.User toDomain() {
  //    return momzzangseven.mztkbe.modules.user.domain.model.User.builder()
  //        .id(this.id)
  //        .provider(this.provider)
  //        .providerUserId(this.providerUserId)
  //        .email(this.email)
  //        .nickname(this.nickname)
  //        .role(this.role)
  //        .profileImageUrl(this.profileImageUrl)
  //        .walletAddress(this.walletAddress)
  //        .passwordHash(this.passwordHash)
  //        .createdAt(this.createdAt)
  //        .updatedAt(this.updatedAt)
  //        .build();
  //  }

  //  public static UserEntity fromDomain(momzzangseven.mztkbe.modules.user.domain.model.User user)
  // {
  //    return UserEntity.builder()
  //        .id(user.getId())
  //        .provider(user.getProvider())
  //        .providerUserId(user.getProviderUserId())
  //        .email(user.getEmail())
  //        .nickname(user.getNickname())
  //        .role(user.getRole())
  //        .profileImageUrl(user.getProfileImageUrl())
  //        .walletAddress(user.getWalletAddress())
  //        .passwordHash(user.getPasswordHash())
  //        .build();
  //  }
}
