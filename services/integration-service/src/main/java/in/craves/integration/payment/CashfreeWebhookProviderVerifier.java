package in.craves.integration.payment;

import com.fasterxml.jackson.databind.JsonNode;
import in.craves.integration.config.PaymentProviderProperties;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CashfreeWebhookProviderVerifier {
    private final PaymentProviderProperties provider;
    private final RestClient providerClient;

    public CashfreeWebhookProviderVerifier(
        PaymentProviderProperties provider,
        RestClient.Builder builder
    ) {
        this.provider = provider;
        this.providerClient = builder.clone().baseUrl(provider.baseUrl()).build();
    }

    public void verifySuccessfulPayment(JsonNode webhook) {
        String webhookStatus = firstText(
            webhook,
            "/data/payment/payment_status",
            "/payment/payment_status",
            "/payment_status"
        );
        if (!"SUCCESS".equalsIgnoreCase(webhookStatus)) {
            return;
        }

        String orderId = firstText(webhook, "/data/order/order_id", "/order/order_id", "/order_id");
        String paymentId = firstText(
            webhook,
            "/data/payment/cf_payment_id",
            "/payment/cf_payment_id",
            "/cf_payment_id"
        );
        if (!StringUtils.hasText(orderId) || !StringUtils.hasText(paymentId)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cashfree success webhook is missing order or payment identity"
            );
        }

        JsonNode verified;
        try {
            verified = providerClient.get()
                .uri("/pg/orders/{orderId}/payments/{paymentId}", orderId, paymentId)
                .header("x-client-id", provider.clientId())
                .header("x-client-" + "secret", provider.clientKey())
                .header("x-api-version", provider.apiVersion())
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Cashfree payment verification API failed",
                exception
            );
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Cashfree payment verification could not be completed",
                exception
            );
        }

        if (verified == null
            || !orderId.equals(text(verified, "order_id"))
            || !paymentId.equals(text(verified, "cf_payment_id"))
            || !"SUCCESS".equalsIgnoreCase(text(verified, "payment_status"))) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cashfree payment verification did not confirm the success webhook"
            );
        }

        BigDecimal webhookAmount = decimal(firstText(
            webhook,
            "/data/payment/payment_amount",
            "/payment/payment_amount",
            "/payment_amount"
        ));
        BigDecimal verifiedAmount = decimal(text(verified, "payment_amount"));
        BigDecimal verifiedOrderAmount = decimal(text(verified, "order_amount"));
        if (webhookAmount != null && verifiedAmount != null && webhookAmount.compareTo(verifiedAmount) != 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cashfree verified payment amount does not match the webhook"
            );
        }
        if (verifiedAmount == null
            || verifiedOrderAmount == null
            || verifiedAmount.compareTo(verifiedOrderAmount) != 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cashfree successful payment amount does not match the Cashfree order amount"
            );
        }

        String webhookCurrency = firstText(
            webhook,
            "/data/payment/payment_currency",
            "/payment/payment_currency",
            "/payment_currency"
        );
        String verifiedCurrency = text(verified, "payment_currency");
        String verifiedOrderCurrency = text(verified, "order_currency");
        if (StringUtils.hasText(webhookCurrency)
            && StringUtils.hasText(verifiedCurrency)
            && !webhookCurrency.equalsIgnoreCase(verifiedCurrency)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cashfree verified payment currency does not match the webhook"
            );
        }
        if (!StringUtils.hasText(verifiedCurrency)
            || !StringUtils.hasText(verifiedOrderCurrency)
            || !verifiedCurrency.equalsIgnoreCase(verifiedOrderCurrency)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cashfree successful payment currency does not match the Cashfree order currency"
            );
        }
    }

    private static String firstText(JsonNode node, String... pointers) {
        if (node == null) {
            return null;
        }
        for (String pointer : pointers) {
            JsonNode value = node.at(pointer);
            if (!value.isMissingNode() && !value.isNull()) {
                return value.asText();
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static BigDecimal decimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
