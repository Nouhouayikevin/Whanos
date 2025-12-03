# Whanos Architecture Overview

This document provides a comprehensive technical overview of the Whanos infrastructure, explaining how all components work together to provide automated application deployment.

## 🏗️ System Architecture

### High-Level Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         DEVELOPER                                │
│                             │                                    │
│                             ▼                                    │
│                     Git Push/Webhook                             │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                       JENKINS MASTER                             │
│  ┌────────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │  Job DSL       │  │  Build Jobs  │  │  Docker Engine   │   │
│  │  Configuration │→ │  (Pipeline)  │→ │  (Build Images)  │   │
│  └────────────────┘  └──────────────┘  └──────────────────┘   │
│                              │                │                  │
└──────────────────────────────┼────────────────┼─────────────────┘
                               │                │
                    ┌──────────┘                └──────────┐
                    ▼                                      ▼
        ┌─────────────────────┐              ┌──────────────────────┐
        │  LOCAL REGISTRY     │              │   DOCKER HUB         │
        │  (Master VM:5000)   │              │   (Public Registry)  │
        └─────────┬───────────┘              └──────────────────────┘
                  │
                  │ Image Pull
                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                    KUBERNETES CLUSTER (K3s)                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │   Master     │  │   Worker 1   │  │   Worker 2   │         │
│  │   Node       │  │   Node       │  │   Node       │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
│         │                   │                  │                 │
│         └───────────────────┴──────────────────┘                │
│                          │                                       │
│                  ┌───────▼───────┐                              │
│                  │   Helm Chart  │                              │
│                  │  (Deployment) │                              │
│                  └───────────────┘                              │
│                          │                                       │
│         ┌────────────────┼────────────────┐                    │
│         ▼                ▼                ▼                     │
│   ┌─────────┐      ┌─────────┐      ┌─────────┐              │
│   │  Pod 1  │      │  Pod 2  │      │  Pod N  │              │
│   └─────────┘      └─────────┘      └─────────┘              │
└─────────────────────────────────────────────────────────────────┘
```

### Component Distribution

**VM1 - Master Node:**
- Jenkins Server (Port 8080, 50000)
- Docker Registry (Port 5000)
- K3s Master (API Server, Controller Manager, Scheduler)
- etcd (K3s embedded)

**VM2 & VM3 - Worker Nodes:**
- K3s Worker (Kubelet, Kube-proxy)
- Container Runtime (containerd)
- Application Pods

## 🔧 Core Components

### 1. Ansible Automation

**Purpose:** Infrastructure provisioning and configuration management

**Key Responsibilities:**
- System package installation and updates
- Docker installation and configuration on all nodes
- K3s cluster initialization and worker joining
- Docker Registry deployment
- Jenkins container deployment with custom configuration
- Network and firewall configuration

**Technical Details:**
```yaml
Playbook Structure (7 Phases):
1. System Prerequisites (apt packages, Python, curl, etc.)
2. Docker Installation (official Docker repository)
3. K3s Master Setup (cluster initialization, kubeconfig)
4. K3s Workers Join (secure token-based joining)
5. Docker Registry Deployment (port 5000, persistent volume)
6. K3s Registry Configuration (containerd insecure registry)
7. Jenkins Deployment (custom image with tools)
```

**Critical Files:**
- `ansible/deploy_whanos.yml` (448 lines) - Main playbook
- `ansible/inventory.ini` - VM inventory with connection details
- `Dockerfile.jenkins` - Custom Jenkins image definition

**Deployment Flow:**
```
deploy.sh → Load .env → ansible-playbook deploy_whanos.yml
    ↓
Phase 1: Install system dependencies on all VMs
    ↓
Phase 2: Install Docker on all VMs (version 27.4.1)
    ↓
Phase 3: Initialize K3s on master (generate token)
    ↓
Phase 4: Join workers to cluster (using token)
    ↓
Phase 5: Deploy Docker Registry on master
    ↓
Phase 6: Configure K3s to use insecure registry
    ↓
