package in.craves.integration.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class RazorpayRequestSafety {
    private RazorpayRequestSafety() {}

    public static long toSubunits(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment amount must be positive");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment amount has unsupported precision");
        }
    }

    public static BigDecimal fromSubunits(long amount) {
        return BigDecimal.valueOf(amount, 2);
    }

    public static void requireMoney(
        BigDecimal expectedAmount,
        String expectedCurrency,
        long actualSubunits,
        String actualCurrency,
        String context
    ) {
        if (expectedAmount == null
            || expectedAmount.compareTo(fromSubunits(actualSubunits)) != 0
            || expectedCurrency == null
            || actualCurrency == null
            || !expectedCurrency.equalsIgnoreCase(actualCurrency)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, context + " amount or currency does not match Craves");
        }
    }
}
