pipeline {
    agent any

    environment {
        APP_DIR = "/opt/haobox-api"
        SERVICE_NAME = "haobox-api"
    }

    triggers {
        githubPush()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh '''
                if [ -f "./mvnw" ]; then
                  chmod +x mvnw
                  ./mvnw clean package -DskipTests
                else
                  mvn clean package -DskipTests
                fi
                '''
            }
        }

        stage('Deploy') {
            steps {
                sh '''
                sudo mkdir -p $APP_DIR
                sudo cp target/*.jar $APP_DIR/app.jar
                sudo systemctl restart $SERVICE_NAME
                sudo systemctl status $SERVICE_NAME --no-pager
                '''
            }
        }
    }

    post {
        success {
            echo 'Deploy completed.'
        }
        failure {
            echo 'Deploy failed.'
        }
    }
}