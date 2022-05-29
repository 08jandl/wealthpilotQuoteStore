package com.wealthpilot.quote.store.util;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Simple extension to ease the use of the {@link LoggingInterceptor}.
 */
public class LoggingInterceptorExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private final LoggingInterceptor loggingInterceptor = new LoggingInterceptor();

    @Override
    public void beforeEach(ExtensionContext context) {
        loggingInterceptor.start();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        loggingInterceptor.stop();
    }

    @Override
    public boolean supportsParameter(final ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType().equals(LoggingInterceptor.class);
    }

    @Override
    public LoggingInterceptor resolveParameter(final ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return loggingInterceptor;
    }
}
