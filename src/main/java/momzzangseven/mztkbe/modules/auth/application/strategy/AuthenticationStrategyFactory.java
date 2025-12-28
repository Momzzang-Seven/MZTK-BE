package momzzangseven.mztkbe.modules.auth.application.strategy;

import momzzangseven.mztkbe.global.error.UnsupportedProviderException;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Role: Create instance of AuthenticationStrategy Responsibility: create Strategy instance
 * according to the provider.
 */
@Component
public class AuthenticationStrategyFactory {

  private final Map<AuthProvider, AuthenticationStrategy> strategies =
      new EnumMap<>(AuthProvider.class);

  /**
   * Register available strategies.
   *
   * @param strategyList List of available strategy beans
   */
  public AuthenticationStrategyFactory(List<AuthenticationStrategy> strategyList) {
    for (AuthenticationStrategy s : strategyList) {
      strategies.put(s.supports(), s);
    }
  }

  /**
   * Return strategy for the requested provider.
   *
   * @param provider Authentication provider
   * @return Matching strategy
   * @throws UnsupportedProviderException if no strategy is registered
   */
  public AuthenticationStrategy getStrategy(AuthProvider provider) {
    AuthenticationStrategy strategy = strategies.get(provider);
    if (strategy == null) {
      throw new UnsupportedProviderException(provider);
    }
    return strategy;
  }
}
