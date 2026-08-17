package in.craves.order.service;

import in.craves.order.exception.OrderApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class CustomerAddressClient {
    private static final String INTERNAL_HEADER = "X-Craves-Internal-Secret";

    private final RestClient restClient;
    private final String baseUrl;
    private final String accessValue;

    public CustomerAddressClient(
        @Value("${CRAVES_USER_CHEF_INTERNAL_BASE_URL:http://localhost:8081}") String baseUrl,
        @Value("${CRAVES_INTERNAL_SERVICE_SECRET:}") String accessValue,
        RestClient.Builder builder
    ) {
        this.baseUrl = baseUrl;
        this.accessValue = accessValue;
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public CustomerAddress getActiveOwnedAddress(UUID identityId, UUID addressId) {
        if (identityId == null || addressId == null) {
            throw OrderApiException.badRequest(
                "DELIVERY_ADDRESS_REQUIRED",
                "Save the current location or select a saved delivery address before placing the order."
            );
        }
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(accessValue)) {
            throw OrderApiException.serviceUnavailable(
                "DELIVERY_ADDRESS_LOOKUP_UNAVAILABLE",
                "Delivery address verification is temporarily unavailable."
            );
        }

        try {
            CustomerAddress address = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/internal/v1/customer-addresses/{addressId}")
                    .queryParam("identityId", identityId)
                    .build(addressId))
                .header(INTERNAL_HEADER, accessValue)
                .header(HttpHeaders.ACCEPT, "application/json")
                .retrieve()
                .body(CustomerAddress.class);

            if (address == null || address.id() == null || address.identityId() == null) {
                throw OrderApiException.serviceUnavailable(
                    "DELIVERY_ADDRESS_LOOKUP_INVALID_RESPONSE",
                    "Delivery address verification returned an incomplete response."
                );
            }
            if (!addressId.equals(address.id()) || !identityId.equals(address.identityId()) || !address.active()) {
                throw OrderApiException.notFound(
                    "DELIVERY_ADDRESS_NOT_AVAILABLE",
                    "The selected delivery address is inactive or does not belong to the customer."
                );
            }
            return address;
        } catch (HttpClientErrorException.NotFound ex) {
            throw OrderApiException.notFound(
                "DELIVERY_ADDRESS_NOT_AVAILABLE",
                "The selected delivery address is inactive or does not belong to the customer."
            );
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
            throw OrderApiException.serviceUnavailable(
                "DELIVERY_ADDRESS_LOOKUP_UNAUTHORIZED",
                "Delivery address verification is not configured correctly."
            );
        } catch (HttpServerErrorException ex) {
            throw OrderApiException.serviceUnavailable(
                "DELIVERY_ADDRESS_LOOKUP_UNAVAILABLE",
                "Delivery address verification is temporarily unavailable."
            );
        } catch (OrderApiException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw OrderApiException.serviceUnavailable(
                "DELIVERY_ADDRESS_LOOKUP_UNAVAILABLE",
                "Delivery address verification is temporarily unavailable."
            );
        }
    }

    public record CustomerAddress(
        UUID id,
        UUID identityId,
        String addressLabel,
        String recipientName,
        String contactPhoneNumber,
        String addressLine1,
        String addressLine2,
        String landmark,
        String areaName,
        String city,
        String state,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean isDefault,
        boolean active,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
