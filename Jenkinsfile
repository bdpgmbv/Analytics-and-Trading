pipeline {
    agent any
    environment {
        IMAGE_NAME = "my-registry/positionloader"
    }
    stages {
        stage('Build & Test') {
            steps {
                sh './gradlew clean build' // Runs Unit + ArchUnit
            }
        }
        stage('Docker Build') {
            steps {
                sh "docker build -f positionloader/Dockerfile -t ${IMAGE_NAME}:${BUILD_NUMBER} ./positionloader"
            }
        }
        stage('Security Scan') {
            steps {
                sh "trivy image ${IMAGE_NAME}:${BUILD_NUMBER}"
            }
        }
        stage('Deploy to Dev') {
            steps {
                sh "helm upgrade --install positionloader ./positionloader/charts/positionloader --set image.tag=${BUILD_NUMBER}"
            }
        }
    }
}