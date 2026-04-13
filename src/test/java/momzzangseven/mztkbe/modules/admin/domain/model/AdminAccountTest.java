package momzzangseven.mztkbe.modules.admin.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AdminAccount 도메인 모델 단위 테스트")
class AdminAccountTest {

  private static final Long USER_ID = 1L;
  private static final String LOGIN_ID = "admin001";
  private static final String PASSWORD_HASH = "$2a$10$hashedValue";
  private static final Long CREATED_BY = 99L;

  private AdminAccount createDefaultAccount() {
    return AdminAccount.create(USER_ID, LOGIN_ID, PASSWORD_HASH, CREATED_BY);
  }

  // ---------------------------------------------------------------------------
  // create() factory method
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("create() 팩토리 메서드")
  class CreateFactory {

    @Test
    @DisplayName("[M-44] create with valid inputs returns correctly initialized AdminAccount")
    void create_validInputs_returnsCorrectlyInitializedAccount() {
      // given
      Instant before = Instant.now();

      // when
      AdminAccount account = AdminAccount.create(USER_ID, LOGIN_ID, PASSWORD_HASH, CREATED_BY);

      // then
      Instant after = Instant.now();

      assertThat(account.getUserId()).isEqualTo(USER_ID);
      assertThat(account.getLoginId()).isEqualTo(LOGIN_ID);
      assertThat(account.getPasswordHash()).isEqualTo(PASSWORD_HASH);
      assertThat(account.getCreatedBy()).isEqualTo(CREATED_BY);
      assertThat(account.getId()).isNull();
      assertThat(account.getLastLoginAt()).isNull();
      assertThat(account.getDeletedAt()).isNull();
      assertThat(account.getPasswordLastRotatedAt()).isNotNull();
      assertThat(account.getCreatedAt()).isNotNull();
      assertThat(account.getUpdatedAt()).isNotNull();
      assertThat(account.getCreatedAt()).isBetween(before, after);
      assertThat(account.getUpdatedAt()).isBetween(before, after);
      assertThat(account.getPasswordLastRotatedAt())
          .isCloseTo(account.getCreatedAt(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("[M-45] create with null createdBy succeeds (seed accounts)")
    void create_nullCreatedBy_succeeds() {
      // when
      AdminAccount account = AdminAccount.create(USER_ID, LOGIN_ID, PASSWORD_HASH, null);

      // then
      assertThat(account.getCreatedBy()).isNull();
      assertThat(account.getUserId()).isEqualTo(USER_ID);
      assertThat(account.getLoginId()).isEqualTo(LOGIN_ID);
      assertThat(account.getPasswordHash()).isEqualTo(PASSWORD_HASH);
      assertThat(account.getId()).isNull();
      assertThat(account.getLastLoginAt()).isNull();
      assertThat(account.getDeletedAt()).isNull();
      assertThat(account.getPasswordLastRotatedAt()).isNotNull();
      assertThat(account.getCreatedAt()).isNotNull();
      assertThat(account.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("[M-46] create with null userId throws IllegalArgumentException")
    void create_nullUserId_throwsException() {
      assertThatThrownBy(() -> AdminAccount.create(null, LOGIN_ID, PASSWORD_HASH, CREATED_BY))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("userId must not be null");
    }

    @Test
    @DisplayName("[M-47] create with null loginId throws IllegalArgumentException")
    void create_nullLoginId_throwsException() {
      assertThatThrownBy(() -> AdminAccount.create(USER_ID, null, PASSWORD_HASH, CREATED_BY))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("loginId must not be blank");
    }

    @Test
    @DisplayName("[M-48] create with blank loginId throws IllegalArgumentException")
    void create_blankLoginId_throwsException() {
      assertThatThrownBy(() -> AdminAccount.create(USER_ID, "   ", PASSWORD_HASH, CREATED_BY))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("loginId must not be blank");
    }

    @Test
    @DisplayName("[M-49] create with empty loginId throws IllegalArgumentException")
    void create_emptyLoginId_throwsException() {
      assertThatThrownBy(() -> AdminAccount.create(USER_ID, "", PASSWORD_HASH, CREATED_BY))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("loginId must not be blank");
    }

    @Test
    @DisplayName("[M-50] create with null passwordHash throws IllegalArgumentException")
    void create_nullPasswordHash_throwsException() {
      assertThatThrownBy(() -> AdminAccount.create(USER_ID, LOGIN_ID, null, CREATED_BY))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("passwordHash must not be blank");
    }

    @Test
    @DisplayName("[M-51] create with blank passwordHash throws IllegalArgumentException")
    void create_blankPasswordHash_throwsException() {
      assertThatThrownBy(() -> AdminAccount.create(USER_ID, LOGIN_ID, "   ", CREATED_BY))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("passwordHash must not be blank");
    }

    @Test
    @DisplayName("[M-52] create with empty passwordHash throws IllegalArgumentException")
    void create_emptyPasswordHash_throwsException() {
      assertThatThrownBy(() -> AdminAccount.create(USER_ID, LOGIN_ID, "", CREATED_BY))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("passwordHash must not be blank");
    }

    @Test
    @DisplayName("[M-53] create sets passwordLastRotatedAt equal to createdAt")
    void create_setsPasswordLastRotatedAtEqualToCreatedAt() {
      // when
      AdminAccount account = createDefaultAccount();

      // then
      assertThat(account.getPasswordLastRotatedAt())
          .isCloseTo(account.getCreatedAt(), within(1, ChronoUnit.SECONDS));
    }
  }

  // ---------------------------------------------------------------------------
  // updateLastLogin()
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("updateLastLogin() 메서드")
  class UpdateLastLogin {

    @Test
    @DisplayName("[M-54] updateLastLogin sets lastLoginAt and updatedAt")
    void updateLastLogin_setsLastLoginAtAndUpdatedAt() {
      // given
      AdminAccount original = createDefaultAccount();

      // when
      AdminAccount updated = original.updateLastLogin();

      // then
      assertThat(updated).isNotSameAs(original);
      assertThat(updated.getLastLoginAt()).isNotNull();
      assertThat(updated.getLastLoginAt()).isAfterOrEqualTo(original.getCreatedAt());
      assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updated.getLastLoginAt());

      // all other fields unchanged
      assertThat(updated.getUserId()).isEqualTo(original.getUserId());
      assertThat(updated.getLoginId()).isEqualTo(original.getLoginId());
      assertThat(updated.getPasswordHash()).isEqualTo(original.getPasswordHash());
      assertThat(updated.getCreatedBy()).isEqualTo(original.getCreatedBy());
      assertThat(updated.getDeletedAt()).isEqualTo(original.getDeletedAt());
    }

    @Test
    @DisplayName("[M-55] updateLastLogin preserves immutability of original object")
    void updateLastLogin_preservesImmutability() {
      // given
      AdminAccount original = createDefaultAccount();
      Instant originalLastLoginAt = original.getLastLoginAt();

      // when
      AdminAccount updated = original.updateLastLogin();

      // then
      assertThat(original.getLastLoginAt()).isEqualTo(originalLastLoginAt);
      assertThat(original.getLastLoginAt()).isNull();
      assertThat(updated.getLastLoginAt()).isNotNull();
    }
  }

  // ---------------------------------------------------------------------------
  // rotatePassword()
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("rotatePassword() 메서드")
  class RotatePassword {

    private static final String NEW_HASH = "$2a$10$newHashedValue";

    @Test
    @DisplayName("[M-56] rotatePassword with valid hash updates passwordHash and timestamps")
    void rotatePassword_validHash_updatesPasswordHashAndTimestamps() {
      // given
      AdminAccount original = createDefaultAccount();

      // when
      AdminAccount rotated = original.rotatePassword(NEW_HASH);

      // then
      assertThat(rotated.getPasswordHash()).isEqualTo(NEW_HASH);
      assertThat(rotated.getPasswordLastRotatedAt())
          .isAfterOrEqualTo(original.getPasswordLastRotatedAt());
      assertThat(rotated.getUpdatedAt()).isAfterOrEqualTo(original.getUpdatedAt());

      // all other fields unchanged
      assertThat(rotated.getUserId()).isEqualTo(original.getUserId());
      assertThat(rotated.getLoginId()).isEqualTo(original.getLoginId());
      assertThat(rotated.getCreatedBy()).isEqualTo(original.getCreatedBy());
      assertThat(rotated.getDeletedAt()).isEqualTo(original.getDeletedAt());
      assertThat(rotated.getLastLoginAt()).isEqualTo(original.getLastLoginAt());
    }

    @Test
    @DisplayName("[M-57] rotatePassword preserves immutability of original object")
    void rotatePassword_preservesImmutability() {
      // given
      AdminAccount original = createDefaultAccount();
      String originalHash = original.getPasswordHash();

      // when
      AdminAccount rotated = original.rotatePassword(NEW_HASH);

      // then
      assertThat(original.getPasswordHash()).isEqualTo(originalHash);
      assertThat(rotated.getPasswordHash()).isEqualTo(NEW_HASH);
    }

    @Test
    @DisplayName("[M-58] rotatePassword with null newHash throws IllegalArgumentException")
    void rotatePassword_nullNewHash_throwsException() {
      // given
      AdminAccount account = createDefaultAccount();

      // then
      assertThatThrownBy(() -> account.rotatePassword(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("newHash must not be blank");
    }

    @Test
    @DisplayName("[M-59] rotatePassword with blank newHash throws IllegalArgumentException")
    void rotatePassword_blankNewHash_throwsException() {
      // given
      AdminAccount account = createDefaultAccount();

      // then
      assertThatThrownBy(() -> account.rotatePassword("   "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("newHash must not be blank");
    }

    @Test
    @DisplayName("[M-60] rotatePassword with empty newHash throws IllegalArgumentException")
    void rotatePassword_emptyNewHash_throwsException() {
      // given
      AdminAccount account = createDefaultAccount();

      // then
      assertThatThrownBy(() -> account.rotatePassword(""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("newHash must not be blank");
    }
  }

  // ---------------------------------------------------------------------------
  // softDelete()
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("softDelete() 메서드")
  class SoftDelete {

    @Test
    @DisplayName("[M-61] softDelete on active account sets deletedAt and updatedAt")
    void softDelete_activeAccount_setsDeletedAtAndUpdatedAt() {
      // given
      AdminAccount active = createDefaultAccount();

      // when
      AdminAccount deleted = active.softDelete();

      // then
      assertThat(deleted.getDeletedAt()).isNotNull();
      assertThat(deleted.getDeletedAt()).isAfterOrEqualTo(active.getCreatedAt());
      assertThat(deleted.getUpdatedAt()).isAfterOrEqualTo(deleted.getDeletedAt());
      assertThat(deleted.isActive()).isFalse();

      // all other fields unchanged
      assertThat(deleted.getUserId()).isEqualTo(active.getUserId());
      assertThat(deleted.getLoginId()).isEqualTo(active.getLoginId());
      assertThat(deleted.getPasswordHash()).isEqualTo(active.getPasswordHash());
      assertThat(deleted.getCreatedBy()).isEqualTo(active.getCreatedBy());
    }

    @Test
    @DisplayName("[M-62] softDelete preserves immutability of original object")
    void softDelete_preservesImmutability() {
      // given
      AdminAccount original = createDefaultAccount();

      // when
      AdminAccount deleted = original.softDelete();

      // then
      assertThat(original.getDeletedAt()).isNull();
      assertThat(original.isActive()).isTrue();
      assertThat(deleted.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("[M-63] softDelete on already-deleted account throws IllegalStateException")
    void softDelete_alreadyDeleted_throwsException() {
      // given
      AdminAccount deleted = createDefaultAccount().softDelete();

      // then
      assertThatThrownBy(deleted::softDelete)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Account is already deleted");
    }
  }

  // ---------------------------------------------------------------------------
  // isActive()
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("isActive() 메서드")
  class IsActive {

    @Test
    @DisplayName("[M-64] isActive returns true when deletedAt is null")
    void isActive_deletedAtNull_returnsTrue() {
      // given
      AdminAccount account = createDefaultAccount();

      // then
      assertThat(account.isActive()).isTrue();
    }

    @Test
    @DisplayName("[M-65] isActive returns false when deletedAt is non-null (via softDelete)")
    void isActive_afterSoftDelete_returnsFalse() {
      // given
      AdminAccount deleted = createDefaultAccount().softDelete();

      // then
      assertThat(deleted.isActive()).isFalse();
    }

    @Test
    @DisplayName("[M-66] isActive returns false when account is built with explicit deletedAt")
    void isActive_builtWithExplicitDeletedAt_returnsFalse() {
      // given
      AdminAccount account =
          AdminAccount.builder()
              .userId(USER_ID)
              .loginId(LOGIN_ID)
              .passwordHash(PASSWORD_HASH)
              .createdBy(CREATED_BY)
              .deletedAt(Instant.now())
              .createdAt(Instant.now())
              .updatedAt(Instant.now())
              .build();

      // then
      assertThat(account.isActive()).isFalse();
    }
  }
}
