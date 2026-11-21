# Task Manager - Mini-projet DevSecOps RSI

Application REST Java / Spring Boot de gestion des tâches, préparée pour le mini-projet DevSecOps 2026-2027.

## 1. Fonctionnalités
- Créer une tâche
- Lister toutes les tâches
- Consulter une tâche par ID
- Modifier une tâche
- Supprimer une tâche
- Statuts disponibles : `TODO`, `IN_PROGRESS`, `DONE`

## 2. Architecture
- Java 21 et Spring Boot
- Maven pour le build et les tests
- H2 en mémoire pour simplifier le démarrage local
- Docker multi-stage avec utilisateur non-root
- Kubernetes : Deployment, Service, probes et limites de ressources
- Jenkinsfile : build, tests, SonarQube, Quality Gate, Dependency-Check, Docker, Trivy, Docker Hub, Minikube et ZAP

## 3. Prérequis
- JDK 21
- Maven 3.9+
- Git
- Pour les phases suivantes : GitHub, Jenkins, SonarQube, Docker, Trivy, Docker Hub, kubectl, Minikube et OWASP ZAP

## 4. Lancer l'application localement
À la racine du projet :

```bash
mvn clean test
mvn spring-boot:run
```

L'API sera disponible sur `http://localhost:8080`.
La console H2 est disponible sur `http://localhost:8080/h2-console` (JDBC URL : `jdbc:h2:mem:taskdb`, utilisateur `sa`, mot de passe vide).

## 5. Tester l'API
Créer une tâche :

```bash
curl -X POST http://localhost:8080/api/tasks   -H "Content-Type: application/json"   -d '{"title":"Réviser DevOps","description":"Préparer le mini-projet","status":"TODO"}'
```

Lister les tâches :

```bash
curl http://localhost:8080/api/tasks
```

Consulter une tâche :

```bash
curl http://localhost:8080/api/tasks/1
```

Modifier une tâche :

```bash
curl -X PUT http://localhost:8080/api/tasks/1   -H "Content-Type: application/json"   -d '{"title":"Réviser DevSecOps","description":"Préparer le pipeline","status":"IN_PROGRESS"}'
```

Supprimer une tâche :

```bash
curl -X DELETE http://localhost:8080/api/tasks/1
```

Santé de l'application : `http://localhost:8080/actuator/health`.

## 6. Docker
```bash
mvn clean package
docker build -t task-manager:1.0.0 .
docker run --rm -p 8080:8080 task-manager:1.0.0
```

## 7. Kubernetes / Minikube
Avant le déploiement, remplace `DOCKERHUB_USERNAME` dans `k8s/deployment.yaml` par ton identifiant Docker Hub et publie l'image.

```bash
minikube start
kubectl apply -f k8s/deployment.yaml
kubectl get pods
kubectl get services
minikube service task-manager-service
```

## 8. Préparation Jenkins / DevSecOps
Le `Jenkinsfile` est un modèle de pipeline à adapter à la configuration de ta machine Jenkins :
- Configurer un outil Maven dans Jenkins (ou utiliser Maven disponible sur l'agent).
- Configurer SonarQube sous le nom `SonarQube` dans Jenkins et installer le plugin SonarQube Scanner.
- Installer les plugins Jenkins Pipeline, JUnit et HTML Publisher.
- Ajouter les outils `docker`, `trivy`, `kubectl` et `minikube` sur l'agent Jenkins.
- Créer un identifiant Jenkins de type Username/Password nommé `dockerhub-credentials`.
- Remplacer `DOCKERHUB_USERNAME` par le nom du compte Docker Hub.
- Configurer le webhook GitHub ou le déclenchement du job par polling.
- Le Quality Gate nécessite un webhook SonarQube vers `http://JENKINS_URL/sonarqube-webhook/`.

Le pipeline arrête la publication si Trivy détecte une vulnérabilité CRITICAL. Le seuil Dependency-Check est volontairement réglé à 11 pour générer le rapport sans bloquer le premier démarrage ; il faudra définir et justifier le seuil de blocage retenu dans le rapport du projet. Le scan ZAP est lancé en fin de pipeline et son rapport est archivé.

## 9. Structure
```text
task-manager-devsecops/
├── src/main/java/tn/rsi/taskmanager/
│   ├── controller/
│   ├── model/
│   ├── repository/
│   └── service/
├── src/main/resources/application.properties
├── src/test/java/
├── k8s/deployment.yaml
├── Dockerfile
├── Jenkinsfile
├── pom.xml
└── README.md
```

## 10. Livrables du cahier des charges
Le dépôt contient le code, les tests, le Dockerfile, les manifestes Kubernetes, le Jenkinsfile et ce README. Les rapports SonarQube, Dependency-Check, Trivy et ZAP, l'image Docker Hub et le rapport technique seront produits pendant les phases de configuration et d'exécution.
