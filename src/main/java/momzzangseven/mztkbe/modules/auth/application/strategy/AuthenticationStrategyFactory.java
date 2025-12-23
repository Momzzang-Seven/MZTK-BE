package momzzangseven.mztkbe.modules.auth.application.strategy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import momzzangseven.mztkbe.global.error.UnsupportedProviderException;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import org.springframework.stereotype.Component;

/**
 * Role: Create instance of AuthenticationStrategy Responsibility: create Strategy instance
 * according to the provider
 */
@Component
public class AuthenticationStrategyFactory {

    private final Map<AuthProvider, AuthenticationStrategy> strategies =
            new EnumMap<>(AuthProvider.class);

    public AuthenticationStrategyFactory(List<AuthenticationStrategy> strategyList) {
        for (AuthenticationStrategy s : strategyList) {
            strategies.put(s.supports(), s);
        }
    }

    public AuthenticationStrategy getStrategy(AuthProvider provider) {
        AuthenticationStrategy strategy = strategies.get(provider);
        if (strategy == null) {
            throw new UnsupportedProviderException(provider);
        }
        return strategy;
    }
}
