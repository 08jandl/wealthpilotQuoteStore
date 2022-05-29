#!/usr/bin/env bash

# debug (DO NOT USE on test-server as then all secret passwords are logged to datadog!!!)
# set -x

echo "Starting test server script with arguments: $@"

echo "checking for running application"
fuser -n tcp 8080

if [[ $? = 0 ]]; then
  echo "Detected running application, exiting...."
  echo "kill application by executing:"
  echo "fuser -k -n tcp 8080"
  exit 1
fi

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
CREDENTIALS_FILE=${DIR}/.pwp_credentials
echo "reading credentials from ${CREDENTIALS_FILE}"
source ${CREDENTIALS_FILE}

if [ "${CREDENTIALS_VERSION}" != "pwp_20181219" ]; then
  echo "could not read file containing credentials!"
  exit -1
else
  echo "Credentials version: ${CREDENTIALS_VERSION}"
fi

BUILD_PROJECT=true
REMOTE_DEBUGGING_ARGS=

for i in "$@"
do
case $i in
    --no-build)
    BUILD_PROJECT=false
    echo "Disabled building source code"
    shift # past argument=value
    ;;
    --remote-debug)
    echo "Enable remote debugging on port 5005"
    REMOTE_DEBUGGING_ARGS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
    shift # past argument=value
    ;;
     *)
    echo "Unknown option: $i"
    # unknown option
    ;;
esac
done

echo "BUILD_PROJECT: ${BUILD_PROJECT}"
echo "REMOTE_DEBUGGING_ARGS: ${REMOTE_DEBUGGING_ARGS}"

# compile source code
if [[ "$BUILD_PROJECT" == "true" ]]; then
    echo "building project"
    mvn -Pprod clean package -DskipTests || exit -3
fi


# set environment variables used by application (like this, they are not visible in process list):
export RDS_HOSTNAME=${DB_HOSTNAME}
export RDS_PORT=${DB_PORT}
export RDS_USERNAME=${DB_USERNAME}
export RDS_DB_NAME=${DB_NAME}
export RDS_PASSWORD=${DB_PASSWORD}
export SPRING_LIQUIBASE_CONTEXTS=prod,prod_server
export COMMONJPA_DATABASEENCRYPTION_V02PASSWORD=${DB_ENCRYPTION_V02_PASSWORD}
export COMMONJPA_DATABASEENCRYPTION_V03PASSWORD=${DB_ENCRYPTION_V03_PASSWORD}
export COMMONJPA_DATABASEENCRYPTION_V04PASSWORD=${DB_ENCRYPTION_V04_PASSWORD}
export COMMONJPA_DATABASEENCRYPTION_V05PASSWORD=${DB_ENCRYPTION_V05_PASSWORD}
export COMMONJPA_DATABASEENCRYPTION_V06PASSWORD=${DB_ENCRYPTION_V06_PASSWORD}
export REFINITIV_RDP_CLIENTID=${REFINITIV_RDP_CLIENTID}
export REFINITIV_RDP_USERNAME=${REFINITIV_RDP_USERNAME}
export REFINITIV_RDP_PASSWORD=${REFINITIV_RDP_PASSWORD}
export MANAGEMENT_METRICS_EXPORT_DATADOG_ENABLED=${MANAGEMENT_METRICS_EXPORT_DATADOG_ENABLED}
export MANAGEMENT_METRICS_EXPORT_DATADOG_API_KEY=${MANAGEMENT_METRICS_EXPORT_DATADOG_API_KEY}
export MANAGEMENT_METRICS_TAGS_ENVIRONMENT=${MANAGEMENT_METRICS_TAGS_ENVIRONMENT}
export MANAGEMENT_METRICS_TAGS_HOST=${MANAGEMENT_METRICS_TAGS_HOST}
export B2B_IMPORT20_PROCESSOR_SCANDIRECTORYPATH=/tmp
export QUOTECACHE_QUOTECACHEDUMPFILE=/tmp/quoteCacheDumpFile.json

# log to console and to log file:
java \
 -Dspring.profiles.active=prod \
 -Djhipster.cron.allNightlyUpdatesEnabled=false \
 -Djhipster.cron.scheduledTasksEnabled=false \
 -Dmail.enabled=false \
 -Xmx10000m -Xms10000m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heap_dump.hprof \
 ${REMOTE_DEBUGGING_ARGS} \
 -jar wealthpilot-app/target/wealthpilot-app-*.jar


# to enable FinApi Live environment:K
# -Dcommon.certificate.truststorePath=/truststore_finapi_live.jks \
# -Dcommon.certificate.truststorePassword=${TRUSTSTORE_PASSWORD} \
# -Djhipster.finApi.apiUri=https://live.finapi.io/api/v1 \
# -Djhipster.finApi.apiUriOAuth=https://live.finapi.io/oauth \
# -Djhipster.finApi.clientId=b17e307e-26b7-49b3-aa30-449ae8e0284f \
# -Djhipster.finApi.clientSecret=${FINAPI_CLIENTSECRET} \
# -Djhipster.cron.nightlyUpdates="0 42 14 * * *" \
# -Djhipster.async.updateApiSourceParallelism=30 \
