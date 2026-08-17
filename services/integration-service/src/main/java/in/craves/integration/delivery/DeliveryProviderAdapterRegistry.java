package in.craves.integration.delivery;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DeliveryProviderAdapterRegistry {
    private final Map<String, DeliveryProviderAdapter> adapters;

    public DeliveryProviderAdapterRegistry(List<DeliveryProviderAdapter> adapters) {
        this.adapters = adapters.stream().collect(Collectors.toUnmodifiableMap(
            adapter -> normalize(adapter.providerId()), Function.identity(),
            (first, duplicate) -> { throw new IllegalStateException("Duplicate delivery adapter for " + first.providerId()); }
        ));
    }

    public Optional<DeliveryProviderAdapter> find(String providerId) {
        return Optional.ofNullable(adapters.get(normalize(providerId)));
    }

    public List<DeliveryProviderAdapter> all() { return List.copyOf(adapters.values()); }
    private static String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT); }
}
