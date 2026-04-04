package momzzangseven.mztkbe.modules.account.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("UserAccount domain model unit test")
class UserAccountTest {

  // ============================================================
  // Factory Methods
  // ============================================================

  @Nested
  @DisplayName("createLocal")
  class CreateLocal {

    @Test
    @DisplayName("sets LOCAL provider, ACTIVE status, and hashed password")
    void setsLocalProviderAndActiveStatus() {
      UserAccount account = UserAccount.createLocal(1L, "$2a$hash");

      assertThat(account.getUserId()).isEqualTo(1L);
      assertThat(account.getProvider()).isEqualTo(AuthProvider.LOCAL);
      assertThat(account.getPasswordHash()).isEqualTo("$2a$hash");
      assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
      assertThat(account.getId()).isNull();
      assertThat(account.getCreatedAt()).isNotNull();
      assertThat(account.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("providerUserId is null for local accounts")
    void providerUserIdIsNull() {
      UserAccount account = UserAccount.createLocal(1L, "$2a$hash");

      assertThat(account.getProviderUserId()).isNull();
    }
  }

  @Nested
  @DisplayName("createSocial")
  class CreateSocial {

    @Test
    @DisplayName("sets given provider and providerUserId with ACTIVE status")
    void setsSocialProviderAndActiveStatus() {
      UserAccount account = UserAccount.createSocial(2L, AuthProvider.KAKAO, "kakao-123");

      assertThat(account.getUserId()).isEqualTo(2L);
      assertThat(account.getProvider()).isEqualTo(AuthProvider.KAKAO);
      assertThat(account.getProviderUserId()).isEqualTo("kakao-123");
      assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
      assertThat(account.getPasswordHash()).isNull();
      assertThat(account.getCreatedAt()).isNotNull();
      assertThat(account.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("creates GOOGLE social account correctly")
    void createsGoogleAccountCorrectly() {
      UserAccount account = UserAccount.createSocial(3L, AuthProvider.GOOGLE, "google-456");

      assertThat(account.getProvider()).isEqualTo(AuthProvider.GOOGLE);
      assertThat(account.getProviderUserId()).isEqualTo("google-456");
    }
  }

  // ============================================================
  // Business Logic
  // ============================================================

  @Nested
  @DisplayName("updateLastLogin")
  class UpdateLastLogin {

    @Test
    @DisplayName("sets lastLoginAt and bumps updatedAt")
    void setsLastLoginAtAndUpdatedAt() {
      UserAccount original = UserAccount.createLocal(1L, "hash");
      Instant before = Instant.now();

      UserAccount updated = original.updateLastLogin();

      assertThat(updated.getLastLoginAt()).isNotNull();
      assertThat(updated.getLastLoginAt()).isAfterOrEqualTo(before);
      assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("is immutable — original is not modified")
    void isImmutable() {
      UserAccount original = UserAccount.createLocal(1L, "hash");

      original.updateLastLogin();

      assertThat(original.getLastLoginAt()).isNull();
    }
  }

  @Nested
  @DisplayName("updateGoogleRefreshToken")
  class UpdateGoogleRefreshToken {

    @Test
    @DisplayName("stores encrypted token and bumps updatedAt")
    void storesEncryptedToken() {
      UserAccount account = UserAccount.createSocial(1L, AuthProvider.GOOGLE, "google-id");
      Instant before = Instant.now();

      UserAccount updated = account.updateGoogleRefreshToken("encrypted-token");

      assertThat(updated.getGoogleRefreshToken()).isEqualTo("encrypted-token");
      assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("is immutable — original token is unchanged")
    void isImmutable() {
      UserAccount account = account().googleRefreshToken("old-token").build();

      account.updateGoogleRefreshToken("new-token");

      assertThat(account.getGoogleRefreshToken()).isEqualTo("old-token");
    }
  }

  @Nested
  @DisplayName("withdraw")
  class Withdraw {

    @Test
    @DisplayName("sets status to DELETED and records deletedAt")
    void setsDeletedStatusAndTimestamp() {
      UserAccount active = UserAccount.createLocal(1L, "hash");
      Instant before = Instant.now();

      UserAccount withdrawn = active.withdraw();

      assertThat(withdrawn.getStatus()).isEqualTo(AccountStatus.DELETED);
      assertThat(withdrawn.getDeletedAt()).isNotNull();
      assertThat(withdrawn.getDeletedAt()).isAfterOrEqualTo(before);
      assertThat(withdrawn.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("is immutable — original remains ACTIVE")
    void isImmutable() {
      UserAccount active = UserAccount.createLocal(1L, "hash");

      active.withdraw();

      assertThat(active.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }
  }

  @Nested
  @DisplayName("reactivate")
  class Reactivate {

    @Test
    @DisplayName("sets status to ACTIVE, clears deletedAt, and sets lastLoginAt")
    void setsActiveStatusAndClearsDeletedAt() {
      UserAccount deleted =
          account()
              .status(AccountStatus.DELETED)
              .deletedAt(Instant.now().minusSeconds(100))
              .build();
      Instant before = Instant.now();

      UserAccount reactivated = deleted.reactivate();

      assertThat(reactivated.getStatus()).isEqualTo(AccountStatus.ACTIVE);
      assertThat(reactivated.getDeletedAt()).isNull();
      assertThat(reactivated.getLastLoginAt()).isNotNull();
      assertThat(reactivated.getLastLoginAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("is immutable — original remains DELETED")
    void isImmutable() {
      UserAccount deleted =
          account().status(AccountStatus.DELETED).deletedAt(Instant.now()).build();

      deleted.reactivate();

      assertThat(deleted.getStatus()).isEqualTo(AccountStatus.DELETED);
    }
  }

  // ============================================================
  // Status Checks
  // ============================================================

  @Nested
  @DisplayName("status predicates")
  class StatusPredicates {

    @Test
    @DisplayName("isActive returns true only for ACTIVE status")
    void isActive() {
      assertThat(account().status(AccountStatus.ACTIVE).build().isActive()).isTrue();
      assertThat(account().status(AccountStatus.DELETED).build().isActive()).isFalse();
      assertThat(account().status(AccountStatus.BLOCKED).build().isActive()).isFalse();
      assertThat(account().status(AccountStatus.UNVERIFIED).build().isActive()).isFalse();
    }

    @Test
    @DisplayName("isDeleted returns true only for DELETED status")
    void isDeleted() {
      assertThat(account().status(AccountStatus.DELETED).build().isDeleted()).isTrue();
      assertThat(account().status(AccountStatus.ACTIVE).build().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("isBlocked returns true only for BLOCKED status")
    void isBlocked() {
      assertThat(account().status(AccountStatus.BLOCKED).build().isBlocked()).isTrue();
      assertThat(account().status(AccountStatus.ACTIVE).build().isBlocked()).isFalse();
    }

    @Test
    @DisplayName("isUnverified returns true only for UNVERIFIED status")
    void isUnverified() {
      assertThat(account().status(AccountStatus.UNVERIFIED).build().isUnverified()).isTrue();
      assertThat(account().status(AccountStatus.ACTIVE).build().isUnverified()).isFalse();
    }
  }

  // ============================================================
  // Helpers
  // ============================================================

  private UserAccount.UserAccountBuilder account() {
    Instant now = Instant.now();
    return UserAccount.builder()
        .userId(1L)
        .provider(AuthProvider.LOCAL)
        .passwordHash("$2a$hash")
        .status(AccountStatus.ACTIVE)
        .createdAt(now)
        .updatedAt(now);
  }
}
