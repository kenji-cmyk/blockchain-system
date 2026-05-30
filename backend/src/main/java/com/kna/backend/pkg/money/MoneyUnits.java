package com.kna.backend.pkg.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUnits {

    public static final int SCALE = 8;
    public static final long UNITS_PER_COIN = 100_000_000L;

    private MoneyUnits() {
    }

    public static long toUnits(double amount) {
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("Amount must be finite");
        }
        return BigDecimal.valueOf(amount)
                .movePointRight(SCALE)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();
    }

    public static double fromUnits(long units) {
        return BigDecimal.valueOf(units, SCALE).doubleValue();
    }

    public static boolean equals(double left, double right) {
        try {
            return toUnits(left) == toUnits(right);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return false;
        }
    }

    public static String canonical(double amount) {
        return BigDecimal.valueOf(fromUnits(toUnits(amount))).stripTrailingZeros().toPlainString();
    }
}
