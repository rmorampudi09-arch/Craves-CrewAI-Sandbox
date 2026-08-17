package in.craves.integration.web;

import in.craves.integration.delivery.InternalRequestAuthorizer;
import in.craves.integration.delivery.borzo.BorzoApiClient;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.CreateDeliveryRequest;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.ProviderDelivery;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.ProviderQuote;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.QuoteRequest;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.TrackingSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/delivery-providers/borzo")
public class BorzoInternalController {
    private static final String INTERNAL_HEADER = "X-Craves-Internal-Secret";
    private final BorzoApiClient borzo;
    private final InternalRequestAuthorizer authorizer;

    public BorzoInternalController(BorzoApiClient borzo, InternalRequestAuthorizer authorizer) {
        this.borzo = borzo;
        this.authorizer = authorizer;
    }

    @PostMapping("/quote")
    public ProviderQuote quote(
        @RequestHeader(INTERNAL_HEADER) String internalKey,
        @RequestBody QuoteRequest request
    ) {
        authorizer.requireValid(internalKey);
        return borzo.quote(request);
    }

    @PostMapping("/deliveries")
    public ProviderDelivery create(
        @RequestHeader(INTERNAL_HEADER) String internalKey,
        @RequestBody CreateDeliveryRequest request
    ) {
        authorizer.requireValid(internalKey);
        return borzo.create(request);
    }

    @GetMapping("/deliveries/{providerDeliveryId}")
    public TrackingSnapshot track(
        @RequestHeader(INTERNAL_HEADER) String internalKey,
        @PathVariable String providerDeliveryId
    ) {
        authorizer.requireValid(internalKey);
        return borzo.track(providerDeliveryId);
    }

    @PostMapping("/deliveries/{providerDeliveryId}/cancel")
    public ProviderDelivery cancel(
        @RequestHeader(INTERNAL_HEADER) String internalKey,
        @PathVariable String providerDeliveryId
    ) {
        authorizer.requireValid(internalKey);
        return borzo.cancel(providerDeliveryId);
    }
}