Phase 7: Build and deploy Jenkins container
```

### 2. Jenkins CI/CD

**Purpose:** Continuous Integration and Continuous Deployment orchestration

**Key Features:**
- **Job DSL:** Dynamic job creation from code
- **Configuration as Code (JCasC):** Automated Jenkins configuration
- **Credentials Management:** Secure storage of GitHub and Docker Hub tokens
- **Build Automation:** Automatic language detection and containerization

**Architecture:**

```
Jenkins Container
├── Jenkins Core (Port 8080)
├── Docker CLI (build images)
├── kubectl (K8s deployments)
├── helm (chart deployments)
├── Git (repository cloning)
└── Configuration
    ├── config.yml (JCasC)
    ├── job_dsl.groovy (Job definitions)
    └── plugins.txt (Required plugins)
```

**Job Structure:**

```
Whanos base images/
├── whanos-befunge
├── whanos-c
├── whanos-java
├── whanos-javascript
├── whanos-python
└── Build all base images (triggers all above)

Projects/
├── link-project (job creator)
└── <dynamically created project jobs>
    └── Project-<name>-<timestamp>
```

**Build Pipeline:**

```
1. Git Clone
   ↓
2. Language Detection (check app/ directory structure)
   ↓
3. Base Image Selection (whanos-<language>)
   ↓
4. Custom Dockerfile Check (if exists in repo)
   ↓
5. Image Build
   ├→ Use base image as FROM
   ├→ Apply custom Dockerfile (optional)
   └→ Tag with build number
   ↓
6. Registry Push
   ├→ Push to local registry (REGISTRY_URL)
   └→ Push to Docker Hub (if configured)
   ↓
7. Deployment (if whanos.yml exists)
   ├→ Generate Helm values
   ├→ helm install/upgrade
   └→ Wait for rollout
```

**Technical Implementation:**

**config.yml (JCasC):**
```yaml
Key Sections:
- Security realm: Local user database
- Authorization: Logged-in users can do anything
- Credentials: GitHub and Docker Hub tokens from env vars
- Seed Jobs: Load job_dsl.groovy on startup
```

**job_dsl.groovy (316 lines):**
```groovy
Structure:
1. Folder creation (Whanos base images, Projects)
2. Base image jobs (lines 19-75)
   - Shell script for docker build + push
   - Dual registry push (local + Docker Hub)
   - Environment variable injection
3. Build all images job (lines 77-98)
   - Triggers all base image builds
4. link-project job (lines 100-316)
   - Parameters: DISPLAY_NAME, GIT_URL, BRANCH
   - Language detection logic
   - Dynamic job creation in Projects/
   - whanos.yml parsing
   - Helm deployment logic
```

### 3. Docker Registry

**Purpose:** Local image storage for Kubernetes cluster

**Configuration:**
```yaml
Container: registry:2
Port: 5000 (HTTP)
Volume: /var/lib/registry (persistent storage)
Access: Insecure (HTTP only - configured in K3s)
```

**Integration with K3s:**
```bash
# K3s containerd configuration
[plugins."io.containerd.grpc.v1.cri".registry.mirrors."MASTER_IP:5000"]
  endpoint = ["http://MASTER_IP:5000"]

[plugins."io.containerd.grpc.v1.cri".registry.configs."MASTER_IP:5000".tls]
  insecure_skip_verify = true
```

**Why Local Registry?**
- Fast image pulls (local network)
- No Docker Hub rate limits
- Works offline
- Backup to Docker Hub for redundancy

### 4. Kubernetes Cluster (K3s)

**Why K3s?**
- Lightweight Kubernetes (single binary)
- Perfect for VMs and edge computing
- Full Kubernetes API compatibility
- Embedded etcd (no external dependencies)
- Easy multi-node setup

**Cluster Configuration:**

```yaml
Master Node:
  Role: control-plane, master
  Components:
    - API Server (port 6443)
    - Controller Manager
    - Scheduler
    - etcd (embedded)
    - kubelet
    - kube-proxy
  K3s Version: v1.33.5+k3s1

Worker Nodes (x2):
  Role: worker
  Components:
    - kubelet
    - kube-proxy
    - containerd
