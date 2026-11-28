
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        IMAGE_NAME = 'safaza/task-manager'
        IMAGE_TAG = "${BUILD_NUMBER}"
        SONAR_HOST_URL = 'http://localhost:9000'
        KUBECONFIG = '/var/lib/jenkins/.kube/config'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build et tests Maven') {
            steps {
                sh 'mvn -B clean verify'
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Analyse SonarQube') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    withCredentials([string(credentialsId: 'sonarqube-token', variable: 'SONAR_TOKEN')]) {
                        sh 'mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:5.0.0.4389:sonar -Dsonar.token=$SONAR_TOKEN -Dsonar.projectKey=Task-Manager'
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('OWASP Dependency-Check') {
            steps {
                sh 'mvn -B org.owasp:dependency-check-maven:12.1.0:check'
            }
            post {
                always {
                    publishHTML(target: [
                        reportDir: 'target',
                        reportFiles: 'dependency-check-report.html',
                        reportName: 'OWASP Dependency-Check',
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true
                    ])
                }
            }
        }

        stage('Construction Docker') {
            steps {
                sh 'docker build -t $IMAGE_NAME:$IMAGE_TAG -t $IMAGE_NAME:latest .'
            }
        }

        stage('Scan Trivy') {
            steps {
                sh 'trivy image --exit-code 1 --severity CRITICAL $IMAGE_NAME:$IMAGE_TAG'
            }
        }

        stage('Publication Docker Hub') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-credentials',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASSWORD'
                )]) {
                    sh '''
                        echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USER" --password-stdin
                        docker push "$IMAGE_NAME:$IMAGE_TAG"
                        docker push "$IMAGE_NAME:latest"
                        docker logout
                    '''
                }
            }
        }

        stage('Déploiement Minikube') {
            steps {
                sh '''
                    sed "s|safaza/task-manager:1.0.0|$IMAGE_NAME:$IMAGE_TAG|g" k8s/deployment.yaml | kubectl apply -f -
                    kubectl rollout status deployment/task-manager --timeout=120s
                '''
            }
        }

        stage('Test dynamique OWASP ZAP') {
            steps {
                sh '''
                    mkdir -p reports
                    minikube service task-manager-service --url > service-url.txt
                    APP_URL=$(head -n 1 service-url.txt)

                    docker run --rm --network host \
                      -v "$WORKSPACE/reports:/zap/wrk/:rw" \
                      ghcr.io/zaproxy/zaproxy:stable \
                      zap-baseline.py -t "$APP_URL" \
                      -r zap-report.html -J zap-report.json || true
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'reports/*,service-url.txt',
                                     allowEmptyArchive: true
                }
            }
        }
    }

    post {
        always {
            sh 'docker image prune -f || true'
        }
        success {
            echo 'Pipeline DevSecOps terminé avec succès.'
        }
        failure {
            echo 'Pipeline en échec : consulter les logs de l’étape concernée.'
        }
    }
}
