
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        IMAGE_NAME = 'raneeem/task-manager'
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
                    withSonarQubeEnv('SonarQube') {
                        withCredentials([string(credentialsId: 'sonarqube-token', variable: 'SONAR_TOKEN')]) {
                            sh '''
                                set -eu
                                task_id=$(sed -n 's/^ceTaskId=//p' target/sonar/report-task.txt)
                                if [ -z "$task_id" ]; then
                                    echo 'SonarQube analysis task ID was not found in target/sonar/report-task.txt.'
                                    exit 1
                                fi

                                echo 'Waiting for SonarQube analysis to finish.'
                                analysis_id=''
                                for attempt in $(seq 1 55); do
                                    task_json=$(curl -fsS --user "$SONAR_TOKEN:" "$SONAR_HOST_URL/api/ce/task?id=$task_id")
                                    task_status=$(printf '%s' "$task_json" | python3 -c "import json,sys; print(json.load(sys.stdin)['task']['status'])")
                                    case "$task_status" in
                                        SUCCESS)
                                            analysis_id=$(printf '%s' "$task_json" | python3 -c "import json,sys; print(json.load(sys.stdin)['task'].get('analysisId', ''))")
                                            break
                                            ;;
                                        FAILED|CANCELED)
                                            printf '%s' "$task_json" | python3 -c "import json,sys; print(json.load(sys.stdin)['task'].get('errorMessage', 'SonarQube analysis failed.'))"
                                            exit 1
                                            ;;
                                        PENDING|IN_PROGRESS)
                                            sleep 5
                                            ;;
                                        *)
                                            echo "Unexpected SonarQube analysis status: $task_status"
                                            exit 1
                                            ;;
                                    esac
                                done

                                if [ -z "$analysis_id" ]; then
                                    echo 'Timed out waiting for SonarQube analysis.'
                                    exit 1
                                fi

                                quality_json=$(curl -fsS --user "$SONAR_TOKEN:" "$SONAR_HOST_URL/api/qualitygates/project_status?analysisId=$analysis_id")
                                quality_status=$(printf '%s' "$quality_json" | python3 -c "import json,sys; print(json.load(sys.stdin)['projectStatus']['status'])")
                                echo "SonarQube Quality Gate: $quality_status"
                                if [ "$quality_status" != 'OK' ]; then
                                    exit 1
                                fi
                            '''
                        }
                    }
                }
            }
        }

        stage('OWASP Dependency-Check') {
            steps {
                sh 'mvn -B -DossindexAnalyzerEnabled=false org.owasp:dependency-check-maven:12.1.0:check'
            }
            post {
                always {
                    archiveArtifacts artifacts: 'target/dependency-check-report.html',
                                     allowEmptyArchive: true
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
                sh 'docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v trivy-cache:/root/.cache/ aquasec/trivy:0.76.2 image --exit-code 1 --severity CRITICAL $IMAGE_NAME:$IMAGE_TAG'
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
                    sed "s|raneeem/task-manager:1.0.0|$IMAGE_NAME:$IMAGE_TAG|g" k8s/deployment.yaml | kubectl apply -f -
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
