package momzzangseven.mztkbe.modules.account.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.token.RefreshTokenNotFoundException;
import momzzangseven.mztkbe.modules.account.domain.model.RefreshToken;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.entity.RefreshTokenEntity;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.repository.RefreshTokenJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenPersistenceAdapter 단위 테스트")
class RefreshTokenPersistenceAdapterTest {

  @Mock private RefreshTokenJpaRepository repository;

  @InjectMocks private RefreshTokenPersistenceAdapter adapter;

  private static final Long USER_ID = 1L;
  private static final String TOKEN_VALUE = "eyJhbGciOiJIUzI1NiJ9.validTokenForPersistence12345";

  // SHA-256("eyJhbGciOiJIUzI1NiJ9.validTokenForPersistence12345")
  // 미리 계산하지 않고, 어댑터의 동작(hash 후 조회)만 검증
  private RefreshToken createDomainToken() {
    Instant now = Instant.now();
    return RefreshToken.create(USER_ID, TOKEN_VALUE, now.plus(Duration.ofDays(1)), now);
  }

  private RefreshTokenEntity createEntityWithId(Long id) {
    Instant now = Instant.now();
    return RefreshTokenEntity.builder()
        .id(id)
        .userId(USER_ID)
        .tokenHash("any-hash-value")
        .expiresAt(now.plus(Duration.ofDays(1)))
        .createdAt(now)
        .build();
  }

  // ============================================
  // findByTokenValue()
  // ============================================

  @Nested
  @DisplayName("findByTokenValue()")
  class FindByTokenValueTest {

