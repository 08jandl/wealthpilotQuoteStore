# ideas and code taken from https://github.com/atmoz/sftp/tree/alpine
FROM alpine:3.9

LABEL "com.datadoghq.ad.logs"='[{"source": "sftp", "service": "b2b-inbox-sftp"}]'

# set timezone to Europe/Berlin
ENV TZ=Europe/Berlin
RUN apk add --update tzdata \
 && cp /usr/share/zoneinfo/Europe/Berlin /etc/localtime \
 && rm -rf /var/cache/apk/*

# Steps done in one RUN layer:
# - Install packages
# - Fix default group (1000 does not exist)
# - OpenSSH needs /var/run/sshd to run
# - Remove generic host keys, entrypoint.sh generates unique keys
RUN echo "@community http://dl-cdn.alpinelinux.org/alpine/edge/community" >> /etc/apk/repositories && \
    apk add --no-cache bash shadow@community openssh openssh-sftp-server && \
    mkdir -p /var/run/sshd && \
    rm -f /etc/ssh/ssh_host_*key*

COPY files/sshd_config /etc/ssh/sshd_config
COPY files/create-sftp-user /usr/local/bin/
COPY files/entrypoint /
COPY files/users.conf /etc/sftp/

EXPOSE 22

ENTRYPOINT ["/entrypoint"]
