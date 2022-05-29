pipeline {
    agent any

    options {
        timestamps ()
        buildDiscarder(logRotator(numToKeepStr: '50', artifactNumToKeepStr: '30'))
    }

    tools {
        jdk 'openjdk-17'
        maven 'maven_3.3.x'
    }

    environment {
        // need more heap for IT:
        MAVEN_OPTS = '-Xmx768m'
    }

    stages {
        stage('Clean') {
            steps {
                // Clean in separate stage so that the following build steps have a valid clean state to start from.
                withCredentials([string(credentialsId: 'npmjs-token', variable: 'NPM_TOKEN')]) {
                    sh """
                    echo 'Check the @wealthpilot/ngui dependency in src/main/pwp-angularjs/package.json to use a fixed version'
                    if grep -q "@wealthpilot/ngui.*[\\^|~]" src/main/pwp-angularjs/package.json; then
                        echo "ERROR: the version for @wealthpilot/ngui in package.json is not using a fixed version. Namely it contains ^ or ~ before version number!"
                        exit 1
                    fi
                    echo "//registry.npmjs.org/:_authToken=${env.NPM_TOKEN}" > ~/.npmrc
                    echo 'Cleaning...'
                    mvn clean
                    """
                }
            }
        }
        stage('Unit Tests (backend and frontend)...') {
            steps {
                echo 'Building & Unit testing the backend and frontend...'
                sh 'mvn test'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                    junit 'wealthpilot-app/target/jest-coverage/angularjs/junit.xml'
                }
            }
        }
        stage('Test (ITs)') {
            steps {
                echo 'Running integration tests...'
                // run liquibase to init test h2 instance
                sh 'mvn -Dtest=DBInitTest -DfailIfNoTests=false -Dskip.yarn -Dskip.webpack test'
                // -Dmaven.test.failure.ignore must be specified so the junit plugin can mark the build as unstable
                sh 'mvn -T 0.5C -Dskip.yarn -Dskip.webpack -DskipUTs -Dmaven.test.failure.ignore -DexcludedGroups=ExternalApi verify'
            }
            post {
                always {
                    junit '**/target/failsafe-reports/*.xml'
                    jacoco(
                        execPattern: '**/target/test-results/coverage/**/*.exec',
                        classPattern: '**/target/classes'
                    )
                    // cleanup h2 database copies
                    sh 'rm -rf wealthpilot-app/target/h2-test-db/pwp_*.db'
                }
            }
        }
        stage('Sonar') {
            steps {
                withCredentials([string(credentialsId: 'sonar', variable: 'SONAR_API_KEY')]) {
                    script {
                        if (env.GIT_BRANCH != 'master' && env.GIT_BRANCH != 'develop') {
                            echo 'Running sonar analysis with Sonar Gate Check ENABLED...'
                            sh "mvn -Psonar -Dsonar.branch.name=${env.GIT_BRANCH} -Dsonar.login=${SONAR_API_KEY} -Dsonar.coverage.jacoco.xmlReportPaths=\$(find \$(pwd) -name 'jacoco.xml' | xargs echo | sed 's/ /,/g') -Dsonar.qualitygate.wait=true"
                        } else {
                            echo 'Running sonar analysis with Sonar Gate Check DISABLED...'
                            sh "mvn -Psonar -Dsonar.branch.name=${env.GIT_BRANCH} -Dsonar.login=${SONAR_API_KEY} -Dsonar.coverage.jacoco.xmlReportPaths=\$(find \$(pwd) -name 'jacoco.xml' | xargs echo | sed 's/ /,/g')"
                        }
                    }
                }
            }
        }
    }

    post {
        unsuccessful {
            emailext(
                subject: "[Jenkins] ${currentBuild.fullDisplayName} failed!",
                body: "<b>Link to the build:</b><br>${env.BUILD_URL}",
                recipientProviders: [[$class: 'CulpritsRecipientProvider']],
                mimeType: 'text/html',
                attachLog: false
            )
        }
        fixed {
            emailext(
                subject: "[Jenkins] ${currentBuild.fullDisplayName} is fixed!",
                body: "<b>Link to the build:</b><br>${env.BUILD_URL}",
                recipientProviders: [[$class: 'CulpritsRecipientProvider']],
                mimeType: 'text/html'
            )
        }
    }
}
