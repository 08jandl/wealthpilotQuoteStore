package com.wealthpilot.quote.store.util;

/**
 * Application constants.
 */
public final class Constants {

    //Regex for acceptable logins
    public static final String LOGIN_REGEX = "^[_.@A-Za-z0-9-]*$";
    // Spring profile for development and production, see http://jhipster.github.io/profiles/
    public static final String SPRING_PROFILE_DEVELOPMENT = "dev";
    public static final String SPRING_PROFILE_PRODUCTION = "prod";
    public static final String SPRING_PROFILE_TEST = "test";

    public static final String SYSTEM_ACCOUNT = "system";
    public static final String SYSTEM_CURRENCY = "EUR";

    // Dummy/default value to be used if we do not get a WKN from an API
    public static final String WKN_PLACEHOLDER = "n/a";
    /**
     * Commerzbank date time response format.
     */
    public static final String LOCAL_DATE_TIME_COMMERZBANK = "yyyy-MM-dd[z]";
    public static final String DEFAULT_CURRENCY = "EUR";
    public static final String PERCENTAGE_CURRENCY = "%";

    public static final String SCHEDULED_TASKS_DISABLED_MESSAGE = "Scheduled tasks are disabled by configuration (jhipster.cron.scheduledTasksEnabled)!";
    public static final String NIGHTLY_UPDATES_DISABLED_MESSAGE = "Nightly updates are disabled by configuration (jhipster.cron.allNightlyUpdatesEnabled)!";

    private Constants() {
    }
}