    @Test
    @DisplayName("토큰이 존재하면 도메인 모델 반환 (원본 tokenValue 복원)")
    void findByTokenValue_Found_ReturnsDomainWithOriginalValue() {
      RefreshTokenEntity entity = createEntityWithId(10L);
      given(repository.findByTokenHash(anyString())).willReturn(Optional.of(entity));

      Optional<RefreshToken> result = adapter.findByTokenValue(TOKEN_VALUE);

      assertThat(result).isPresent();
      assertThat(result.get().getTokenValue()).isEqualTo(TOKEN_VALUE);
      assertThat(result.get().getId()).isEqualTo(10L);
      assertThat(result.get().getUserId()).isEqualTo(USER_ID);
      verify(repository, times(1)).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("토큰이 없으면 Optional.empty() 반환")
    void findByTokenValue_NotFound_ReturnsEmpty() {
      given(repository.findByTokenHash(anyString())).willReturn(Optional.empty());

      Optional<RefreshToken> result = adapter.findByTokenValue(TOKEN_VALUE);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("동일 토큰 값은 동일한 해시로 조회 (결정론적 해싱)")
    void findByTokenValue_SameInput_UsesSameHash() {
      given(repository.findByTokenHash(anyString())).willReturn(Optional.empty());

      adapter.findByTokenValue(TOKEN_VALUE);
      adapter.findByTokenValue(TOKEN_VALUE);

      ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
      verify(repository, times(2)).findByTokenHash(hashCaptor.capture());

      List<String> capturedHashes = hashCaptor.getAllValues();
      assertThat(capturedHashes.get(0)).isEqualTo(capturedHashes.get(1));
    }

    @Test
    @DisplayName("해시는 64자 16진수 문자열 (SHA-256 출력)")
    void findByTokenValue_GeneratesValidSha256Hash() {
      given(repository.findByTokenHash(anyString())).willReturn(Optional.empty());

      adapter.findByTokenValue(TOKEN_VALUE);

      ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
      verify(repository).findByTokenHash(hashCaptor.capture());

      String hash = hashCaptor.getValue();
      assertThat(hash).hasSize(64);
      assertThat(hash).matches("[0-9a-f]{64}");
    }
  }

  // ============================================
  // findByTokenValueWithLock()
  // ============================================

  @Nested
  @DisplayName("findByTokenValueWithLock()")
  class FindByTokenValueWithLockTest {

    @Test
    @DisplayName("락 버전 조회 시 findByTokenHashWithLock 호출")
    void findByTokenValueWithLock_Found_UsesLockQuery() {
      RefreshTokenEntity entity = createEntityWithId(10L);
      given(repository.findByTokenHashWithLock(anyString())).willReturn(Optional.of(entity));

      Optional<RefreshToken> result = adapter.findByTokenValueWithLock(TOKEN_VALUE);

      assertThat(result).isPresent();
      assertThat(result.get().getTokenValue()).isEqualTo(TOKEN_VALUE);
      verify(repository, times(1)).findByTokenHashWithLock(anyString());
      verify(repository, never()).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("락 버전 조회 시 토큰 없으면 Optional.empty() 반환")
    void findByTokenValueWithLock_NotFound_ReturnsEmpty() {
      given(repository.findByTokenHashWithLock(anyString())).willReturn(Optional.empty());

      Optional<RefreshToken> result = adapter.findByTokenValueWithLock(TOKEN_VALUE);

      assertThat(result).isEmpty();
    }
  }

  // ============================================
  // existsByTokenValue()
  // ============================================

  @Nested
  @DisplayName("existsByTokenValue()")
  class ExistsByTokenValueTest {

    @Test
    @DisplayName("토큰 존재 시 true 반환")
    void existsByTokenValue_Exists_ReturnsTrue() {
      given(repository.existsByTokenHash(anyString())).willReturn(true);

      boolean result = adapter.existsByTokenValue(TOKEN_VALUE);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("토큰 미존재 시 false 반환")
    void existsByTokenValue_NotExists_ReturnsFalse() {
      given(repository.existsByTokenHash(anyString())).willReturn(false);

      boolean result = adapter.existsByTokenValue(TOKEN_VALUE);

      assertThat(result).isFalse();
    }
  }

  // ============================================
  // save() - CREATE (id == null)
  // ============================================

  @Nested
  @DisplayName("save() - 신규 토큰 저장 (id == null)")
  class SaveNewTokenTest {

    @Test
    @DisplayName("id가 null인 토큰은 신규 엔티티 생성 후 저장")
    void save_NewToken_CreatesNewEntity() {
      RefreshToken newToken = createDomainToken(); // id == null
      RefreshTokenEntity savedEntity = createEntityWithId(99L);

      given(repository.save(any(RefreshTokenEntity.class))).willReturn(savedEntity);

      RefreshToken result = adapter.save(newToken);

      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(99L);
      assertThat(result.getTokenValue()).isEqualTo(TOKEN_VALUE);
      verify(repository, never()).findById(any());
      verify(repository, times(1)).save(any(RefreshTokenEntity.class));
    }

    @Test
    @DisplayName("저장 시 tokenHash(SHA-256)를 사용하여 엔티티 생성")
    void save_NewToken_StoresHashNotPlainValue() {
      RefreshToken newToken = createDomainToken();
      RefreshTokenEntity savedEntity = createEntityWithId(99L);

      given(repository.save(any(RefreshTokenEntity.class))).willReturn(savedEntity);

      adapter.save(newToken);

      ArgumentCaptor<RefreshTokenEntity> entityCaptor =
          ArgumentCaptor.forClass(RefreshTokenEntity.class);
      verify(repository).save(entityCaptor.capture());

      RefreshTokenEntity captured = entityCaptor.getValue();
      assertThat(captured.getTokenHash()).isNotEqualTo(TOKEN_VALUE);
      assertThat(captured.getTokenHash()).hasSize(64);
      assertThat(captured.getTokenHash()).matches("[0-9a-f]{64}");
    }
  }

  // ============================================
  // save() - UPDATE (id != null)
  // ============================================

  @Nested
  @DisplayName("save() - 기존 토큰 업데이트 (id != null)")
  class SaveExistingTokenTest {

    @Test
    @DisplayName("id가 있는 토큰은 기존 엔티티 조회 후 업데이트")
    void save_ExistingToken_UpdatesEntity() {
      Instant now = Instant.now();
      RefreshToken tokenWithId =
          RefreshToken.builder()
              .id(5L)
              .userId(USER_ID)
              .tokenValue(TOKEN_VALUE)
              .expiresAt(now.plus(Duration.ofDays(1)))
              .createdAt(now)
              .build();

      RefreshTokenEntity existingEntity = createEntityWithId(5L);
      given(repository.findById(5L)).willReturn(Optional.of(existingEntity));
      given(repository.save(any(RefreshTokenEntity.class))).willReturn(existingEntity);

      RefreshToken result = adapter.save(tokenWithId);

      assertThat(result).isNotNull();
      verify(repository, times(1)).findById(5L);
      verify(repository, times(1)).save(existingEntity);
    }

    @Test
    @DisplayName("업데이트 시 id로 조회된 엔티티가 없으면 RefreshTokenNotFoundException 발생")
    void save_ExistingToken_NotFoundById_ThrowsException() {
      Instant now = Instant.now();
      RefreshToken tokenWithId =
          RefreshToken.builder()
              .id(999L)
              .userId(USER_ID)
              .tokenValue(TOKEN_VALUE)
              .expiresAt(now.plus(Duration.ofDays(1)))
              .createdAt(now)
              .build();

      given(repository.findById(999L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> adapter.save(tokenWithId))
          .isInstanceOf(RefreshTokenNotFoundException.class);

      verify(repository, never()).save(any());
    }
  }

  // ============================================
  // deleteByUserId()
  // ============================================

  @Nested
  @DisplayName("deleteByUserId()")
  class DeleteByUserIdTest {

    @Test
    @DisplayName("userId로 토큰 전체 삭제")
    void deleteByUserId_DelegatestoRepository() {
      willDoNothing().given(repository).deleteByUserId(USER_ID);

      adapter.deleteByUserId(USER_ID);

      verify(repository, times(1)).deleteByUserId(USER_ID);
    }
  }

  // ============================================
  // deleteById()
  // ============================================

  @Nested
  @DisplayName("deleteById()")
  class DeleteByIdTest {

    @Test
    @DisplayName("존재하는 id 삭제 성공")
    void deleteById_ExistingId_Deletes() {
      Long tokenId = 10L;
      given(repository.existsById(tokenId)).willReturn(true);
      willDoNothing().given(repository).deleteById(tokenId);

      adapter.deleteById(tokenId);

      verify(repository, times(1)).existsById(tokenId);
      verify(repository, times(1)).deleteById(tokenId);
    }

    @Test
    @DisplayName("id가 null이면 IllegalArgumentException 발생")
    void deleteById_NullId_ThrowsIllegalArgumentException() {
      assertThatThrownBy(() -> adapter.deleteById(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("refreshTokenId must not be null");

      verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("존재하지 않는 id이면 RefreshTokenNotFoundException 발생")
    void deleteById_NotFound_ThrowsRefreshTokenNotFoundException() {
      Long tokenId = 999L;
      given(repository.existsById(tokenId)).willReturn(false);

      assertThatThrownBy(() -> adapter.deleteById(tokenId))
          .isInstanceOf(RefreshTokenNotFoundException.class);

      verify(repository, never()).deleteById(any());
    }
  }

  // ============================================
  // deleteByUserIdIn()
  // ============================================

  @Nested
  @DisplayName("deleteByUserIdIn()")
  class DeleteByUserIdInTest {

    @Test
    @DisplayName("유효한 userId 목록으로 삭제 위임")
    void deleteByUserIdIn_ValidList_DelegatesToRepository() {
      List<Long> userIds = List.of(1L, 2L, 3L);
      given(repository.deleteByUserIdIn(userIds)).willReturn(3);

      adapter.deleteByUserIdIn(userIds);

      verify(repository, times(1)).deleteByUserIdIn(userIds);
    }

    @Test
    @DisplayName("null 목록이면 repository 호출 없이 조용히 종료")
    void deleteByUserIdIn_NullList_NoRepositoryCall() {
      adapter.deleteByUserIdIn(null);

      verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("빈 목록이면 repository 호출 없이 조용히 종료")
    void deleteByUserIdIn_EmptyList_NoRepositoryCall() {
      adapter.deleteByUserIdIn(List.of());

      verifyNoInteractions(repository);
    }
  }
}
