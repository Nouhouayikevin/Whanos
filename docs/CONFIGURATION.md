# Whanos Configuration Reference

Complete reference for all configuration options in the Whanos infrastructure.

## 📋 Table of Contents

- [Environment Variables](#environment-variables)
- [Ansible Configuration](#ansible-configuration)
- [Jenkins Configuration](#jenkins-configuration)
- [Kubernetes Configuration](#kubernetes-configuration)
- [Helm Chart Values](#helm-chart-values)
- [Application Configuration (whanos.yml)](#application-configuration-whanosyml)

## 🔧 Environment Variables

All environment variables are defined in the `.env` file at the repository root.

### .env File Structure

```bash
# ===== JENKINS CONFIGURATION =====
ADMIN_PASSWORD=admin123

# ===== GITHUB CREDENTIALS =====
GITHUB_USERNAME=your_username
GITHUB_TOKEN=ghp_xxxxxxxxxxxxx

# ===== DOCKER REGISTRY =====
REGISTRY_URL=192.168.1.100:5000

# ===== DOCKER HUB CREDENTIALS =====
DOCKER_HUB_USERNAME=your_dockerhub_username
DOCKER_HUB_TOKEN=dckr_pat_xxxxxxxxxxxxx
```

### Variable Details

#### ADMIN_PASSWORD

**Description:** Password for Jenkins admin user

**Format:** String (any characters)

**Default:** `admin123`

**Recommended:** 
- Minimum 16 characters
- Mix of uppercase, lowercase, numbers, symbols
- Example: `Wh@nos!2024$SecurePwd`

**Usage:** 
- Jenkins login credentials
- Username is always `admin`

**Security Notes:**
- Never commit `.env` file to Git
- Rotate password regularly
- Use password manager for storage

---

#### GITHUB_USERNAME

**Description:** GitHub username for accessing repositories

**Format:** String (GitHub username)

**Required for:** Accessing private GitHub repositories

**Example:** `kevvvvvvv`

**How to find:**
```bash
# Your GitHub username is in your profile URL
# https://github.com/<YOUR_USERNAME>
```

---

#### GITHUB_TOKEN

**Description:** GitHub Personal Access Token for authentication

**Format:** String starting with `ghp_`

**Required for:** Private repository access

**How to generate:**
1. Go to GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Click "Generate new token (classic)"
3. Set expiration (recommend: 90 days)
4. Select scopes:
   - ✅ `repo` (Full control of private repositories)
   - ✅ `read:packages` (Download packages)
5. Generate and copy token immediately

**Example:** `ghp_1234567890abcdefghijklmnopqrstuvwxyz`

**Security:**
- Token grants access to your repositories
- Store securely, treat like a password
- Rotate regularly (before expiration)
- Revoke if compromised

---

#### REGISTRY_URL

**Description:** Docker Registry URL for Kubernetes cluster

**Format:** `IP:PORT` or `HOSTNAME:PORT`

**Default Port:** `5000`

**Examples:**
- `192.168.1.100:5000` (private network)
- `registry.example.com:5000` (custom domain)
- `10.0.0.5:5000` (local network)

**Important:**
- Use the **public IP** or **IP accessible by worker nodes**
- Do NOT use `localhost` or `127.0.0.1` (won't work from workers)
- Typically same as your master VM IP

**Testing:**
```bash
# From any node, this should work:
curl http://REGISTRY_URL/v2/_catalog
```

---

#### DOCKER_HUB_USERNAME

**Description:** Docker Hub username for public image hosting

**Format:** String (Docker Hub username)

**Optional:** Yes (but highly recommended)

**Example:** `kevvvvvvv`

**Benefits of configuring:**
- Images backed up to public registry
- Accessible from anywhere
- No rate limits on your own images
- Easy sharing with others

**How to find:**
```
# Your Docker Hub username is at:
https://hub.docker.com/u/<YOUR_USERNAME>
```

---

#### DOCKER_HUB_TOKEN

**Description:** Docker Hub Access Token for authentication

**Format:** String starting with `dckr_pat_`

**Optional:** Yes (but recommended)

**How to generate:**
1. Login to Docker Hub
2. Account Settings → Security → New Access Token
3. Description: "Whanos Jenkins"
4. Access permissions: Read, Write, Delete
5. Generate and copy token

**Example:** `dckr_pat_1234567890abcdefghijklmnopqrstu`

**Security:**
- Grants write access to your Docker Hub
- Rotate every 6 months
- Use separate token per deployment

---

### Environment Variable Flow

```
.env file
    ↓
deploy.sh (loads with 'source')
    ↓
Ansible playbook (environment variables)
    ↓
Jenkins container (docker run -e)
    ↓
Jenkins Configuration as Code (config.yml)
    ↓
Job DSL scripts (accessible as env vars)
    ↓
Shell build steps (accessible as $VAR)
```

## 📦 Ansible Configuration

### Inventory File (ansible/inventory.ini)

```ini
[jenkins_master]
master ansible_host=134.209.197.7 ansible_user=root ansible_ssh_pass=password

[k3s_master]
master ansible_host=134.209.197.7 ansible_user=root ansible_ssh_pass=password

[k3s_workers]
worker1 ansible_host=178.62.222.96 ansible_user=root ansible_ssh_pass=password
worker2 ansible_host=164.90.202.159 ansible_user=root ansible_ssh_pass=password

[all:vars]
ansible_python_interpreter=/usr/bin/python3
```

### Inventory Variables

#### ansible_host

**Description:** IP address or hostname of target VM

**Format:** IPv4 address or FQDN

**Example:** `134.209.197.7`

---

#### ansible_user

**Description:** SSH user for connection

**Default:** `root`

**Alternatives:** Any user with sudo privileges

**Note:** If not root, add `become=yes` to playbook tasks

---

#### ansible_ssh_pass

**Description:** SSH password for authentication

**Security Warning:** Passwords in plain text are insecure

**Production Alternative:** Use SSH keys

**How to use SSH keys:**
```bash
# Generate key
ssh-keygen -t ed25519

# Copy to VMs
ssh-copy-id root@VM_IP

# Remove ansible_ssh_pass from inventory
```

---

#### ansible_python_interpreter

**Description:** Python interpreter path on target

**Default:** `/usr/bin/python3`

**Why needed:** Ansible requires Python on target systems

**Alternatives:**
- `/usr/bin/python` (Python 2, deprecated)
- `/usr/bin/python3.8` (specific version)

---

### Playbook Variables

Defined in `ansible/deploy_whanos.yml`:

```yaml
vars:
  k3s_version: v1.33.5+k3s1
  docker_version: "5:27.4.1-1~ubuntu.22.04~jammy"
  registry_port: 5000
  jenkins_port: 8080
  jenkins_jnlp_port: 50000
```

These can be overridden at runtime:

```bash
ansible-playbook deploy_whanos.yml -e "k3s_version=v1.30.0+k3s1"
```

## 🔨 Jenkins Configuration

### Configuration as Code (config.yml)

Located at: `jenkins/config.yml`

#### Security Configuration

```yaml
jenkins:
  securityRealm:
    local:
      allowsSignup: false
      users:
        - id: "admin"
          password: "${ADMIN_PASSWORD}"
  authorizationStrategy:
    loggedInUsersCanDoAnything:
      allowAnonymousRead: false
```

**Options:**

**allowsSignup:** 
- `false` - Only admin can create users (recommended)
- `true` - Anyone can register (not recommended)

**allowAnonymousRead:**
- `false` - Must login to view Jenkins (recommended)
- `true` - Public read access (not recommended)

---

#### Credentials Configuration

```yaml
credentials:
  system:
    domainCredentials:
      - credentials:
          - usernamePassword:
              scope: GLOBAL
              id: "github-credentials"
              username: "${GITHUB_USERNAME}"
              password: "${GITHUB_TOKEN}"
              description: "GitHub credentials for private repositories"
          - usernamePassword:
              scope: GLOBAL
              id: "dockerhub-credentials"
              username: "${DOCKER_HUB_USERNAME}"
              password: "${DOCKER_HUB_TOKEN}"
              description: "Docker Hub credentials"
```

**Credential Scopes:**
- `GLOBAL` - Available to all jobs
- `SYSTEM` - Available to Jenkins only

---

#### Job DSL Seed Job

```yaml
jobs:
  - script: |
      job('seed-job') {
        description('Generates all Whanos jobs from job_dsl.groovy')
        steps {
          dsl {
            external('job_dsl.groovy')
            removeAction('DELETE')
            removeViewAction('DELETE')
          }
        }
      }
```

**removeAction/removeViewAction Options:**
- `DELETE` - Delete jobs not in DSL (dangerous)
- `DISABLE` - Disable old jobs
- `IGNORE` - Keep old jobs (recommended)

---

### Plugin Configuration (plugins.txt)

Located at: `jenkins/plugins.txt`

**Format:** `plugin-id:version`

**Essential Plugins:**
```
job-dsl:latest              # Job DSL for dynamic job creation
configuration-as-code:latest # Configuration as Code
git:latest                  # Git integration
docker-plugin:latest        # Docker build support
kubernetes:latest           # Kubernetes deployment
```

**Adding new plugins:**
1. Find plugin ID at https://plugins.jenkins.io/
2. Add to `plugins.txt`: `plugin-id:version`
3. Rebuild Jenkins image
4. Restart Jenkins container

---

### Job DSL Configuration (job_dsl.groovy)

Located at: `jenkins/job_dsl.groovy`

#### Language Support

```groovy
def languages = ['befunge', 'c', 'java', 'javascript', 'python']
```

**To add new language:**
1. Add to array: `'new-language'`
2. Create base image in `images/new-language/`
3. Redeploy Jenkins

---

#### Build Configuration

```groovy
freeStyleJob("Whanos base images/whanos-${lang}") {
    logRotator {
        numToKeep(10)  # Keep last 10 builds
    }
}
```

**logRotator options:**
- `numToKeep(N)` - Keep N most recent builds
- `daysToKeep(N)` - Keep builds from last N days
- `artifactNumToKeep(N)` - Keep artifacts from N builds

---

#### Registry Configuration in Jobs

```groovy
shell("""
    REGISTRY_URL=\${REGISTRY_URL:-localhost:5000}
    DOCKER_HUB_USERNAME=\${DOCKER_HUB_USERNAME:-}
    
    docker build -t \$REGISTRY_URL/whanos-${lang}:latest .
    docker push \$REGISTRY_URL/whanos-${lang}:latest
    
    if [ -n "\$DOCKER_HUB_USERNAME" ]; then
        docker tag \$REGISTRY_URL/whanos-${lang}:latest \$DOCKER_HUB_USERNAME/whanos-${lang}:latest
        docker push \$DOCKER_HUB_USERNAME/whanos-${lang}:latest
    fi
""")
```

## ☸️ Kubernetes Configuration

### K3s Installation Options

Configured in `ansible/deploy_whanos.yml`:

```yaml
- name: Installer K3s sur master
  shell: |
    curl -sfL https://get.k3s.io | \
    INSTALL_K3S_VERSION="{{ k3s_version }}" \
    sh -s - server \
      --write-kubeconfig-mode 644 \
      --disable traefik
```

**K3s Options:**

**--disable traefik**
- Disables Traefik ingress controller
- Can enable if you need ingress: remove this flag

**--write-kubeconfig-mode 644**
- Makes kubeconfig readable by all users
- Change to `600` for production (root only)

**Additional options:**
```bash
--tls-san "custom-domain.com"  # Additional TLS SAN
--node-name "custom-name"      # Custom node name
--cluster-cidr "10.42.0.0/16"  # Pod network CIDR
--service-cidr "10.43.0.0/16"  # Service network CIDR
```

---

### Registry Configuration

K3s registry config: `/etc/rancher/k3s/registries.yaml`

```yaml
mirrors:
  "MASTER_IP:5000":
    endpoint:
      - "http://MASTER_IP:5000"
configs:
  "MASTER_IP:5000":
    tls:
      insecure_skip_verify: true
```

**For production (with TLS):**
```yaml
mirrors:
  "registry.example.com":
    endpoint:
      - "https://registry.example.com"
configs:
  "registry.example.com":
    auth:
      username: "user"
      password: "pass"
    tls:
      cert_file: "/path/to/cert.pem"
      key_file: "/path/to/key.pem"
      ca_file: "/path/to/ca.pem"
```

## 📊 Helm Chart Values

### Default Values (helm/Whanos/values.yaml)

```yaml
image:
  repository: ""  # Set by Jenkins
  tag: ""         # Set by Jenkins
  pullPolicy: IfNotPresent

deployment:
  replicas: 1
  resources: {}
  ports: []

service:
  type: NodePort
```

### Values Reference

#### image.repository

**Description:** Docker image repository URL

**Set by:** Jenkins during deployment

**Format:** `REGISTRY_URL/project-name`

**Example:** `134.209.197.7:5000/my-app`

---

#### image.tag

**Description:** Docker image tag

**Set by:** Jenkins (build number)

**Format:** String

**Example:** `42` (Jenkins build #42)

---

#### image.pullPolicy

**Description:** When to pull image

**Options:**
- `IfNotPresent` - Pull if not cached (default, recommended)
- `Always` - Always pull (slower but ensures latest)
- `Never` - Never pull (only use cached)

---

#### deployment.replicas

**Description:** Number of pod replicas

**Default:** `1`

**Range:** 1 to N (limited by node resources)

**Example:** `3` for high availability

**Scaling:**
```bash
# Scale via kubectl
kubectl scale deployment my-app --replicas=5
```

---

#### deployment.resources

**Description:** CPU and memory limits/requests

**Default:** Empty (no limits)

**Format:**
```yaml
resources:
  limits:
    cpu: "1000m"      # 1 CPU core max
    memory: "1Gi"     # 1 GB RAM max
  requests:
    cpu: "100m"       # 0.1 CPU core requested
    memory: "128Mi"   # 128 MB RAM requested
```

**CPU Units:**
- `1000m` = 1 CPU core
- `500m` = 0.5 CPU core
- `100m` = 0.1 CPU core

**Memory Units:**
- `Mi` = Mebibytes (1024² bytes)
- `Gi` = Gibibytes (1024³ bytes)
- `M` = Megabytes (1000² bytes)
- `G` = Gigabytes (1000³ bytes)

---

#### deployment.ports

**Description:** Container ports to expose

**Format:** Array of integers

**Example:**
```yaml
ports:
  - 8080
  - 3000
  - 5432
```

**Used for:**
- Container port exposure
- Service creation
- Health checks

---

#### service.type

**Description:** Kubernetes service type

**Options:**

**NodePort** (default)
- Exposes on each node's IP at a random port (30000-32767)
- Access: `http://NODE_IP:NODEPORT`
- Best for: Dev/test environments

**LoadBalancer**
- Provisions cloud load balancer (if available)
- Access: `http://EXTERNAL_IP:PORT`
- Best for: Production with cloud provider

**ClusterIP**
- Internal cluster access only
- Access: Only from within cluster
- Best for: Internal services, databases

**Example:**
```yaml
service:
  type: LoadBalancer  # Requires cloud provider or MetalLB
```

## 📄 Application Configuration (whanos.yml)

Place `whanos.yml` at the root of your application repository.

### Complete Example

```yaml
deployment:
  replicas: 3
  resources:
    limits:
      cpu: 1000m
      memory: 1Gi
    requests:
      cpu: 250m
      memory: 256Mi
  ports:
    - 8080
    - 9090

service:
  type: NodePort
```

### Configuration Options

#### deployment.replicas

**Description:** Number of application instances

**Type:** Integer

**Default:** 1

**Recommendations:**
- Dev: 1
- Staging: 2
- Production: 3+

**Example:**
```yaml
deployment:
  replicas: 5  # 5 identical pods
```

---

#### deployment.resources.limits

**Description:** Maximum resources per pod

**Purpose:** Prevent resource exhaustion

**Example:**
```yaml
limits:
  cpu: 500m     # Max 0.5 CPU
  memory: 512Mi # Max 512 MB
```

**What happens when limit reached:**
- CPU: Throttling (pod runs slower)
- Memory: Pod killed and restarted (OOMKilled)

---

#### deployment.resources.requests

**Description:** Guaranteed resources per pod

**Purpose:** Kubernetes scheduling decisions

**Example:**
```yaml
requests:
  cpu: 100m     # Reserve 0.1 CPU
  memory: 128Mi # Reserve 128 MB
```

**Impact:**
- Pod only scheduled on nodes with available resources
- Guaranteed minimum performance

---

#### deployment.ports

**Description:** Container ports to expose

**Type:** Array of integers

**Example:**
```yaml
ports:
  - 8080  # Main application port
  - 9090  # Metrics port
```

**Creates:**
- Service with these ports exposed
- Container port mappings

---

#### service.type

**Description:** How service is exposed

**Type:** String

**Values:** `NodePort`, `LoadBalancer`, `ClusterIP`

**Example:**
```yaml
service:
  type: LoadBalancer  # External access via LB
```

---

### whanos.yml Validation

**Required fields:** None (all optional)

**Validation rules:**
- `replicas` must be positive integer
- `resources` values must have valid units
- `ports` must be integers 1-65535
- `service.type` must be valid type

**Testing:**
```bash
# Validate YAML syntax
python3 -c "import yaml; yaml.safe_load(open('whanos.yml'))"

# Deploy and check
kubectl get deployment <app-name> -o yaml
```

## 🔍 Configuration Precedence

When multiple configurations exist:

```
1. helm/Whanos/values.yaml (defaults)
   ↓ (overridden by)
2. whanos.yml in repository
   ↓ (overridden by)
3. Jenkins job parameters
   ↓ (overridden by)
4. helm install --set flags
```

**Example:**
```bash
# values.yaml: replicas: 1
# whanos.yml: replicas: 3
# helm install --set deployment.replicas=5

# Result: 5 replicas (last override wins)
```

## 📚 Configuration Examples

### Production Application

```yaml
# whanos.yml for production app
deployment:
  replicas: 5
  resources:
    limits:
      cpu: 2000m
      memory: 2Gi
    requests:
      cpu: 500m
      memory: 512Mi
  ports:
    - 8080

service:
  type: LoadBalancer
```

### Development Application

```yaml
# whanos.yml for dev app
deployment:
  replicas: 1
  resources:
    limits:
      cpu: 500m
      memory: 512Mi
    requests:
      cpu: 100m
      memory: 128Mi
  ports:
    - 3000

service:
  type: NodePort
```

### Microservice

```yaml
# whanos.yml for microservice
deployment:
  replicas: 3
  resources:
    limits:
      cpu: 1000m
      memory: 1Gi
    requests:
      cpu: 250m
      memory: 256Mi
  ports:
    - 8080  # HTTP API
    - 9090  # Metrics
    - 8081  # Health checks

service:
  type: ClusterIP  # Internal only
```

---

**For more information:**
- [User Guide](USER_GUIDE.md) - Getting started
- [Deployment Guide](DEPLOYMENT.md) - Installation steps
- [Architecture Overview](ARCHITECTURE.md) - System design
- [Troubleshooting](TROUBLESHOOTING.md) - Common issues
