# Phase 3 — Docker + Kubernetes

> **Status: 🔲 Not started.**

## Goal

Package and deploy the full stack — Spring Boot app + PostgreSQL (with pgvector) — using Docker Compose for local development and Kubernetes (Minikube) for orchestration.

---

## Docker

### Dockerfile

Multi-stage build: the build stage uses the JDK to compile; the run stage uses only the JRE, keeping the final image small and free of compiler tooling. The container runs as a non-root user to limit blast radius if compromised.

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle ./gradle
COPY src ./src
RUN ./gradlew bootJar -x test

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Docker Compose

```yaml
version: '3.8'

services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/llmmemory
      - SPRING_DATASOURCE_USERNAME=postgres
      - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
      - OPENAI_API_KEY=${OPENAI_API_KEY}
    depends_on:
      postgres:
        condition: service_healthy

  postgres:
    image: pgvector/pgvector:pg17
    ports:
      - "5432:5432"
    environment:
      - POSTGRES_DB=llmmemory
      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
```

`pgvector/pgvector:pg17` is the official Postgres image with the pgvector extension pre-installed — no manual `CREATE EXTENSION` step needed in the container. `depends_on` with `condition: service_healthy` prevents the app from starting before Postgres is ready. `OPENAI_API_KEY` and `POSTGRES_PASSWORD` come from the shell — no secrets in the Compose file.

---

## Kubernetes

### Why Separate Deployments

Ingestion (chunking, OpenAI calls, embedding) is CPU/memory intensive and bursty. Search (embed query + pgvector KNN) is lightweight and high-frequency. Separating them into distinct Deployments lets each scale independently:

```bash
kubectl scale deployment llm-memory-ingestion --replicas=5
kubectl scale deployment llm-memory-search --replicas=2
```

### Manifests

#### ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: llm-memory-config
data:
  SPRING_PROFILES_ACTIVE: "kubernetes"
  APP_DB_HOST: "postgres-service"
  APP_DB_PORT: "5432"
  APP_DB_NAME: "llmmemory"
  APP_DB_USER: "postgres"
```

#### Secret

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: llm-memory-secrets
type: Opaque
data:
  OPENAI_API_KEY: <base64-encoded-key>
```

Generate: `echo -n "your-key" | base64`. In production, use a secrets manager (AWS Secrets Manager, Vault) instead of YAML-embedded secrets.

#### App Deployment + Service

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: llm-memory-app
spec:
  replicas: 2
  selector:
    matchLabels:
      app: llm-memory
  template:
    metadata:
      labels:
        app: llm-memory
    spec:
      containers:
        - name: llm-memory
          image: your-dockerhub/llm-memory:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: llm-memory-config
            - secretRef:
                name: llm-memory-secrets
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 15
            periodSeconds: 5
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: llm-memory-service
spec:
  selector:
    app: llm-memory
  ports:
    - port: 80
      targetPort: 8080
  type: LoadBalancer
```

### Actuator Probes

Liveness and readiness probes require Spring Boot Actuator with Kubernetes probe support.

```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

```properties
management.endpoint.health.probes.enabled=true
management.health.livenessState.enabled=true
management.health.readinessState.enabled=true
management.endpoints.web.exposure.include=health,info,metrics
```

Liveness (`/actuator/health/liveness`): if unhealthy, Kubernetes restarts the pod.
Readiness (`/actuator/health/readiness`): if not ready, Kubernetes removes the pod from the Service endpoint list without restarting it.

### Local Cluster (Minikube)

```bash
minikube start
eval $(minikube docker-env)          # point Docker CLI at minikube's daemon
docker build -t llm-memory:latest .
kubectl apply -f k8s/
minikube service llm-memory-service
```

---

## Directory Structure

```
llm-memory-search/
├── Dockerfile
├── docker-compose.yml
└── k8s/
    ├── configmap.yaml
    ├── secret.yaml
    └── app-deployment.yaml
```