```

**Network Configuration:**
```
Pod Network: 10.42.0.0/16 (default K3s)
Service Network: 10.43.0.0/16 (default K3s)
Node Communication: Direct VM-to-VM
CNI: Flannel (K3s default)
```

**Storage:**
```
Default StorageClass: local-path
Provisioner: rancher.io/local-path
Location: /var/lib/rancher/k3s/storage
```

### 5. Helm Charts

**Purpose:** Templated Kubernetes deployments

**Chart Structure:**

```
helm/Whanos/
├── Chart.yaml              # Chart metadata
├── values.yaml             # Default values
└── templates/
    ├── deployment.yaml     # Pod deployment
    ├── service.yaml        # Network service
    └── _helpers.tpl        # Template helpers
```

**Deployment Template Logic:**

```yaml
# deployment.yaml (simplified)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .Values.nameOverride | default .Release.Name }}
spec:
  replicas: {{ .Values.deployment.replicas | default 1 }}
  template:
    spec:
      containers:
      - name: app
        image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
        ports:
        {{- range .Values.deployment.ports }}
        - containerPort: {{ . }}
        {{- end }}
        resources:
          {{- toYaml .Values.deployment.resources | nindent 10 }}
```

**Values Override Flow:**

```
1. Default values (values.yaml)
   ↓
2. whanos.yml from repository (if exists)
   ↓
3. Jenkins job parameters
   ↓
4. Final merged values
   ↓
5. Helm template rendering
   ↓
6. kubectl apply
```

## 🔄 Complete Deployment Flow

### Base Image Creation

```
1. Administrator triggers "Build all base images"
   ↓
2. Jenkins loops through languages array
   ↓
3. For each language:
   - cd /var/jenkins_home/whanos_images/<language>
   - docker build -t REGISTRY_URL/whanos-<language>:latest -f Dockerfile.base
   - docker push REGISTRY_URL/whanos-<language>:latest
   - docker login to Docker Hub
   - docker tag REGISTRY_URL/whanos-<language> DOCKERHUB/whanos-<language>
   - docker push DOCKERHUB/whanos-<language>:latest
   ↓
4. Images available in both registries
```

### Application Deployment

```
1. Developer pushes to Git repository
   ↓
2. Webhook triggers Jenkins job (or manual build)
   ↓
3. Jenkins clones repository
   ↓
4. Language detection:
   - Check for package.json → JavaScript
   - Check for requirements.txt → Python
   - Check for pom.xml → Java
   - Check for Makefile → C
   - Check for .bf files → Befunge
   ↓
5. Image build:
   - Use whanos-<detected-language> as base
   - COPY app/ to container
   - Apply custom Dockerfile if exists
   - Build with tag: <project-name>:<build-number>
   ↓
6. Push to registries:
   - docker push REGISTRY_URL/<project>:<build>
   - docker push DOCKERHUB/<project>:<build>
   ↓
7. Check for whanos.yml in repository
   ↓
8. If whanos.yml exists:
   - Parse deployment configuration
   - Generate Helm values file
   - helm upgrade --install <project> ./helm/Whanos \
       --set image.repository=REGISTRY_URL/<project> \
       --set image.tag=<build-number> \
       --values whanos.yml
   ↓
9. Kubernetes creates:
   - Deployment (manages Pods)
   - ReplicaSet (ensures replica count)
   - Pods (actual containers)
   - Service (network exposure)
   ↓
10. Application accessible via NodePort
```

## 🔐 Security Architecture

### Authentication & Authorization

**Jenkins:**
- Local user database
- Admin user created on first boot
- Password from environment variable
- CSRF protection enabled

**Kubernetes:**
- K3s token-based authentication
- RBAC enabled by default
- Service accounts for pods

**Docker Registry:**
- No authentication (internal network only)
- Trust-based security model
- Consider adding basic auth for production

### Network Security

```
External Access:
- Jenkins: Port 8080 (HTTP)
- Kubernetes API: Port 6443 (HTTPS)
- Application Services: NodePort range (30000-32767)

Internal Communication:
- K3s cluster: Full mesh network
- Registry: Port 5000 (HTTP, internal only)
- Pods: CNI network (10.42.0.0/16)
```

### Credential Management

```
Credentials Flow:
.env file → Ansible → Jenkins Environment → Job DSL → Kubernetes Secrets

