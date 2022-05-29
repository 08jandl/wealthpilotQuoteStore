package com.wealthpilot.quote.store.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.BinaryOperator;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Simple arithmetic utility functions.
 */
public final class ArithmeticsUtil {

    private static final double EPSILON = 0.000001;

    private ArithmeticsUtil() {
    }

    @Nullable
    public static Double getSumOrNull(Double... values) {
        return getSumOfValues(values, null);
    }

    public static double getSumOrZero(Double... values) {
        return Objects.requireNonNull(getSumOfValues(values, 0d));
    }

    @Nullable
    private static Double getSumOfValues(Double[] values, @Nullable Double defaultValue) {
        return reduceValues(values, defaultValue, Double::sum);
    }

    public static long getSumOrZero(Long... values) {
        return Objects.requireNonNull(getSumOfValues(values, 0L));
    }

    @SuppressWarnings("SameParameterValue") // method should be used for other cases too
    private static Long getSumOfValues(Long[] values, @NonNull Long defaultValue) {
        return Objects.requireNonNull(reduceValues(values, defaultValue, Long::sum));
    }

    @Nullable
    private static <T> T reduceValues(T[] values, @Nullable T defaultValue, BinaryOperator<T> sum) {
        return Arrays.stream(values).filter(Objects::nonNull).reduce(sum).orElse(defaultValue);
    }

    public static double nullSafeDouble(@Nullable Double value) {
        return (value == null) ? 0 : value;
    }

    public static OptionalDouble getValue(@Nullable BigDecimal decimal) {
        return decimal == null ? OptionalDouble.empty() : OptionalDouble.of(decimal.doubleValue());
    }

    public static OptionalInt getValue(@Nullable BigInteger integer) {
        return integer == null ? OptionalInt.empty() : OptionalInt.of(integer.intValue());
    }

    @Nullable
    public static String getFormattedDouble(@Nullable final Double d) {
        return d == null ? null : new DecimalFormat("#,###").format(d);
    }

    public static boolean isDifferent(final @Nullable Double value, final @Nullable Double otherValue) {
        if (value == null && otherValue == null) {
            return false;
        }
        if (value == null || otherValue == null) {
            return true;
        }
        return Math.abs(value - otherValue) > EPSILON;
    }

}
