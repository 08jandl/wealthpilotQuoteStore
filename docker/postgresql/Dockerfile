FROM postgres:13.5-alpine
LABEL "Product"="PostgreSQL (SSL enabled)"

COPY docker-entrypoint-initdb.d /docker-entrypoint-initdb.d

# add ssl keys
ARG POSTGRES_CERTIFICATE_DIR=
COPY ssl/dev.wealthpilot.de.crt.pem /var/lib/postgresql/ssl/server.crt
COPY ssl/dev.wealthpilot.de.key.pem /var/lib/postgresql/ssl/server.key
RUN chown postgres /var/lib/postgresql/ssl/server.key \
 && chmod 600 /var/lib/postgresql/ssl/server.key

ENV POSTGRES_PASSWORD=pwp-dev-server-db

# switch on ssl configuration in sample (is copied on startup to /var/lib/postgresql/data
RUN sed -i -e 's/#ssl = off/ssl = on/' /usr/local/share/postgresql/postgresql.conf.sample \
 && sed -i -e "s/#ssl_cert_file = 'server.crt'/ssl_cert_file = '\/var\/lib\/postgresql\/ssl\/server.crt'/" /usr/local/share/postgresql/postgresql.conf.sample \
 && sed -i -e "s/#ssl_key_file = 'server.key'/ssl_key_file = '\/var\/lib\/postgresql\/ssl\/server.key'/" /usr/local/share/postgresql/postgresql.conf.sample

EXPOSE 5432

