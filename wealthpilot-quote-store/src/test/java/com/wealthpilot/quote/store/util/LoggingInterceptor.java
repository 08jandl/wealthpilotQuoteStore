package com.wealthpilot.quote.store.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.lang.Nullable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Interceptor to verify that a specific logger was called or logged a specified string.
 * This interceptor works with SLF4J loggers and also Log4J2 loggers as these are internally the same in our application.
 */
@RequiredArgsConstructor
public class LoggingInterceptor {

    private final ListMdcAppender<ILoggingEvent> listMdcAppender = new ListMdcAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    public void start() {
        logger.addAppender(listMdcAppender);
        listMdcAppender.start();
    }

    public void stop() {
        listMdcAppender.stop();
        clearMessages();
        logger.detachAppender(listMdcAppender);
    }

    public void clearMessages() {
        listMdcAppender.list.clear();
    }

    // we don't want to do assertions on "debug" normally,
    // if really needed the logger must be programmatically configured, see com.wealthpilot.util.web.filter.ClientCertHeadersFilterIT.setUp
    private void assertDebugContains(final String text) {
        assertMessageContains(text, Level.DEBUG);
    }

    public void assertDebugContains(final String format, @Nullable final Object... args) {
        //noinspection RedundantCast // needed for varargs handling
        assertDebugContains(String.format(format, (Object[]) args));
    }

    public void assertInfo(final String... regex) {
        Arrays.stream(regex).forEach(r -> assertMessage(regexPredicate(r), Level.INFO));
    }

    public void assertInfoContains(final String text) {
        assertMessageContains(text, Level.INFO);
    }

    public void assertInfoContains(final String format, @Nullable final Object... args) {
        //noinspection RedundantCast // needed for varargs handling
        assertInfoContains(String.format(format, (Object[]) args));
    }

    public void assertWarning(final String... regex) {
        Arrays.stream(regex).forEach(r -> assertMessage(regexPredicate(r), Level.WARN));
    }

    public void assertNoErrors() {
        assertNoneOfLevelOccurred(Level.ERROR);
    }

    public void assertNoWarnings() {
        assertNoneOfLevelOccurred(Level.WARN);
    }

    public void assertNoInfos() {
        assertNoneOfLevelOccurred(Level.INFO);
    }

    private void assertNoneOfLevelOccurred(final Level level) {
        final List<ILoggingEvent> elements = messages(null, level);
        Assertions.assertThat(elements).as(String.format("Elements with %s found!", level)).isEmpty();
    }

    public void assertWarnContains(final String text) {
        assertMessageContains(text, Level.WARN);
    }

    public void assertError(@Nullable final String regex) {
        assertMessage(regexPredicate(regex), Level.ERROR);
    }

    public void assertErrorContains(final String text) {
        assertMessageContains(text, Level.ERROR);
    }

    public void assertErrorContains(final String format, final Object... args) {
        //noinspection RedundantCast // needed for varargs handling
        assertErrorContains(String.format(format, (Object[]) args));
    }

    public List<ILoggingEvent> warnings() {
        return messages(null, Level.WARN);
    }

    public List<ILoggingEvent> errors() {
        return messages(null, Level.ERROR);
    }

    private void assertMessage(@Nullable final Predicate<ILoggingEvent> predicate, final Level level) {
        final List<ILoggingEvent> elements = messages(predicate, level);
        assertThat(elements).as(String.format("No %ss with message found!", level)).isNotEmpty();
        assertThat(elements).as(String.format("Multiple %ss: %s", level, elements)).hasSize(1);
    }

    private void assertMessageContains(final String text, final Level level) {
        assertMessage(e -> e.getFormattedMessage().contains(text), level);
    }

    private List<ILoggingEvent> messages(@Nullable final Predicate<ILoggingEvent> predicate, final Level level) {
        return messageAsStream(predicate, level).collect(Collectors.toList());
    }

    private Stream<ILoggingEvent> messageAsStream(@Nullable Predicate<ILoggingEvent> predicate, final Level level) {
        final Stream<ILoggingEvent> stream = listMdcAppender.list.stream().filter(event -> level.equals(event.getLevel()));
        return predicate != null ? stream.filter(predicate) : stream;
    }

    @Nullable
    private Predicate<ILoggingEvent> regexPredicate(@Nullable String regex) {
        return regex == null ? null : e -> e.getFormattedMessage().matches(regex);
    }

    public final void assertMdcMapContains(final String key, final String value) {
        assertMdcMapContents(Map.entry(key, value));
    }

    @SafeVarargs
    private void assertMdcMapContents(final Map.Entry<String, String>... entries) {
        Arrays.stream(entries).forEach(entry -> assertThat(listMdcAppender.mdcMap).contains(entry));
    }

    public void assertMdcMapContainsNullValueForKey(final String... keys) {
        Arrays.stream(keys).forEach(key -> {
            assertThat(listMdcAppender.mdcMap).containsKey(key);
            assertThat(listMdcAppender.mdcMap.get(key)).isNull();
        });
    }

    public void assertEmptyMdcMap() {
        assertThat(listMdcAppender.mdcMap).isEmpty();
    }

    @Log4j2
    static final class ListMdcAppender<E> extends AppenderBase<E> {

        private final List<E> list = Collections.synchronizedList(new ArrayList<>());
        private final Map<String, String> mdcMap = new HashMap<>();

        @Override
        public void start() {
            super.start();
            mdcMap.clear();
        }

        @Override
        public void append(final E e) {
            list.add(e);
            final Map<String, String> contextMap = MDC.getCopyOfContextMap();
            // remove the request_id, which is set into the Map by the SoapServiceExtension if this is used in a test
            final String removed = contextMap.remove("request_id");
            if (removed != null) {
                log.info("Removed request_id from MDC map container, value was {}.", removed);
            }
            mdcMap.putAll(contextMap);
        }
    }
}
