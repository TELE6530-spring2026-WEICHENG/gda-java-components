pipeline {
    agent { docker { image 'maven:3.9.12-eclipse-temurin-21-alpine' } }

    stages {
        stage('checkout') {
            steps {
                checkout scm
            }
        }
        stage('build') {
            steps {
                sh 'mvn --version'
            }
        }
    }
}
