// AuditFlow Plugin CI Pipeline
// Jenkinsfile for Jenkins CI builds
// Referenced by jenkins-infra/repository-permissions-updater

pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('Build') {
            steps {
                script {
                    echo 'Building AuditFlow Plugin...'
                }
                sh './mvnw.cmd clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                script {
                    echo 'Running Regression Tests...'
                }
                sh './mvnw.cmd test'
                junit 'target/surefire-reports/*.xml'
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'target/*.hpi', fingerprint: true
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        success {
            echo 'Build succeeded!'
        }
        failure {
            echo 'Build failed. Check logs for details.'
        }
    }
}
