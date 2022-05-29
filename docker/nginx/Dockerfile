# nginx for pwp-application:
# build with:
# docker build -t pwp-nginx .
# docker build --no-cache -t pwp-nginx .

FROM nginx:1.21.6-alpine

LABEL "com.datadoghq.ad.logs"='[{"source": "nginx", "service": "nginx"}]'

# debian
#RUN addgroup -gid 500 pwpusers
#RUN useradd --no-create-home --uid 501 --gid pwpusers --shell /bin/false pwp-nginx

# alpine
RUN addgroup -g 500 pwpusers
RUN adduser -u 501 -G pwpusers -D -H -s /sbin/nologin pwp-nginx

# set timezone to Europe/Berlin
ENV TZ=Europe/Berlin

# debian
#RUN apt-get install -y tzdata \
# && cp /usr/share/zoneinfo/$TZ /etc/localtime \
# && apt-get clean \
# && rm -rf /var/lib/apt/lists/*

# alpine
RUN apk add --update tzdata \
 && cp /usr/share/zoneinfo/Europe/Berlin /etc/localtime \
 && rm -rf /var/cache/apk/*

COPY config/nginx.conf /etc/nginx/nginx.conf
COPY config/default.conf /etc/nginx/conf.d/default.conf
COPY config/include_filter_rules_404.conf /etc/nginx/include/include_filter_rules_404.conf
COPY errorpages/maintenance.html /usr/share/nginx/html
COPY errorpages/50x.html /usr/share/nginx/html
COPY ssl /etc/ssl/

EXPOSE 80
EXPOSE 443