Storage:
- GitHub Token: Jenkins credentials store
- Docker Hub Token: Jenkins credentials store
- K3s Token: File on master (/var/lib/rancher/k3s/server/node-token)
```

## 📊 Resource Management

### Default Resource Allocation

**Jenkins Container:**
```yaml
CPU: No limit (uses host)
Memory: No limit (recommended 2GB+)
Storage: /var/jenkins_home volume
```

**Base Images:**
```yaml
befunge: ~100MB
c: ~150MB
java: ~400MB
javascript: ~200MB
python: ~200MB
```

**Application Pods (default):**
```yaml
CPU: No limits (unless specified in whanos.yml)
Memory: No limits (unless specified in whanos.yml)
Replicas: 1 (unless specified in whanos.yml)
```

**Recommended Production Limits:**
```yaml
deployment:
  resources:
    limits:
      cpu: 1000m
      memory: 1Gi
    requests:
      cpu: 100m
      memory: 128Mi
```

## 🔍 Monitoring & Observability

### Logs

**Jenkins Logs:**
```bash
docker logs whanos-jenkins
docker logs -f whanos-jenkins  # Follow mode
```

**K3s Logs:**
```bash
journalctl -u k3s -f          # Master
journalctl -u k3s-agent -f    # Workers
```

**Application Logs:**
```bash
kubectl logs <pod-name>
kubectl logs -f deployment/<app-name>
```

### Health Checks

**Cluster Health:**
```bash
kubectl get nodes
kubectl get componentstatuses
kubectl get pods --all-namespaces
```

**Registry Health:**
```bash
curl http://MASTER_IP:5000/v2/_catalog
```

**Jenkins Health:**
```bash
curl http://MASTER_IP:8080/login
```

## 🚀 Scaling Considerations

### Horizontal Scaling

**Application Level:**
```yaml
# In whanos.yml
deployment:
  replicas: 5  # Scale to 5 pods
```

**Cluster Level:**
- Add more worker nodes
- Update inventory.ini
- Run Ansible playbook phase 4

### Vertical Scaling

**Increase VM Resources:**
- More CPU/RAM per VM
- Better disk I/O
- Update resource limits in whanos.yml

### Performance Optimization

**Registry:**
- Use SSD for /var/lib/registry
- Consider registry caching proxy
- Implement garbage collection

**K3s:**
- Tune kubelet parameters
- Optimize CNI configuration
- Use faster storage class

## 🔧 Extensibility

### Adding New Languages

1. Create base image in `images/<new-language>/`:
   - `Dockerfile.base` - Base image definition
   - `Dockerfile.standalone` - Standalone testing image

2. Add language to job_dsl.groovy:
   ```groovy
   def languages = ['befunge', 'c', 'java', 'javascript', 'python', 'new-language']
   ```

3. Update language detection in link-project job

4. Build new base image

### Custom Plugins

Add to `jenkins/plugins.txt`:
```
plugin-name:version
```

Redeploy Jenkins container.

### Custom Helm Templates

Modify files in `helm/Whanos/templates/`:
- Add ConfigMaps
- Add Secrets
- Add Ingress
- Add PersistentVolumeClaims

## 📚 Technical References

**Technologies Used:**
- Ansible 2.18+ ([docs](https://docs.ansible.com))
- Jenkins 2.x ([docs](https://www.jenkins.io/doc/))
- Job DSL Plugin ([docs](https://plugins.jenkins.io/job-dsl/))
- K3s v1.33+ ([docs](https://docs.k3s.io))
- Docker 27.4+ ([docs](https://docs.docker.com))
- Helm 3.x ([docs](https://helm.sh/docs/))
- Kubernetes 1.33+ ([docs](https://kubernetes.io/docs/))

**Related Documentation:**
- [Deployment Guide](DEPLOYMENT.md)
- [Configuration Reference](CONFIGURATION.md)
- [Helm Charts](HELM.md)
- [Troubleshooting](TROUBLESHOOTING.md)

---

This architecture provides a production-ready, scalable, and maintainable infrastructure for automated application deployment. Each component is designed to work seamlessly with others while remaining independently replaceable and upgradeable.
