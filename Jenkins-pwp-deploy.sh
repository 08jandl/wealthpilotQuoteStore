#!/usr/bin/env bash

# scripts for https://jenkins.wealthpilot.com/job/pwp-deploy/ Jenkins job

# stop if running
docker-compose -p ${JOB_BASE_NAME} -f ./wealthpilot-app/src/main/docker/app-and-nginx.yml down || true

# start application/nginx/database:
docker-compose -p ${JOB_BASE_NAME} -f ./wealthpilot-app/src/main/docker/app-and-nginx.yml build --pull
docker-compose -p ${JOB_BASE_NAME} -f ./wealthpilot-app/src/main/docker/app-and-nginx.yml up --build
