package com.wealthpilot.quote.store.util;

/**
 * Validates and object of the provided class, e.g. an entity or a DTO
 *
 * @param <T> the class of the object
 * @author PhilippSommersguter
 */
public interface ValidationSelfCheck<T> {

    /**
     * Because of Lombok not knowing any annotations before building the object, we need to build first and then validate.
     *
     * @return T the built object.
     */
    default T build() {
        final T builtObject = this.buildWithoutValidation();

        BeanValidator.validate(builtObject);

        return builtObject;
    }

    T buildWithoutValidation();

}
