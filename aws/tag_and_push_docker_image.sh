#!/bin/bash

# uses image pwp:master, tags with repository name and pushes it to the repository
# tags and pushes a "master" and a version tag (from pom file)

# for debug set: set -x
set -x
set -o errexit

# DOCKER TAG can be set by first parameter or use "master" if not given:
DOCKER_TAG_NAME=${1:-master}

DOCKER_REPOSITORY_BASE=451926814640.dkr.ecr.eu-central-1.amazonaws.com/wealthpilot
DOCKER_REPOSITORY_APPLICATION=${DOCKER_REPOSITORY_BASE}/application
DOCKER_REPOSITORY_NGINX=${DOCKER_REPOSITORY_BASE}/nginx
DOCKER_REPOSITORY_SFTP=${DOCKER_REPOSITORY_BASE}/b2b-inbox-sftp

APP_VERSION=$(mvn -q \
    -Dexec.executable="echo" \
    -Dexec.args='${project.version}' \
    --non-recursive \
    org.codehaus.mojo:exec-maven-plugin:1.3.1:exec)

# login aws docker registry:
aws ecr get-login-password --region eu-central-1 | docker login --username AWS --password-stdin ${DOCKER_REPOSITORY_BASE}

# tag and push application
docker tag pwp:${DOCKER_TAG_NAME} ${DOCKER_REPOSITORY_APPLICATION}:${APP_VERSION}
docker tag pwp:${DOCKER_TAG_NAME} ${DOCKER_REPOSITORY_APPLICATION}:${DOCKER_TAG_NAME}
docker push ${DOCKER_REPOSITORY_APPLICATION}:${APP_VERSION}
docker push ${DOCKER_REPOSITORY_APPLICATION}:${DOCKER_TAG_NAME}

# cleanup
docker rmi ${DOCKER_REPOSITORY_APPLICATION}:${APP_VERSION}
docker rmi ${DOCKER_REPOSITORY_APPLICATION}:${DOCKER_TAG_NAME}

# tag and push nginx
docker tag pwp-nginx:${DOCKER_TAG_NAME} ${DOCKER_REPOSITORY_NGINX}:${APP_VERSION}
docker tag pwp-nginx:${DOCKER_TAG_NAME} ${DOCKER_REPOSITORY_NGINX}:${DOCKER_TAG_NAME}
docker push ${DOCKER_REPOSITORY_NGINX}:${APP_VERSION}
docker push ${DOCKER_REPOSITORY_NGINX}:${DOCKER_TAG_NAME}

# cleanup
docker rmi ${DOCKER_REPOSITORY_NGINX}:${APP_VERSION}
docker rmi ${DOCKER_REPOSITORY_NGINX}:${DOCKER_TAG_NAME}

# tag and push b2b-inbox-sftp
docker tag pwp-b2b-inbox-sftp:${DOCKER_TAG_NAME} ${DOCKER_REPOSITORY_SFTP}:${APP_VERSION}
docker tag pwp-b2b-inbox-sftp:${DOCKER_TAG_NAME} ${DOCKER_REPOSITORY_SFTP}:${DOCKER_TAG_NAME}
docker push ${DOCKER_REPOSITORY_SFTP}:${APP_VERSION}
docker push ${DOCKER_REPOSITORY_SFTP}:${DOCKER_TAG_NAME}

# cleanup
docker rmi ${DOCKER_REPOSITORY_SFTP}:${APP_VERSION}
docker rmi ${DOCKER_REPOSITORY_SFTP}:${DOCKER_TAG_NAME}

docker logout ${DOCKER_REPOSITORY_BASE}
