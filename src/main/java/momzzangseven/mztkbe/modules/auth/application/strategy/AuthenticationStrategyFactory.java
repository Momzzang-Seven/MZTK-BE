package momzzangseven.mztkbe.modules.auth.application.strategy;

import momzzangseven.mztkbe.global.error.UnsupportedProviderException;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Role: Create instance of AuthenticationStrategy
 * Responsibility: create Strategy instance according to the provider
 */
@Component
public class AuthenticationStrategyFactory {
    private final Map<AuthProvider, AuthenticationStrategy> strategies;

    // Injected every strategies by constructor injection
    public AuthenticationStrategyFactory(List<AuthenticationStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(                  // Convert each element of List to Map Entry
                        AuthenticationStrategy::supports,   // set the Key. AuthProvider
                        Function.identity()                 // set the Value. AuthenticationStrategy
                ));
    }

    public AuthenticationStrategy getStrategy(AuthProvider provider) {
        AuthenticationStrategy strategy = strategies.get(provider);
        if (strategy == null) {
            throw new UnsupportedProviderException(provider);
        }
        return strategy;
    }


}
