#!/usr/bin/env bash

DOCKER_TAG_NAME=${1:-master}

docker build --pull --no-cache -t pwp:${DOCKER_TAG_NAME} wealthpilot-app/target

docker build --pull --no-cache -t pwp-nginx:${DOCKER_TAG_NAME} docker/nginx
docker build --pull --no-cache -t pwp-b2b-inbox-sftp:${DOCKER_TAG_NAME} docker/b2b-inbox-sftp
