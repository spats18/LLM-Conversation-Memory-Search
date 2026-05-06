# Phase 4 — Docker + Kubernetes

## Goal

Make the application **production-deployable** and demonstrate containerization and orchestration alongside the application code.

---

## Part A: Docker

### What Gets Containerized

You have two things that need to run:
1. Your Spring Boot application
2. Redis Stack (already has an official image)

### Dockerfile for Spring Boot

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

# Don't run as root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Why multi-stage build?** The build stage needs the JDK (compiler). The run stage only needs the JRE (runtime). This keeps the final image small — no compiler tools in production.

**Why non-root user?** Security. Running as root inside a container is a real risk if the container is ever compromised.

### Docker Compose for Local Development

```yaml
version: '3.8'

services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/llmmemory
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - OPENAI_API_KEY=${OPENAI_API_KEY}
    depends_on:
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  redis:
    image: redis/redis-stack:latest
    ports:
      - "6379:6379"
      - "8001:8001"    # RedisInsight UI
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  redis_data:
```

**Key decisions to explain:**
- `depends_on` with `condition: service_healthy` — the app does not start until Redis is actually ready, not just started
- Named volume `redis_data` — Redis data persists across container restarts
- Environment variables — no secrets hardcoded in the file; `OPENAI_API_KEY` comes from your shell

### Running Locally

```bash
export OPENAI_API_KEY=your-key-here
docker compose up --build
```

Everything starts. Your app is at `http://localhost:8080`. RedisInsight is at `http://localhost:8001`.

---

## Part B: Kubernetes

### Concepts to Understand Before Writing YAML

**Pod:** The smallest deployable unit. One or more containers running together.

**Deployment:** Manages a set of identical Pods. Handles rolling updates and rollbacks.

**Service:** A stable network endpoint in front of a set of Pods. Pods come and go; the Service address stays the same.

**ConfigMap:** Non-sensitive configuration (Redis host, app settings).

**Secret:** Sensitive configuration (API keys, passwords). Base64-encoded in YAML, but stored encrypted in etcd.

**Liveness probe:** Is the app alive? If this fails, Kubernetes restarts the Pod.

**Readiness probe:** Is the app ready to receive traffic? If this fails, Kubernetes removes the Pod from the Service (stops sending it requests) but does not restart it.

---

### The Scaling Argument (Important for Interviews)

Your app has two distinct workloads:

**Ingestion:** CPU and memory intensive. Fetching URLs, chunking text, calling OpenAI, generating embeddings. Slow and bursty — someone submits 10 conversations at once.

**Search API:** Fast and lightweight. Embed a query (one call), search Redis (milliseconds), return results. High frequency, low resource.

In Kubernetes, you can scale these independently:

```bash
kubectl scale deployment llm-memory-ingestion --replicas=5  # Heavy ingestion load
kubectl scale deployment llm-memory-search --replicas=2     # Search is fast, 2 is enough
```

This is the reason to separate the ingestion service from the search service into different Deployments. You are not doing this because Kubernetes exists — you are doing it because the workloads have different resource profiles.

---

### Kubernetes Manifests

#### ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: llm-memory-config
data:
  REDIS_HOST: "redis-service"
  REDIS_PORT: "6379"
  SPRING_PROFILES_ACTIVE: "kubernetes"
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

Generate base64: `echo -n "your-key" | base64`

In practice, you would use a secrets manager (AWS Secrets Manager, Vault) instead of storing secrets in YAML files. For Phase 4, the YAML approach is fine to learn the concept.

#### Redis Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
spec:
  replicas: 1
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
        - name: redis
          image: redis/redis-stack:latest
          ports:
            - containerPort: 6379
          volumeMounts:
            - name: redis-data
              mountPath: /data
      volumes:
        - name: redis-data
          persistentVolumeClaim:
            claimName: redis-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: redis-service
spec:
  selector:
    app: redis
  ports:
    - port: 6379
      targetPort: 6379
```

#### App Deployment

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

---

### Spring Boot Actuator (Required for K8s Probes)

Add to `build.gradle.kts`:
```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

Add to `application.properties`:
```properties
management.endpoint.health.probes.enabled=true
management.health.livenessState.enabled=true
management.health.readinessState.enabled=true
management.endpoints.web.exposure.include=health,info,metrics
```

This gives you `/actuator/health/liveness` and `/actuator/health/readiness` for free.

---

### Local Kubernetes with Minikube

You do not need a cloud cluster. Run Kubernetes locally:

```bash
# Install minikube
brew install minikube  # macOS
# or download from minikube.sigs.k8s.io

# Start a local cluster
minikube start

# Point Docker to minikube's registry (so your image is available)
eval $(minikube docker-env)
docker build -t llm-memory:latest .

# Apply manifests
kubectl apply -f k8s/

# Access the service
minikube service llm-memory-service
```

---

### Directory Structure for Phase 4

```
llm-memory-search/
├── Dockerfile
├── docker-compose.yml
└── k8s/
    ├── configmap.yaml
    ├── secret.yaml
    ├── redis-deployment.yaml
    ├── redis-pvc.yaml
    └── app-deployment.yaml
```

---

## Phase 4 Done When...

- [ ] `docker compose up` starts the entire stack with one command
- [ ] The Docker image uses a multi-stage build
- [ ] The app runs as a non-root user in the container
- [ ] Kubernetes manifests deploy the app and Redis on Minikube
- [ ] Liveness and readiness probes respond correctly
- [ ] No secrets are hardcoded in any YAML file
