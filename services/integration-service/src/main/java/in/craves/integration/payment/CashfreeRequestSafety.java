package in.craves.integration.payment;

import com.fasterxml.jackson.databind.JsonNode;
import in.craves.integration.config.PaymentProviderProperties;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared validation for data sent to, or trusted from, Cashfree.
 *
 * Production deliberately rejects malformed customer/provider data instead of
 * substituting values that could hide a real checkout configuration problem.
 */
public final class CashfreeRequestSafety {
    private static final String SANDBOX_PHONE_FALLBACK = "9999999999";

    private CashfreeRequestSafety() {
    }

    public static String normalizeIndianPhone(String value, boolean sandbox) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        if (digits.matches("[6-9][0-9]{9}")) {
            return digits;
        }
        if (sandbox) {
            return SANDBOX_PHONE_FALLBACK;
        }
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Customer phone must be a valid 10-digit Indian mobile number"
        );
    }

    public static String safeReturnUrl(PaymentProviderProperties provider, String requested) {
        String value = StringUtils.hasText(requested) ? requested.trim() : provider.defaultReturnUrl();
        URI target = httpsUri(value, "Payment return URL");
        if (!provider.sandbox()) {
            URI configured = httpsUri(provider.defaultReturnUrl(), "Configured payment return URL");
            String configuredHost = configured.getHost().toLowerCase(Locale.ROOT);
            String requestedHost = target.getHost().toLowerCase(Locale.ROOT);
            if (!requestedHost.equals(configuredHost) && !requestedHost.endsWith("." + configuredHost)) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment return URL must use the configured Craves domain"
                );
            }
        }
        return target.toString();
    }

    public static void requireMoney(
        BigDecimal expectedAmount,
        String expectedCurrency,
        BigDecimal actualAmount,
        String actualCurrency,
        String context
    ) {
        if (expectedAmount == null || actualAmount == null || expectedAmount.compareTo(actualAmount) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, context + " amount does not match Craves");
        }
        if (!StringUtils.hasText(expectedCurrency)
            || !StringUtils.hasText(actualCurrency)
            || !expectedCurrency.equalsIgnoreCase(actualCurrency)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, context + " currency does not match Craves");
        }
    }

    public static void requireCreateOrderResponse(
        JsonNode response,
        String expectedOrderId,
        BigDecimal expectedAmount,
        String expectedCurrency
    ) {
        if (response == null
            || !StringUtils.hasText(text(response, "payment_session_id"))
            || !StringUtils.hasText(text(response, "cf_order_id"))
            || !StringUtils.hasText(text(response, "order_id"))) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cashfree create-order response is incomplete");
        }
        if (!expectedOrderId.equals(text(response, "order_id"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cashfree order identity does not match Craves");
        }
        requireMoney(
            expectedAmount,
            expectedCurrency,
            decimal(text(response, "order_amount")),
            text(response, "order_currency"),
            "Cashfree order"
        );
    }

    public static BigDecimal decimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static URI httpsUri(String value, String description) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, description + " is invalid", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, description + " must use HTTPS");
        }
        return uri;
    }
}
