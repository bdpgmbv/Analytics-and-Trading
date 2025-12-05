pipeline {
    agent any
    environment {
        IMAGE_NAME = "my-registry/positionloader"
        // Banks use internal Nexus/Artifactory mirrors
        GRADLE_OPTS = "-Dorg.gradle.daemon=false"
    }
    stages {
        stage('Quality Gates') {
            parallel {
                stage('Static Analysis') {
                    steps {
                        // Fails if code isn't formatted (Google Style)
                        sh './gradlew spotlessCheck'
                    }
                }
                stage('Mutation Testing') {
                    steps {
                        // Fails if tests don't actually catch bugs (Pitest)
                        // Threshold set to 80% mutation coverage
                        sh './gradlew pitest'
                    }
                }
            }
        }

        stage('Unit & Contract Tests') {
            steps {
                // Runs standard JUnit + New Pact Contract Tests
                sh './gradlew test'
            }
        }

        stage('Docker Build') {
            steps {
                sh "docker build -f positionloader/Dockerfile -t ${IMAGE_NAME}:${BUILD_NUMBER} ./positionloader"
            }
        }

        stage('Security Scan') {
            steps {
                // Scan the container for CVEs (Trivy/Clair)
                sh "trivy image --severity HIGH,CRITICAL ${IMAGE_NAME}:${BUILD_NUMBER}"
            }
        }

        stage('Deploy to UAT') {
            steps {
                // Upgrade Helm release with new image
                sh "helm upgrade --install positionloader ./positionloader/charts/positionloader --set image.tag=${BUILD_NUMBER}"
            }
        }
    }
    post {
        always {
            junit '**/build/test-results/test/*.xml'
            // Archive Pitest HTML report for auditors
            publishHTML(target: [
                allowMissing: false,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'positionloader/build/reports/pitest',
                reportFiles: 'index.html',
                reportName: 'Mutation Coverage'
            ])
        }
    }
}