package com.wealthpilot.quote.store.util;

import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.lang.NonNull;

/**
 * Simple 'bean validator', which internally wraps the hibernate validator for doing validations.
 */
public final class BeanValidator {

    private BeanValidator() {
    }

    private static final Logger LOG = LogManager.getLogger();
    private static final javax.validation.Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    public static <T> void validate(@NonNull final T t) {
        final Set<ConstraintViolation<T>> violations = getConstraintViolationsForEntity(t);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    @NonNull
    public static <T> Set<ConstraintViolation<T>> getConstraintViolationsForEntity(@NonNull final T t) {
        final Set<ConstraintViolation<T>> violations = VALIDATOR.validate(t);

        violations.stream().map(violation -> buildViolationMessage(t, violation)).forEach(LOG::error);
        return violations;
    }

    @NonNull
    private static <T> String buildViolationMessage(@NonNull final T t, @NonNull final ConstraintViolation<T> violation) {
        return t.getClass().getSimpleName() + "#" + violation.getPropertyPath() + " " + violation.getMessage() + " for entity / DTO:" + violation.getRootBean();
    }
}
