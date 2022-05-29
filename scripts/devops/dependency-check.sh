#!/bin/bash

# exit on error
set -e

MAVEN_BIN=/var/opt/jenkins-agent/tools/hudson.tasks.Maven_MavenInstallation/maven_3.3.x/bin/mvn
if [ ! -f $MAVEN_BIN ]; then
  echo "Maven installation of jenkins not found, using default maven from path"
  MAVEN_BIN=mvn
fi

# check if yarn is there, if not install
if ! command -v yarn &> /dev/null
then
    echo
    echo "Install yarn globally, as this is required by the dependency check"
    npm install -g yarn
fi

echo
echo "Install the node dependencies"
# must go in the correct folder for installing...
cd wealthpilot-app/src/main/pwp-angularjs/
# remove obsolete package-lock, which should not be used anymore
rm -rf package-lock.json
yarn install
cd ../../../..

echo
echo "Execute dependency check"
$MAVEN_BIN dependency-check:aggregate
