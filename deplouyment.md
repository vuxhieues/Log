# 🏗️ APPLICATION BUILD & DEPLOYMENT - HƯỚNG DẪN

> **Mục tiêu:** Hiểu cách build và deploy applications, đặc biệt là **Java applications** cần compile trước khi containerize

---

## 📑 **MỤC LỤC**

1. [**LUỒNG DEPLOY - BACKEND FLOW** ⭐](#0-luồng-deploy---backend-flow)
2. [Tổng quan Build Process](#1-tổng-quan-build-process)
3. [Java Build Flow (Maven/Gradle)](#2-java-build-flow-mavengradl)
4. [So sánh với các ngôn ngữ khác](#3-so-sánh-với-các-ngôn-ngữ-khác)
5. [Dockerfile Generation](#4-dockerfile-generation)
6. [Jenkins Pipeline Stages](#5-jenkins-pipeline-stages)
7. [Multi-stage vs Single-stage Build](#6-multi-stage-vs-single-stage-build)
8. [Q&A](#7-qa-nhanh)

---

## 0️⃣ LUỒNG DEPLOY - BACKEND FLOW ⭐

> **Mô tả đơn giản:** Khi user click "Deploy", backend xử lý như thế nào?

---

### **🎯 TỔNG QUAN 1 CÂU**

```
User click Deploy 
  → Backend tạo Dockerfile 
  → Gửi sang Jenkins 
  → Jenkins build & chạy container
```

---

### **📋 LUỒNG CHI TIẾT (10 BƯỚC)**

```
┌──────────────────────────────────────────────────────────────┐
│              BACKEND DEPLOYMENT FLOW                          │
└──────────────────────────────────────────────────────────────┘

STEP 1: User Action (Frontend)
┌─────────────────┐
│ User clicks     │
│ "Deploy" button │
└────────┬────────┘
         │ POST /api/v1/applications/deploy
         ↓

STEP 2: Backend nhận request (Controller)
┌──────────────────────────────────────────┐
│ ApplicationController.deployApplication()│
│   ↓                                      │
│ 1. Validate request                      │
│ 2. Extract userId từ JWT                 │
│ 3. Log: "Deploying application..."       │
└────────┬─────────────────────────────────┘
         │
         ↓

STEP 3: Application Service xử lý (Business Logic)
┌──────────────────────────────────────────┐
│ ApplicationService.deployApplication()   │
│   ↓                                      │
│ 1. Tìm application trong database        │
│ 2. Verify user ownership                 │
│ 3. Check application status              │
│ 4. Create Build record (status: PENDING) │
└────────┬─────────────────────────────────┘
         │
         ↓

STEP 4: Generate Dockerfile (DockerfileGeneratorService)
┌──────────────────────────────────────────┐
│ DockerfileGeneratorService               │
│   ↓                                      │
│ 1. Detect language (Java/Python/Node.js)│
│ 2. Detect build tool (Maven/Gradle)     │
│ 3. Generate Dockerfile theo template     │
│    - Java: Multi-stage (Maven + JRE)    │
│    - Python: Single-stage (pip install) │
│    - Node.js: Single-stage (npm install)│
│   ↓                                      │
│ Output: Dockerfile content (String)      │
└────────┬─────────────────────────────────┘
         │
         ↓

STEP 5: Prepare Deployment Payload
┌──────────────────────────────────────────┐
│ JenkinsService.triggerDeployment()       │
│   ↓                                      │
│ 1. Get repository info (clone URL)      │
│ 2. Handle private repo authentication    │
│    - GitHub App token (1-hour TTL)      │
│    - Inject: https://x-access-token:    │
│              TOKEN@github.com/repo.git   │
│ 3. Build JSON payload:                   │
│    {                                     │
│      applicationId: "uuid",              │
│      request: {                          │
│        repoCloneUrl: "...",              │
│        branch: "main",                   │
│        language: "java",                 │
│        buildCommand: "mvn package",      │
│        envVars: [{key, value}],          │
│        databaseConfig: {...}             │
│      },                                  │
│      dockerfile: "FROM maven:..."        │
│    }                                     │
└────────┬─────────────────────────────────┘
         │
         ↓

STEP 6: Trigger Jenkins Build
┌──────────────────────────────────────────┐
│ HTTP Request to Jenkins                  │
│   ↓                                      │
│ 1. Get Jenkins CSRF crumb               │
│    GET /crumbIssuer/api/json             │
│    → {crumb: "abc123"}                   │
│                                          │
│ 2. POST to Jenkins job                   │
│    POST /job/deploy-application/         │
│         buildWithParameters              │
│                                          │
│    Headers:                              │
│    - Authorization: Basic base64(        │
│        username:api_token)               │
│    - Jenkins-Crumb: abc123               │
│                                          │
│    Body (form-data):                     │
│    - applicationId: uuid                 │
│    - deploymentData: JSON.stringify()    │
│    - dockerfile: Dockerfile content      │
│                                          │
│ 3. Jenkins returns 201 Created           │
│    → Build queued                        │
└────────┬─────────────────────────────────┘
         │
         ↓

STEP 7: Update Build Status
┌──────────────────────────────────────────┐
│ Back to ApplicationService               │
│   ↓                                      │
│ 1. Update Build record:                  │
│    - status: IN_PROGRESS                 │
│    - triggeredAt: now()                  │
│ 2. Save to database                      │
│ 3. Return BuildDTO to frontend           │
└────────┬─────────────────────────────────┘
         │
         ↓

STEP 8: Jenkins Pipeline Execution (Async)
┌──────────────────────────────────────────┐
│ Jenkinsfile Pipeline (8 stages)          │
│                                          │
│ Stage 1: Parse deployment data           │
│   → Extract all params                   │
│                                          │
│ Stage 2: Clone repository                │
│   → git clone with token                 │
│                                          │
│ Stage 3: Setup database (if needed)      │
│   → CREATE DATABASE, USER, GRANT         │
│                                          │
│ Stage 4: Build application (Java only)   │
│   → mvn clean package -DskipTests        │
│   → target/app.jar ✅                   │
│                                          │
│ Stage 5: Write Dockerfile                │
│   → writeFile(dockerfile content)        │
│                                          │
│ Stage 6: Build Docker image              │
│   → docker build -t app:uuid .           │
│                                          │
│ Stage 7: Deploy container                │
│   → docker stop/rm old container         │
│   → docker run -d --name app-uuid        │
│                                          │
│ Stage 8: Run migrations (if DB exists)   │
│   → docker exec app flask db upgrade    │
│                                          │
│ ⚠️ MỖI STAGE LOG VÀO REDIS STREAMS      │
│    redis-cli XADD jenkins:logs:stream    │
└────────┬─────────────────────────────────┘
         │
         ↓

STEP 9: Real-time Log Streaming
┌──────────────────────────────────────────┐
│ Frontend → Backend SSE → Redis           │
│                                          │
│ Frontend:                                │
│   EventSource('/deploy/stream/uuid')     │
│                                          │
│ Backend:                                 │
│   BuildLogController.streamBuildLogs()   │
│   - Read từ Redis Stream                 │
│   - Push qua SSE                         │
│                                          │
│ User sees logs real-time:                │
│   [INFO] Cloning repository...           │
│   [INFO] Building application...         │
│   [INFO] Building Docker image...        │
│   [SUCCESS] Application deployed! 🚀     │
└────────┬─────────────────────────────────┘
         │
         ↓

STEP 10: Deployment Complete
┌──────────────────────────────────────────┐
│ Jenkins callback (webhook) hoặc polling  │
│   ↓                                      │
│ 1. Update Build record:                  │
│    - status: SUCCESS / FAILED            │
│    - completedAt: now()                  │
│    - duration: endTime - startTime       │
│ 2. Update Application:                   │
│    - status: RUNNING / FAILED            │
│    - containerName: app-uuid             │
│    - port: 8080                          │
│ 3. Save to database                      │
│ 4. Frontend polls /builds/{id}/status    │
│    → Redirect to app monitoring page     │
└──────────────────────────────────────────┘

✅ APPLICATION RUNNING!
   → User có thể access: http://server:8080
   → Monitoring: Real-time metrics
   → Logs: View container logs
```

---

### **🔑 CÁC CLASS/FILE QUAN TRỌNG**

| File | Vai trò | Method chính |
|------|---------|--------------|
| **ApplicationController.java** | REST API endpoint | `deployApplication()` |
| **ApplicationService.java** | Business logic | `deployApplication()`, create Build record |
| **JenkinsService.java** | Trigger Jenkins | `triggerDeployment()`, build payload |
| **DockerfileGeneratorService.java** | Generate Dockerfile | `generateDockerfile()`, detect language |
| **GithubAppService.java** | GitHub authentication | `getInstallationAccessToken()` |
| **BuildLogController.java** | Stream logs | `streamBuildLogs()` via SSE |
| **Jenkinsfile** | Pipeline orchestration | 8 stages (clone, build, deploy) |

---

### **💡 ĐIỂM QUAN TRỌNG**

**1. Backend KHÔNG build/deploy trực tiếp**
```
Backend chỉ:
  ✅ Generate Dockerfile
  ✅ Prepare deployment config
  ✅ Trigger Jenkins API
  ❌ KHÔNG chạy docker build
  ❌ KHÔNG chạy git clone
```

**2. Jenkins là "worker"**
```
Jenkins thực hiện:
  ✅ Clone repository
  ✅ Build application (mvn/gradle/npm)
  ✅ Build Docker image
  ✅ Run container
  ✅ Setup database
  ✅ Run migrations
```

**3. Communication flow**
```
Backend → Jenkins: HTTP POST (trigger)
Jenkins → Redis: XADD (logs)
Backend → Frontend: SSE (stream logs)
```

**4. Async processing**
```
User không chờ deploy xong mới nhận response!

Flow:
  User click Deploy
    ↓
  Backend returns ngay: {buildId, status: "PENDING"}
    ↓
  Frontend polls hoặc SSE để theo dõi progress
    ↓
  Jenkins chạy background (2-5 phút)
    ↓
  Build complete → Update status → Frontend redirect
```

---

### **⏱️ TIMELINE ESTIMATE**

| Stage | Time | Note |
|-------|------|------|
| Backend processing | ~1-2s | Generate Dockerfile, trigger Jenkins |
| Jenkins queue | ~5-10s | Wait for available executor |
| Clone repository | ~10-30s | Depends on repo size |
| Build application (Java) | ~2-5min | Maven download deps + compile |
| Build application (Python) | ~30-60s | pip install |
| Build Docker image | ~1-3min | Depends on layers |
| Deploy container | ~5-10s | docker run |
| Run migrations | ~10-30s | Database queries |
| **TOTAL** | **3-8 min** | Java app, first deploy |

**Subsequent deploys faster:**
- Maven cache: 2-5 min → 30-60s
- Docker layer cache: 3 min → 30s
- No database creation: -30s

---

### **🎓 TÓM TẮT CHO BẢO VỆ**

**Q: Backend làm gì khi user deploy?**

**A (30 giây):**
> "Backend nhận request từ frontend, **generate Dockerfile** tự động dựa trên ngôn ngữ (Java/Python/Node.js), sau đó **trigger Jenkins** qua HTTP API. Backend **không build trực tiếp** mà ủy thác cho Jenkins. Jenkins sẽ clone repo, build application (Maven cho Java), build Docker image, và chạy container. Logs được stream real-time qua Redis Streams sang frontend. Backend chỉ đóng vai trò **orchestrator**, còn Jenkins là **worker** thực sự."

**Key Points:**
1. ✅ Backend = Orchestrator (trigger, không build)
2. ✅ Jenkins = Worker (clone, build, deploy)
3. ✅ Dockerfile tự động generated theo language
4. ✅ Logs real-time qua Redis → SSE
5. ✅ Async processing (không block user)

---

## 1️⃣ TỔNG QUAN BUILD PROCESS

### **Hai loại ngôn ngữ:**

```
┌─────────────────────────────────────────────────────────────┐
│               COMPILED LANGUAGES (Java, Go)                  │
│                                                               │
│  Source Code → BUILD STEP → Binary/JAR → Docker Image       │
│                    ↑                                          │
│              CẦN COMPILE TRƯỚC                               │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│          INTERPRETED LANGUAGES (Python, Node.js, PHP)        │
│                                                               │
│  Source Code → Docker Image (build trong container)          │
│                                                               │
│              KHÔNG CẦN BUILD TRƯỚC                           │
└─────────────────────────────────────────────────────────────┘
```

---

## 2️⃣ JAVA BUILD FLOW (Maven/Gradle)

### **A. Tại sao Java cần BUILD TRƯỚC?**

**Java là compiled language:**
```
.java files (source code) 
    → javac compiler 
    → .class files (bytecode) 
    → JAR file (packaged bytecode + resources)
    → Docker container
```

**Không thể chạy trực tiếp `.java` files như Python/Node.js!**

---

### **B. Java Build Flow trong Jenkins**

```
┌──────────────────────────────────────────────────────────────┐
│                   JAVA APPLICATION DEPLOY                     │
└──────────────────────────────────────────────────────────────┘

STEP 1: Clone Repository
┌─────────────────┐
│ git clone       │
│ repo_uuid/      │
│   ├── src/      │
│   ├── pom.xml   │ ← Maven config
│   └── Dockerfile│
└─────────────────┘

STEP 2: Build JAR (TRONG CONTAINER hoặc HOST)
┌─────────────────────────────────────────────┐
│ OPTION 1: Multi-stage Build (in Docker)    │
│ ------------------------------------------- │
│ Stage 1 (Builder):                          │
│   FROM maven:3.9-eclipse-temurin-21        │
│   COPY pom.xml .                            │
│   RUN mvn dependency:go-offline  ← Cache   │
│   COPY src/ src/                            │
│   RUN mvn clean package -DskipTests        │
│        ↓                                    │
│   target/myapp.jar  ✅                     │
│                                             │
│ Stage 2 (Runtime):                          │
│   FROM eclipse-temurin:21-jre              │
│   COPY --from=builder /app/target/*.jar .  │
│   ENTRYPOINT ["java", "-jar", "app.jar"]   │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│ OPTION 2: Pre-build (on Host)              │
│ ------------------------------------------- │
│ Jenkins Host:                               │
│   cd repo_uuid/                             │
│   mvn clean package -DskipTests            │
│        ↓                                    │
│   target/myapp.jar  ✅                     │
│                                             │
│ Dockerfile (Single-stage):                 │
│   FROM eclipse-temurin:21-jre              │
│   COPY target/myapp.jar app.jar            │
│   ENTRYPOINT ["java", "-jar", "app.jar"]   │
└─────────────────────────────────────────────┘

STEP 3: Build Docker Image
docker build -t myapp:uuid .
    ↓
Docker Image với JAR inside ✅

STEP 4: Run Container
docker run -d -p 8080:8080 myapp:uuid
    ↓
Application running 🚀
```

---

### **C. Maven Build Command Chi Tiết**

```bash
mvn clean package -DskipTests

# Giải thích:
# mvn        → Maven command-line tool
# clean      → Xóa target/ folder (clean slate)
# package    → Compile + test + package thành JAR
# -DskipTests → Skip unit tests (faster build)

# Output:
# target/
#   ├── myapp-1.0.0.jar          ← Executable JAR
#   ├── classes/                 ← Compiled .class files
#   └── maven-archiver/
```

**pom.xml (Maven config):**
```xml
<project>
    <groupId>com.example</groupId>
    <artifactId>myapp</artifactId>
    <version>1.0.0</version>
    
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**Gradle equivalent:**
```bash
gradle clean build -x test

# build.gradle
plugins {
    id 'org.springframework.boot' version '3.2.0'
    id 'java'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
}

# Output: build/libs/myapp-1.0.0.jar
```

---

### **D. Code trong Jenkins Pipeline**

**Jenkinsfile - Java Build Stage:**
```groovy
stage('Build Application') {
    when {
        expression { env.LANGUAGE == 'java' }
    }
    steps {
        script {
            def buildContext = "${REPO_DIR}/${env.ROOT_DIR ?: ''}"
            
            // Check build tool
            def isMaven = fileExists("${buildContext}/pom.xml")
            def isGradle = fileExists("${buildContext}/build.gradle")
            
            if (isMaven) {
                sh """
                cd ${buildContext}
                
                # ⚙️ BUILD JAR với Maven
                mvn clean package -DskipTests 2>&1 | while read line; do
                    echo "\$line"
                    redis-cli XADD jenkins:logs:stream '*' \
                        applicationId "${params.applicationId}" \
                        message "\$line"
                done
                
                # ✅ Verify JAR created
                if [ ! -f target/*.jar ]; then
                    echo "ERROR: JAR file not found after build"
                    exit 1
                fi
                
                echo "✓ JAR built successfully: \$(ls target/*.jar)"
                """
            } else if (isGradle) {
                sh """
                cd ${buildContext}
                
                # ⚙️ BUILD JAR với Gradle
                ./gradlew clean build -x test
                
                echo "✓ JAR built: \$(ls build/libs/*.jar)"
                """
            }
        }
    }
}
```

---

## 3️⃣ SO SÁNH VỚI CÁC NGÔN NGỮ KHÁC

### **A. Python (Interpreted)**

```dockerfile
# Python KHÔNG cần build trước
FROM python:3.11-slim
WORKDIR /app

# Copy source code trực tiếp
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY . .

# Chạy source code trực tiếp
CMD ["python", "app.py"]  ← .py file, không cần compile
```

**Jenkins chỉ cần:**
```groovy
stage('Deploy Python App') {
    steps {
        sh """
        # Clone repo
        git clone ${REPO_URL}
        
        # Write Dockerfile (no build step)
        # Build Docker image directly
        docker build -t app:uuid .
        
        # Run container
        docker run -d app:uuid
        """
    }
}
```

---

### **B. Node.js (Interpreted)**

```dockerfile
# Node.js build dependencies, không phải app
FROM node:20-alpine
WORKDIR /app

COPY package*.json .
RUN npm ci --only=production  ← Install dependencies

COPY . .  ← Copy source code

CMD ["npm", "start"]  ← Run .js files directly
# OR: CMD ["node", "index.js"]
```

**Có `npm run build` cho frontend frameworks (React, Next.js):**
```dockerfile
# Multi-stage cho Next.js
FROM node:20-alpine AS builder
COPY package*.json .
RUN npm ci
COPY . .
RUN npm run build  ← Build static assets, KHÔNG phải compile app

FROM node:20-alpine
COPY --from=builder /app/.next .next
COPY --from=builder /app/node_modules node_modules
CMD ["npm", "start"]
```

---

### **C. Go (Compiled)**

```dockerfile
# Go cũng cần compile như Java
FROM golang:1.21-alpine AS builder
WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download  ← Cache dependencies

COPY . .
RUN go build -o main .  ← COMPILE thành binary

# Runtime stage (tiny image)
FROM alpine:latest
WORKDIR /app
COPY --from=builder /app/main .
CMD ["./main"]  ← Run compiled binary
```

**Tương tự Java: Source code → Compile → Binary**

---

### **D. So sánh Build Steps**

| Language | Build Step | Output | Dockerfile Stages | Reason |
|----------|-----------|--------|-------------------|--------|
| **Java** | `mvn package` | JAR file | Multi-stage (recommended) | Compile .java → .class → JAR |
| **Go** | `go build` | Binary executable | Multi-stage (recommended) | Compile .go → binary |
| **Python** | None | Source .py files | Single-stage | Interpreted, no compile |
| **Node.js** | `npm install` | node_modules/ | Single-stage (usually) | Interpreted, install deps only |
| **PHP** | None | Source .php files | Single-stage | Interpreted, no compile |

---

## 4️⃣ DOCKERFILE GENERATION

### **A. Backend Code - DockerfileGeneratorService**

**File:** `DockerfileGeneratorService.java`

```java
public String generateDockerfile(CreateApplicationRequest request) {
    String language = request.getLanguage();
    
    switch (language.toLowerCase()) {
        case "java":
            return generateJavaDockerfile(request);
        case "python":
            return generatePythonDockerfile(request);
        case "nodejs":
            return generateNodeDockerfile(request);
        // ... other languages
    }
}

private String generateJavaDockerfile(CreateApplicationRequest request) {
    String buildTool = detectBuildTool(request);  // Maven or Gradle
    boolean useMultiStage = request.getUseMultiStageBuild() != null 
        ? request.getUseMultiStageBuild() 
        : true;  // Default: multi-stage
    
    if (useMultiStage) {
        return generateJavaMultiStageDockerfile(buildTool, request);
    } else {
        return generateJavaSingleStageDockerfile(buildTool, request);
    }
}
```

---

### **B. Java Dockerfile Templates**

#### **Option 1: Multi-stage Build (Recommended) ✅**

```dockerfile
# ============================================
# STAGE 1: BUILD
# ============================================
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy dependency files first (cache optimization)
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# Download dependencies (cached if pom.xml unchanged)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build JAR
RUN mvn clean package -DskipTests -B

# ============================================
# STAGE 2: RUNTIME
# ============================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Expose port
EXPOSE 8080

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Advantages:**
- ✅ **Small image size**: JRE only (không có Maven, source code)
- ✅ **Security**: Không expose build tools trong production
- ✅ **Self-contained**: Không cần pre-build trên host
- ✅ **Reproducible**: Build environment consistent

**Disadvantages:**
- ❌ **Slower builds**: Mỗi lần build phải compile lại
- ❌ **Larger build context**: Upload source code + dependencies

---

#### **Option 2: Single-stage Build (Faster for dev) ⚡**

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy pre-built JAR
COPY target/myapp-1.0.0.jar app.jar

# Environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Advantages:**
- ✅ **Faster Docker build**: Chỉ copy JAR
- ✅ **Smaller build context**: Không cần source code
- ✅ **Good for CI/CD**: Build JAR once, reuse

**Disadvantages:**
- ❌ **Requires pre-build**: Phải run `mvn package` trước
- ❌ **Host dependency**: Cần Maven/Gradle trên Jenkins host

---

### **C. Python Dockerfile (Simple)**

```dockerfile
FROM python:3.11-slim
WORKDIR /app

# Copy requirements first (cache optimization)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy source code
COPY . .

# Expose port
EXPOSE 5000

# Run application
CMD ["python", "app.py"]
```

**No build step needed! ✅**

---

### **D. Node.js Dockerfile**

```dockerfile
FROM node:20-alpine
WORKDIR /app

# Copy package files (cache optimization)
COPY package*.json .
RUN npm ci --only=production

# Copy source code
COPY . .

# Expose port
EXPOSE 3000

# Run application
CMD ["npm", "start"]
```

**Build step = dependency installation, not compilation ✅**

---

## 5️⃣ JENKINS PIPELINE STAGES

### **Full Pipeline Flow**

```groovy
pipeline {
    agent any
    
    stages {
        // ============================================
        // STAGE 1: Clone Repository (ALL languages)
        // ============================================
        stage('Clone Repository') {
            steps {
                sh "git clone -b ${BRANCH} ${REPO_URL} ${REPO_DIR}"
            }
        }
        
        // ============================================
        // STAGE 2: Setup Database (if needed)
        // ============================================
        stage('Setup Database') {
            when {
                expression { env.DB_TYPE != 'none' }
            }
            steps {
                sh """
                docker exec user-postgres psql -c "CREATE DATABASE ${DB_NAME};"
                """
            }
        }
        
        // ============================================
        // STAGE 3: Build Application (JAVA ONLY) ⭐
        // ============================================
        stage('Build Application') {
            when {
                expression { env.LANGUAGE == 'java' }
            }
            steps {
                script {
                    def buildContext = "${REPO_DIR}/${env.ROOT_DIR ?: ''}"
                    
                    if (fileExists("${buildContext}/pom.xml")) {
                        sh """
                        cd ${buildContext}
                        mvn clean package -DskipTests
                        """
                    } else if (fileExists("${buildContext}/build.gradle")) {
                        sh """
                        cd ${buildContext}
                        ./gradlew clean build -x test
                        """
                    }
                }
            }
        }
        
        // ============================================
        // STAGE 4: Write Dockerfile (ALL languages)
        // ============================================
        stage('Write Dockerfile') {
            steps {
                script {
                    def dockerfilePath = "${REPO_DIR}/Dockerfile"
                    writeFile file: dockerfilePath, text: params.dockerfile
                    // params.dockerfile = generated by backend
                }
            }
        }
        
        // ============================================
        // STAGE 5: Build Docker Image (ALL languages)
        // ============================================
        stage('Build Docker Image') {
            steps {
                sh """
                docker build -t ${APP_NAME}:${applicationId} ${REPO_DIR}
                """
            }
        }
        
        // ============================================
        // STAGE 6: Deploy Container (ALL languages)
        // ============================================
        stage('Deploy Container') {
            steps {
                sh """
                docker stop ${APP_NAME}-${applicationId} || true
                docker rm ${APP_NAME}-${applicationId} || true
                
                docker run -d \
                  --name ${APP_NAME}-${applicationId} \
                  --network app-network \
                  -p ${EXPOSED_PORT}:${EXPOSED_PORT} \
                  ${ENV_VARS} \
                  ${APP_NAME}:${applicationId}
                """
            }
        }
        
        // ============================================
        // STAGE 7: Run Migrations (if DB exists)
        // ============================================
        stage('Run Database Migrations') {
            when {
                expression { env.DB_TYPE != 'none' }
            }
            steps {
                sh """
                # Wait for container to be ready
                sleep 5
                
                # Run migrations based on language
                if [ "${LANGUAGE}" = "python" ]; then
                    docker exec ${CONTAINER_NAME} flask db upgrade
                elif [ "${LANGUAGE}" = "nodejs" ]; then
                    docker exec ${CONTAINER_NAME} npx sequelize-cli db:migrate
                elif [ "${LANGUAGE}" = "java" ]; then
                    # Spring Boot auto-migration with Flyway/Liquibase
                    echo "Migrations handled by application"
                fi
                """
            }
        }
    }
}
```

**Key Difference: Stage 3 chỉ chạy cho Java! ⭐**

---

## 6️⃣ MULTI-STAGE VS SINGLE-STAGE BUILD

### **A. Khi nào dùng Multi-stage?**

**Use cases:**
- ✅ Production deployments (security, image size)
- ✅ CI/CD pipelines (reproducible builds)
- ✅ No build tools on host
- ✅ Complex dependencies (native libs)

**Example: Java with native dependencies**
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
RUN apt-get update && apt-get install -y libssl-dev
COPY . .
RUN mvn package

FROM eclipse-temurin:21-jre-alpine
COPY --from=builder /app/target/*.jar app.jar
CMD ["java", "-jar", "app.jar"]
```

---

### **B. Khi nào dùng Single-stage?**

**Use cases:**
- ✅ Development/testing (faster iteration)
- ✅ Pre-built artifacts (CI caching)
- ✅ Debugging (inspect build environment)

**Example: CI pipeline with artifact caching**
```
Jenkins Job 1: Build JAR
  mvn clean package
  Upload artifact to Nexus/Artifactory

Jenkins Job 2: Deploy
  Download JAR from Nexus
  docker build (single-stage, COPY JAR)
  docker run
```

---

### **C. Image Size Comparison**

| Build Type | Image Size | Layers | Build Time |
|------------|-----------|--------|------------|
| **Multi-stage (Maven + JRE)** | ~250 MB | 8-10 | 3-5 min (first), 1-2 min (cached) |
| **Single-stage (JRE only)** | ~150 MB | 4-6 | 30-60 sec |
| **Multi-stage (Alpine)** | ~180 MB | 6-8 | 2-4 min |

**Conclusion:** Multi-stage → Secure & Production-ready, Single-stage → Fast & Dev-friendly

---

## 7️⃣ Q&A NHANH

### **Q1: Tại sao Java phải build trước?**
**A:** Java là **compiled language**. Source code `.java` không thể chạy trực tiếp, phải compile thành bytecode `.class` rồi package thành JAR. Python/Node.js là **interpreted languages**, chạy trực tiếp source code.

---

### **Q2: Maven vs Gradle khác gì?**
**A:** Cả hai đều là **build tools** cho Java:
- **Maven**: XML config (`pom.xml`), convention over configuration, widely adopted
- **Gradle**: Groovy/Kotlin DSL (`build.gradle`), faster, flexible, modern

Build output giống nhau: JAR file.

---

### **Q3: Tại sao dùng Multi-stage build?**
**A:** **3 lý do chính:**
1. **Image size nhỏ hơn**: JRE ~150MB vs JDK+Maven ~800MB
2. **Security**: Không expose build tools, source code trong production
3. **Self-contained**: Không cần Maven/Gradle trên host machine

---

### **Q4: Single-stage có dùng được không?**
**A:** **Có**, nhưng cần:
1. Pre-build JAR trên host (`mvn package`)
2. Jenkins host phải có Maven/Gradle installed
3. Dockerfile chỉ COPY JAR đã built

Trade-off: Faster build, nhưng dependency on host environment.

---

### **Q5: -DskipTests là gì?**
**A:** Maven flag để **skip unit tests** khi build. Lý do:
- Tests đã chạy trong CI pipeline riêng (trước stage này)
- Build faster (tests có thể mất vài phút)
- Production build không cần re-run tests

```bash
mvn clean package              # Run tests
mvn clean package -DskipTests  # Skip tests (compile only)
mvn clean package -Dmaven.test.skip=true  # Skip compile + run tests
```

---

### **Q6: target/ folder là gì?**
**A:** Maven output directory:
```
target/
├── myapp-1.0.0.jar          ← Executable JAR (Spring Boot fat JAR)
├── classes/                 ← Compiled .class files
├── test-classes/            ← Compiled test classes
├── maven-archiver/          ← Build metadata
└── generated-sources/       ← Auto-generated code
```

Dockerfile copy: `COPY target/*.jar app.jar`

---

### **Q7: Gradle output ở đâu?**
**A:** `build/libs/` folder:
```
build/
├── libs/
│   └── myapp-1.0.0.jar      ← Executable JAR
├── classes/                 ← Compiled classes
└── reports/                 ← Test reports
```

Dockerfile copy: `COPY build/libs/*.jar app.jar`

---

### **Q8: Jenkins có cache Maven dependencies không?**
**A:** **Có**, hai cách:
1. **Host-level cache**: Maven local repo `~/.m2/repository`
   - Jenkins reuse across builds
2. **Docker layer cache**: Multi-stage Dockerfile
   ```dockerfile
   COPY pom.xml .
   RUN mvn dependency:go-offline  ← Cached if pom.xml unchanged
   COPY src/ src/
   RUN mvn package
   ```

---

### **Q9: Python có cần build step không?**
**A:** **Không** cho source code, **có** cho dependencies:
- Source `.py` files → COPY trực tiếp (interpreted)
- Dependencies → `pip install -r requirements.txt` (download packages)
- Compiled extensions (optional) → `pip install` tự compile `.c` files

---

### **Q10: Node.js "build" là compile không?**
**A:** **Không**. Node.js "build" thường là:
- **Frontend frameworks** (React, Vue): Transpile + Bundle (Webpack/Vite)
  - JSX → JS, TypeScript → JS, SCSS → CSS
  - Output: static files (HTML, CSS, JS)
- **Backend (Express)**: Chỉ install dependencies (`npm install`)
  - Không compile, chạy `.js` trực tiếp

---

### **Q11: JRE vs JDK khác gì?**
**A:**
- **JRE** (Java Runtime Environment): Chỉ chạy JAR (java command)
  - Size: ~150 MB
  - Use: Production containers
- **JDK** (Java Development Kit): JRE + Compiler (javac) + Tools
  - Size: ~400 MB
  - Use: Build stage trong multi-stage

Multi-stage: Build với JDK, Run với JRE → Optimize size ✅

---

### **Q12: Làm sao detect Maven vs Gradle?**
**A:** Check file existence:
```groovy
def isMaven = fileExists("${buildContext}/pom.xml")
def isGradle = fileExists("${buildContext}/build.gradle")

if (isMaven) {
    sh "mvn clean package"
} else if (isGradle) {
    sh "./gradlew clean build"
}
```

Backend cũng detect tương tự khi generate Dockerfile.

---

## 🎯 TÓM TẮT (30 GIÂY)

```
JAVA (Compiled):
  Source .java → mvn package → JAR → Docker → Container
                    ↑
              BUILD STEP (2-5 phút)

PYTHON (Interpreted):
  Source .py → Docker → Container (pip install trong Docker)
                   ↑
            NO BUILD, install deps only

KEY POINTS:
1. Java phải compile thành JAR trước (Maven/Gradle)
2. Multi-stage build = Secure + Small image (JRE only)
3. Single-stage = Fast + Pre-build JAR on host
4. Python/Node.js không cần build, COPY source trực tiếp
5. Jenkins có stage riêng "Build Application" cho Java only
```

**Technologies:**
- **Maven/Gradle** - Java build tools
- **JRE vs JDK** - Runtime vs Development kit
- **Multi-stage Dockerfile** - Builder + Runtime pattern
- **JAR (Java Archive)** - Executable package format

---

**✨ Chúc bạn bảo vệ thành công!** 🚀
