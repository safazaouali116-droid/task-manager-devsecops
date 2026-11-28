
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
            steps { checkout scm }
        }

        stage('Build et tests Maven') {
            steps { sh 'mvn -B clean verify' }
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
                                    echo 'SonarQube analysis task ID was not found.'
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
                                            break ;;
                                        FAILED|CANCELED)
                                            echo 'SonarQube analysis failed.'
                                            exit 1 ;;
                                        PENDING|IN_PROGRESS) sleep 5 ;;
                                        *) echo "Unexpected SonarQube status: $task_status"; exit 1 ;;
                                    esac
                                done
                                if [ -z "$analysis_id" ]; then
                                    echo 'Timed out waiting for SonarQube analysis.'
                                    exit 1
                                fi
                                quality_json=$(curl -fsS --user "$SONAR_TOKEN:" "$SONAR_HOST_URL/api/qualitygates/project_status?analysisId=$analysis_id")
                                quality_status=$(printf '%s' "$quality_json" | python3 -c "import json,sys; print(json.load(sys.stdin)['projectStatus']['status'])")
                                echo "SonarQube Quality Gate: $quality_status"
                                [ "$quality_status" = 'OK' ]
                            '''
                        }
                    }
                }
            }
        }

        stage('OWASP Dependency-Check') {
            steps { sh 'mvn -B -DossindexAnalyzerEnabled=false org.owasp:dependency-check-maven:12.1.0:check' }
            post {
                always {
                    archiveArtifacts artifacts: 'target/dependency-check-report.html', allowEmptyArchive: true
                }
            }
        }

        stage('Construction Docker') {
            steps { sh 'docker build -t $IMAGE_NAME:$IMAGE_TAG -t $IMAGE_NAME:latest .' }
        }

        stage('Scan Trivy') {
            steps { sh 'docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v trivy-cache:/root/.cache/ aquasec/trivy:0.76.2 image --exit-code 1 --severity CRITICAL $IMAGE_NAME:$IMAGE_TAG' }
        }

        stage('Publication Docker Hub') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASSWORD')]) {
                    sh '''
                        echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USER" --password-stdin
                        docker push "$IMAGE_NAME:$IMAGE_TAG"
                        docker push "$IMAGE_NAME:latest"
                        docker logout
                    '''
                }
            }
        }

        stage('Déploiement Kubernetes Docker Desktop') {
            steps {
                sh '''
                    set -eu
                    CONFIG=''
                    for candidate in /var/lib/jenkins/.kube/config /home/*/.kube/config /root/.kube/config /mnt/c/Users/USER/.kube/config; do
                        if [ -r "$candidate" ] && kubectl --kubeconfig="$candidate" config get-contexts -o name 2>/dev/null | grep -Fxq 'docker-desktop'; then
                            CONFIG="$candidate"
                            break
                        fi
                    done
                    if [ -z "$CONFIG" ]; then
                        echo 'No readable kubeconfig with the docker-desktop context was found.'
                        echo 'Checked Jenkins, Ubuntu home, root, and Windows user kubeconfig paths.'
                        exit 1
                    fi
                    export KUBECONFIG="$CONFIG"
                    kubectl config use-context docker-desktop
                    kubectl cluster-info
                    sed "s|raneeem/task-manager:1.0.0|$IMAGE_NAME:$IMAGE_TAG|g" k8s/deployment.yaml | kubectl apply -f -
                    kubectl rollout status deployment/task-manager --timeout=120s
                    kubectl get service task-manager-service
                '''
            }
        }

        stage('Test dynamique OWASP ZAP') {
            steps {
                sh '''
                    set -eu
                    mkdir -p reports
                    chmod 777 reports
                    APP_URL='http://127.0.0.1:18081'
                    echo "$APP_URL" > service-url.txt
                    kubectl port-forward --address 0.0.0.0 service/task-manager-service 18081:8080 > port-forward.log 2>&1 &
                    PF_PID=$!
                    trap 'kill "$PF_PID" 2>/dev/null || true' EXIT
                    ready=0
                    for attempt in $(seq 1 30); do
                        if curl -sS --max-time 2 -o /dev/null "$APP_URL"; then ready=1; break; fi
                        sleep 2
                    done
                    if [ "$ready" -ne 1 ]; then
                        cat port-forward.log
                        echo 'The application did not respond through kubectl port-forward.'
                        exit 1
                    fi
                    docker run --rm --network host \
                      -v "$WORKSPACE/reports:/zap/wrk/:rw" \
                      ghcr.io/zaproxy/zaproxy:stable \
                      zap-baseline.py -t "$APP_URL" \
                      -r zap-report.html -J zap-report.json || ZAP_STATUS=$?
                    if [ ! -s reports/zap-report.html ] || [ ! -s reports/zap-report.json ]; then
                      echo 'ZAP did not produce both report files.'
                      exit 1
                    fi
                    echo "ZAP completed with exit code ${ZAP_STATUS:-0}; reports generated."
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'reports/*,service-url.txt,port-forward.log', allowEmptyArchive: true
                }
            }
        }
    }

    post {
        always { sh 'docker image prune -f || true' }
        success { echo 'Pipeline DevSecOps terminé avec succès.' }
        failure { echo 'Pipeline en échec : consulter les logs de l’étape concernée.' }
    }
}
