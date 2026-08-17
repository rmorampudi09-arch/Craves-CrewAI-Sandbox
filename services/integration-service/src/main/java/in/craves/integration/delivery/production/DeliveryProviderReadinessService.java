package in.craves.integration.delivery.production;

import in.craves.integration.config.BorzoProperties;
import in.craves.integration.config.ShiprocketProperties;
import in.craves.integration.delivery.DeliveryProviderRepository;
import in.craves.integration.delivery.command.DeliveryCommandProperties;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter;
import in.craves.integration.delivery.provider.DeliveryProviderPickupLocationRepository;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DeliveryProviderReadinessService {
    private final BorzoProperties borzo;
    private final ShiprocketProperties shiprocket;
    private final DeliveryCommandProperties delivery;
    private final DeliveryProviderRepository providerRepository;
    private final DeliveryProviderPickupLocationRepository pickupLocations;
    private final Set<String> commandAdapterIds;

    public DeliveryProviderReadinessService(BorzoProperties borzo,
                                            ShiprocketProperties shiprocket,
                                            DeliveryCommandProperties delivery,
                                            DeliveryProviderRepository providerRepository,
                                            DeliveryProviderPickupLocationRepository pickupLocations,
                                            List<DeliveryProviderAdapter> commandAdapters) {
        this.borzo = borzo;
        this.shiprocket = shiprocket;
        this.delivery = delivery;
        this.providerRepository = providerRepository;
        this.pickupLocations = pickupLocations;
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (DeliveryProviderAdapter adapter : commandAdapters) {
            ids.add(normalize(adapter.providerId()));
        }
        this.commandAdapterIds = Set.copyOf(ids);
    }

    /**
     * Backward-compatible Borzo readiness response retained for existing callers.
     */
    public ReadinessResponse status() {
        ProviderReadiness borzoStatus = borzoReadiness();
        return new ReadinessResponse(
            "BORZO",
            borzoStatus.environment(),
            borzoStatus.productionReady(),
            borzoStatus.providerCreateEnabled(),
            delivery.isReconciliationEnabled(),
            delivery.isWebhookProcessingEnabled(),
            delivery.isTrackingReconciliationEnabled(),
            delivery.isStatusPublisherEnabled(),
            borzoStatus.blockers()
        );
    }

    public ReadinessMatrix matrix() {
        return new ReadinessMatrix(
            List.of(
                borzoReadiness(),
                shiprocketReadiness(),
                vendorBlocked(
                    "SHADOWFAX",
                    "VENDOR_PRIVATE_API_CONTRACT_REQUIRED"
                ),
                vendorBlocked(
                    "PORTER",
                    "ENTERPRISE_API_ONBOARDING_REQUIRED"
                ),
                vendorBlocked(
                    "DELHIVERY",
                    "INTRACITY_API_PRODUCT_NOT_VERIFIED"
                )
            ),
            sharedDownstreamBlockers()
        );
    }

    private ProviderReadiness borzoReadiness() {
        List<String> blockers = new ArrayList<>();
        if (!"PRODUCTION".equals(borzo.normalizedEnvironment())) {
            blockers.add("BORZO_API_ENVIRONMENT_NOT_PRODUCTION");
        }
        if (!borzo.isProductionActivationApproved()) {
            blockers.add("PRODUCTION_ACTIVATION_NOT_APPROVED");
        }
        if (!StringUtils.hasText(borzo.getAuthToken())) {
            blockers.add("AUTH_TOKEN_SECRET_NOT_BOUND");
        }
        if (!StringUtils.hasText(borzo.getCallbackSecret())) {
            blockers.add("CALLBACK_SECRET_NOT_BOUND");
        }
        if (!StringUtils.hasText(borzo.getCallbackUrl())) {
            blockers.add("CALLBACK_URL_NOT_CONFIGURED");
        } else {
            try {
                URI callback = URI.create(borzo.getCallbackUrl());
                if (!"https".equalsIgnoreCase(callback.getScheme())) {
                    blockers.add("CALLBACK_URL_NOT_HTTPS");
                }
            } catch (IllegalArgumentException ex) {
                blockers.add("CALLBACK_URL_INVALID");
            }
        }
        String baseUrl = borzo.normalizedBaseUrl().toLowerCase(Locale.ROOT);
        if (baseUrl.contains("test") || baseUrl.contains("sandbox")) {
            blockers.add("PROVIDER_BASE_URL_IS_NON_PRODUCTION");
        }
        if (!adapterRegistered("borzo")) {
            blockers.add("COMMAND_ADAPTER_NOT_REGISTERED");
        }
        if (!catalogActive("borzo")) {
            blockers.add("PROVIDER_CATALOG_INACTIVE");
        }
        blockers.addAll(sharedDownstreamBlockers());

        boolean createEnabled = borzo.isEnabled() && delivery.isEnabled() && catalogActive("borzo");
        boolean productionReady = blockers.isEmpty() && borzo.productionReady();
        return new ProviderReadiness(
            "BORZO",
            borzo.normalizedEnvironment(),
            adapterRegistered("borzo"),
            borzo.isEnabled(),
            createEnabled,
            productionReady,
            false,
            catalogActive("borzo"),
            0,
            List.copyOf(blockers)
        );
    }

    private ProviderReadiness shiprocketReadiness() {
        List<String> blockers = new ArrayList<>();
        if (!"PRODUCTION".equals(shiprocket.executionMode())) {
            blockers.add("SHIPROCKET_API_ENVIRONMENT_NOT_PRODUCTION");
        }
        if (!shiprocket.isProductionActivationApproved()) {
            blockers.add("PRODUCTION_ACTIVATION_NOT_APPROVED");
        }
        if (!shiprocket.credentialReady()) {
            blockers.add("API_CREDENTIALS_NOT_BOUND");
        }
        if (!StringUtils.hasText(shiprocket.getWebhookToken())) {
            blockers.add("WEBHOOK_TOKEN_NOT_BOUND");
        }
        if (!StringUtils.hasText(shiprocket.getOrderEmail())) {
            blockers.add("ORDER_EMAIL_NOT_CONFIGURED");
        }
        if (!shiprocket.packageDimensionsReady()) {
            blockers.add("PACKAGE_DIMENSIONS_NOT_CONFIGURED");
        }
        if (!shiprocket.isAttributionApproved()) {
            blockers.add("COURIER_ATTRIBUTION_NOT_APPROVED");
        }
        if (!shiprocket.isEnabled()) {
            blockers.add("SHIPROCKET_API_DISABLED");
        }
        if (!shiprocket.isCreateEnabled()) {
            blockers.add("SHIPROCKET_CREATE_DISABLED");
        }
        if (!adapterRegistered("shiprocket")) {
            blockers.add("COMMAND_ADAPTER_NOT_REGISTERED");
        }
        if (!catalogActive("shiprocket")) {
            blockers.add("PROVIDER_CATALOG_INACTIVE");
        }
        int verifiedPickupLocations = pickupLocations.countVerified("shiprocket");
        if (verifiedPickupLocations < 1) {
            blockers.add("NO_VERIFIED_CHEF_PICKUP_MAPPING");
        }
        blockers.addAll(sharedDownstreamBlockers());

        boolean readOnlyQuoteReady = shiprocket.isEnabled()
            && shiprocket.credentialReady()
            && adapterRegistered("shiprocket");
        boolean createEnabled = shiprocket.productionCreateReady()
            && delivery.isEnabled()
            && catalogActive("shiprocket")
            && verifiedPickupLocations > 0;
        return new ProviderReadiness(
            "SHIPROCKET",
            shiprocket.executionMode(),
            adapterRegistered("shiprocket"),
            readOnlyQuoteReady,
            createEnabled,
            blockers.isEmpty() && createEnabled,
            readOnlyQuoteReady,
            catalogActive("shiprocket"),
            verifiedPickupLocations,
            List.copyOf(blockers)
        );
    }

    private ProviderReadiness vendorBlocked(String provider, String blocker) {
        String providerId = provider.toLowerCase(Locale.ROOT);
        List<String> blockers = new ArrayList<>();
        blockers.add(blocker);
        if (!adapterRegistered(providerId)) {
            blockers.add("COMMAND_ADAPTER_NOT_REGISTERED");
        }
        if (!catalogActive(providerId)) {
            blockers.add("PROVIDER_CATALOG_INACTIVE");
        }
        return new ProviderReadiness(
            provider,
            "BLOCKED",
            adapterRegistered(providerId),
            false,
            false,
            false,
            false,
            catalogActive(providerId),
            pickupLocations.countVerified(providerId),
            List.copyOf(blockers)
        );
    }

    private List<String> sharedDownstreamBlockers() {
        List<String> blockers = new ArrayList<>();
        if (!StringUtils.hasText(delivery.getFullyQualifiedNamespace())
            && !StringUtils.hasText(delivery.getConnectionString())) {
            blockers.add("SERVICE_BUS_NOT_CONFIGURED");
        }
        if (!delivery.isReconciliationEnabled()) {
            blockers.add("CREATE_RECONCILIATION_DISABLED");
        }
        if (!delivery.isWebhookProcessingEnabled()) {
            blockers.add("WEBHOOK_PROCESSOR_DISABLED");
        }
        if (!delivery.isTrackingReconciliationEnabled()) {
            blockers.add("TRACKING_RECONCILIATION_DISABLED");
        }
        if (!delivery.isStatusPublisherEnabled()) {
            blockers.add("DELIVERY_STATUS_PUBLISHER_DISABLED");
        }
        if (!delivery.isEnabled()) {
            blockers.add("DELIVERY_COMMAND_WORKER_DISABLED");
        }
        return List.copyOf(blockers);
    }

    private boolean adapterRegistered(String providerId) {
        return commandAdapterIds.contains(normalize(providerId));
    }

    private boolean catalogActive(String providerId) {
        return providerRepository.find(providerId).map(response -> response.active()).orElse(false);
    }

    private static String normalize(String providerId) {
        return providerId.trim().toLowerCase(Locale.ROOT);
    }

    public record ReadinessMatrix(
        List<ProviderReadiness> providers,
        List<String> sharedDownstreamBlockers
    ) {}

    public record ProviderReadiness(
        String provider,
        String environment,
        boolean commandAdapterRegistered,
        boolean providerApiOrQuoteEnabled,
        boolean providerCreateEnabled,
        boolean productionReady,
        boolean readOnlyQuoteReady,
        boolean providerCatalogActive,
        int verifiedPickupLocations,
        List<String> blockers
    ) {}

    public record ReadinessResponse(
        String provider,
        String environment,
        boolean productionReady,
        boolean providerCreateEnabled,
        boolean createReconciliationEnabled,
        boolean webhookProcessingEnabled,
        boolean trackingReconciliationEnabled,
        boolean statusPublisherEnabled,
        List<String> blockers
    ) {}
}
