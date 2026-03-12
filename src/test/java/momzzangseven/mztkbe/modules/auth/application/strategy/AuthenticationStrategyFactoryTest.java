package momzzangseven.mztkbe.modules.auth.application.strategy;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.List;
import momzzangseven.mztkbe.global.error.UnsupportedProviderException;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationStrategyFactory 단위 테스트")
class AuthenticationStrategyFactoryTest {

  @Mock private AuthenticationStrategy localStrategy;
  @Mock private AuthenticationStrategy kakaoStrategy;
  @Mock private AuthenticationStrategy googleStrategy;

  private AuthenticationStrategyFactory factory;

  @BeforeEach
  void setUp() {
    given(localStrategy.supports()).willReturn(AuthProvider.LOCAL);
    given(kakaoStrategy.supports()).willReturn(AuthProvider.KAKAO);
    given(googleStrategy.supports()).willReturn(AuthProvider.GOOGLE);

    factory =
        new AuthenticationStrategyFactory(List.of(localStrategy, kakaoStrategy, googleStrategy));
  }

  // ============================================
  // 정상 전략 조회
  // ============================================

  @Nested
  @DisplayName("getStrategy() - 정상 조회")
  class GetStrategySuccessTest {

    @Test
    @DisplayName("LOCAL provider로 LocalAuthenticationStrategy 반환")
    void getStrategy_Local_ReturnsLocalStrategy() {
      AuthenticationStrategy result = factory.getStrategy(AuthProvider.LOCAL);

      assertThat(result).isSameAs(localStrategy);
    }

    @Test
    @DisplayName("KAKAO provider로 KakaoAuthenticationStrategy 반환")
    void getStrategy_Kakao_ReturnsKakaoStrategy() {
      AuthenticationStrategy result = factory.getStrategy(AuthProvider.KAKAO);

      assertThat(result).isSameAs(kakaoStrategy);
    }

    @Test
    @DisplayName("GOOGLE provider로 GoogleAuthenticationStrategy 반환")
    void getStrategy_Google_ReturnsGoogleStrategy() {
      AuthenticationStrategy result = factory.getStrategy(AuthProvider.GOOGLE);

      assertThat(result).isSameAs(googleStrategy);
    }
  }

  // ============================================
  // 미등록 전략 조회 시 예외
  // ============================================

  @Nested
  @DisplayName("getStrategy() - 미등록 provider 예외")
  class GetStrategyFailureTest {

    @Test
    @DisplayName("전략이 등록되지 않은 provider 요청 시 UnsupportedProviderException 발생")
    void getStrategy_UnregisteredProvider_ThrowsException() {
      // LOCAL만 등록된 팩토리
      given(localStrategy.supports()).willReturn(AuthProvider.LOCAL);
      AuthenticationStrategyFactory partialFactory =
          new AuthenticationStrategyFactory(List.of(localStrategy));

      assertThatThrownBy(() -> partialFactory.getStrategy(AuthProvider.KAKAO))
          .isInstanceOf(UnsupportedProviderException.class);
    }

    @Test
    @DisplayName("빈 전략 목록으로 생성 후 조회 시 예외 발생")
    void getStrategy_EmptyStrategies_ThrowsException() {
      AuthenticationStrategyFactory emptyFactory = new AuthenticationStrategyFactory(List.of());

      assertThatThrownBy(() -> emptyFactory.getStrategy(AuthProvider.LOCAL))
          .isInstanceOf(UnsupportedProviderException.class);
    }
  }

  // ============================================
  // 중복 전략 등록 시 덮어쓰기
  // ============================================

  @Nested
  @DisplayName("생성자 - 전략 등록")
  class ConstructorTest {

    @Test
    @DisplayName("동일 provider에 전략이 중복 등록되면 마지막 전략이 사용됨")
    void constructor_DuplicateProvider_LastStrategyWins() {
      AuthenticationStrategy anotherLocalStrategy = mock(AuthenticationStrategy.class);
      given(anotherLocalStrategy.supports()).willReturn(AuthProvider.LOCAL);

      AuthenticationStrategyFactory factoryWithDuplicate =
          new AuthenticationStrategyFactory(List.of(localStrategy, anotherLocalStrategy));

      AuthenticationStrategy result = factoryWithDuplicate.getStrategy(AuthProvider.LOCAL);

      assertThat(result).isSameAs(anotherLocalStrategy);
    }
  }
}
