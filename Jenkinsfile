pipeline {
    agent any
    environment {
        IMAGE_NAME = "my-registry/positionloader"
        GRADLE_OPTS = "-Dorg.gradle.daemon=false"
    }
    stages {
        stage('Quality Gates') {
            parallel {
                stage('Static Analysis') {
                    steps { sh './gradlew spotlessCheck' }
                }
                stage('Mutation Testing') {
                    steps { sh './gradlew pitest' }
                }
            }
        }
        stage('Unit & Contract Tests') {
            steps { sh './gradlew test' }
        }
        stage('Docker Build') {
            steps {
                sh "docker build -f positionloader/Dockerfile -t ${IMAGE_NAME}:${BUILD_NUMBER} ./positionloader"
            }
        }
    }
    post {
        always {
            junit '**/build/test-results/test/*.xml'
            publishHTML(target: [
                allowMissing: false, alwaysLinkToLastBuild: true, keepAll: true,
                reportDir: 'positionloader/build/reports/pitest',
                reportFiles: 'index.html', reportName: 'Mutation Coverage'
            ])
        }
    }
}