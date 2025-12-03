# Whanos Helm Chart Documentation

Technical documentation for the Whanos Helm chart used for Kubernetes deployments.

## 📋 Table of Contents

- [Overview](#overview)
- [Chart Structure](#chart-structure)
- [Configuration Options](#configuration-options)
- [Template Reference](#template-reference)
- [Deployment Process](#deployment-process)
- [Examples](#examples)
- [Troubleshooting](#troubleshooting)

## 🎯 Overview

The Whanos Helm chart is a lightweight, flexible chart designed to deploy applications automatically on Kubernetes. It's used by Jenkins to deploy applications that have a `whanos.yml` configuration file.

**Key Features:**
- ✅ Minimal configuration required
- ✅ Automatic deployment from Jenkins
- ✅ Resource management (CPU, memory)
- ✅ Port exposure via NodePort
- ✅ Replica scaling
- ✅ Compatible with any Docker image

**Chart Location:** `/var/jenkins_home/helm/Whanos/` (inside Jenkins container)

## 📁 Chart Structure

```
helm/Whanos/
├── Chart.yaml              # Chart metadata
├── values.yaml             # Default values
└── templates/
    ├── deployment.yaml     # Kubernetes Deployment template
    └── service.yaml        # Kubernetes Service template
```

### Chart.yaml

```yaml
apiVersion: v2
name: whanos
description: A Helm chart for automatic deployment of Whanos applications
type: application
version: 1.0.0
appVersion: "1.0"
```

**Fields:**
- **apiVersion**: `v2` (Helm 3)
- **name**: Chart name (whanos)
- **type**: `application` (not a library)
- **version**: Chart version (semver)
- **appVersion**: Version of the app being deployed

### values.yaml

**Purpose:** Default values that can be overridden

```yaml
# Image configuration
image:
  repository: ""  # Set by Jenkins
  tag: ""         # Set by Jenkins (build number)
  pullPolicy: IfNotPresent

# Application name
nameOverride: ""

# Deployment configuration
deployment:
  replicas: 1
  resources: {}
  ports: []

# Service configuration
service:
  type: NodePort
```

## ⚙️ Configuration Options

### whanos.yml Format

Place a `whanos.yml` file at the root of your application repository to configure deployment.

**Complete Example:**

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

### Configuration Reference

#### deployment.replicas

**Type:** Integer  
**Default:** `1`  
**Description:** Number of pod replicas to run

**Example:**
```yaml
deployment:
  replicas: 5  # Run 5 instances
```

**Use Cases:**
- Dev/Test: `1`
- Staging: `2-3`
- Production: `3+` (high availability)

---

#### deployment.resources

**Type:** Object  
**Default:** `{}` (no limits)  
**Description:** CPU and memory resource constraints

**Format:**
```yaml
deployment:
  resources:
    limits:      # Maximum resources allowed
      cpu: "1000m"     # 1 CPU core max
      memory: "1Gi"    # 1 GB RAM max
    requests:    # Guaranteed resources
      cpu: "100m"      # 0.1 CPU core reserved
      memory: "128Mi"  # 128 MB RAM reserved
```

**CPU Units:**
- `1000m` = 1 CPU core (1000 millicores)
- `500m` = 0.5 CPU core
- `100m` = 0.1 CPU core

**Memory Units:**
- `Mi` = Mebibytes (1024²)
- `Gi` = Gibibytes (1024³)
- `M` = Megabytes (1000²)
- `G` = Gigabytes (1000³)

**What happens when limits are reached:**
- **CPU:** Throttling (pod runs slower)
- **Memory:** Pod is killed and restarted (OOMKilled)

**Best Practices:**
```yaml
# Production app
resources:
  limits:
    cpu: 2000m
    memory: 2Gi
  requests:
    cpu: 500m
    memory: 512Mi

# Microservice
resources:
  limits:
    cpu: 1000m
    memory: 1Gi
  requests:
    cpu: 250m
    memory: 256Mi

# Dev/Test
resources:
  limits:
    cpu: 500m
    memory: 512Mi
  requests:
    cpu: 100m
    memory: 128Mi
```

---

#### deployment.ports

**Type:** Array of integers  
**Default:** `[]` (empty)  
**Description:** Container ports to expose

**Example:**
```yaml
deployment:
  ports:
    - 8080  # Main application port
    - 9090  # Metrics/monitoring port
    - 8081  # Health check port
```

**Important:**
- Only define ports your application actually uses
- These ports must match what your application listens on
- Used by Service to route traffic

**Common Ports:**
- `8080` - HTTP (Java, Spring Boot)
- `3000` - Node.js/Express
- `5000` - Flask/Python
- `80` - Standard HTTP
- `443` - HTTPS

---

#### service.type

**Type:** String  
**Default:** `NodePort`  
**Options:** `NodePort`, `LoadBalancer`, `ClusterIP`

**NodePort (default):**
```yaml
service:
  type: NodePort
```
- Exposes on each node's IP
- Random port: 30000-32767
- Access: `http://NODE_IP:NODEPORT`
- **Best for:** Dev, test, bare-metal clusters

**LoadBalancer:**
```yaml
service:
  type: LoadBalancer
```
- Provisions cloud load balancer
- Access: `http://EXTERNAL_IP:PORT`
- **Best for:** Production with cloud provider
- **Requires:** Cloud integration (AWS, GCP, Azure) or MetalLB

**ClusterIP:**
```yaml
service:
  type: ClusterIP
```
- Internal cluster access only
- No external exposure
- **Best for:** Internal services, databases, microservices

## 📄 Template Reference

### deployment.yaml

**Purpose:** Creates a Kubernetes Deployment (manages pods)

**Key Sections:**

#### Metadata
```yaml
metadata:
  name: {{ .Values.nameOverride | default .Release.Name }}
  labels:
    app: {{ .Values.nameOverride | default .Release.Name }}
    chart: {{ .Chart.Name }}-{{ .Chart.Version }}
    release: {{ .Release.Name }}
```

**Helm Template Functions:**
- `{{ .Values.nameOverride }}` - From values.yaml
- `{{ .Release.Name }}` - Helm release name (project name)
- `{{ .Chart.Name }}` - Chart name from Chart.yaml
- `| default ...` - Use default if value is empty

#### Replicas
```yaml
spec:
  replicas: {{ .Values.deployment.replicas | default 1 }}
```

**Logic:**
- Uses `deployment.replicas` from whanos.yml
- Falls back to `1` if not specified

#### Selector
```yaml
selector:
  matchLabels:
    app: {{ .Values.nameOverride | default .Release.Name }}
    release: {{ .Release.Name }}
```

**Purpose:**
- Links Deployment to Pods
- Must match template labels exactly

#### Pod Template
```yaml
template:
  metadata:
    labels:
      app: {{ .Values.nameOverride | default .Release.Name }}
      release: {{ .Release.Name }}
  spec:
    containers:
    - name: {{ .Values.nameOverride | default .Release.Name }}
      image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
      imagePullPolicy: {{ .Values.image.pullPolicy }}
```

**Image:**
- `repository`: `134.209.197.7:5000/my-app`
- `tag`: `42` (build number)
- **Result:** `134.209.197.7:5000/my-app:42`

#### Ports (Conditional)
```yaml
{{- if .Values.deployment.ports }}
ports:
{{- range .Values.deployment.ports }}
- name: port-{{ . }}
  containerPort: {{ . }}
  protocol: TCP
{{- end }}
{{- end }}
```

**Helm Template Logic:**
- `{{- if ... }}` - Only render if ports defined
- `{{- range ... }}` - Loop over each port
- `{{ . }}` - Current port number

**Example Output:**
```yaml
ports:
- name: port-8080
  containerPort: 8080
  protocol: TCP
- name: port-9090
  containerPort: 9090
  protocol: TCP
```

#### Resources (Conditional)
```yaml
{{- if .Values.deployment.resources }}
resources:
{{ toYaml .Values.deployment.resources | indent 10 }}
{{- end }}
```

**Template Functions:**
- `toYaml` - Converts object to YAML
- `indent 10` - Indents 10 spaces for proper YAML nesting

---

### service.yaml

**Purpose:** Creates a Kubernetes Service (network endpoint)

**Conditional Rendering:**
```yaml
{{- if .Values.deployment.ports }}
# ... service definition
{{- end }}
```

**Only creates Service if ports are defined!**

**Service Spec:**
```yaml
spec:
  type: {{ .Values.service.type }}
  ports:
  {{- range .Values.deployment.ports }}
  - port: {{ . }}
    targetPort: {{ . }}
    protocol: TCP
    name: port-{{ . }}
  {{- end }}
  selector:
    app: {{ .Values.nameOverride | default .Release.Name }}
    release: {{ .Release.Name }}
```

**Port Mapping:**
- `port`: External port (on Service)
- `targetPort`: Pod port (container)
- Usually the same for simplicity

**Selector:**
- Routes traffic to pods with matching labels
- Must match Deployment labels

## 🚀 Deployment Process

### Automatic Deployment via Jenkins

**Trigger:** Push to Git repository with `whanos.yml`

**Flow:**

```
1. Jenkins detects push
   ↓
2. Clones repository
   ↓
3. Detects language (Python, Java, etc.)
   ↓
4. Builds Docker image
   ↓
5. Pushes to registries (local + Docker Hub)
   ↓
6. Checks for whanos.yml
   ↓
7. Runs Helm command
   ↓
8. Kubernetes creates resources
   ↓
9. Application deployed!
```

### Helm Command (from Jenkins)

```bash
helm upgrade --install my-app /var/jenkins_home/helm/Whanos \
  --set image.repository=134.209.197.7:5000/my-app \
  --set image.tag=42 \
  --set nameOverride=my-app \
  --values whanos.yml \
  --namespace default \
  --create-namespace
```

**Parameters:**
- `upgrade --install` - Install or update
- `my-app` - Release name
- `/var/jenkins_home/helm/Whanos` - Chart path
- `--set image.repository=...` - Override image
- `--set image.tag=42` - Build number
- `--values whanos.yml` - User config
- `--namespace default` - K8s namespace

**Value Precedence:**
```
values.yaml  <  whanos.yml  <  --set flags
   (low)         (medium)       (high)
```

### Manual Deployment

**For testing or manual deployments:**

```bash
# Install new release
helm install my-app ./helm/Whanos \
  --set image.repository=myregistry/myapp \
  --set image.tag=latest \
  --set deployment.replicas=3

# Upgrade existing release
helm upgrade my-app ./helm/Whanos \
  --set image.tag=v2.0

# Uninstall
helm uninstall my-app
```

## 📊 Examples

### Example 1: Simple Python Flask App

**whanos.yml:**
```yaml
deployment:
  replicas: 1
  ports:
    - 5000
```

**Result:**
- 1 pod running
- Accessible via NodePort on port 5000
- No resource limits

**Access:**
```bash
kubectl get svc my-flask-app
# NAME            TYPE       CLUSTER-IP      EXTERNAL-IP   PORT(S)          AGE
# my-flask-app    NodePort   10.43.123.45    <none>        5000:30123/TCP   1m

curl http://NODE_IP:30123
```

---

### Example 2: Production Node.js API

**whanos.yml:**
```yaml
deployment:
  replicas: 5
  resources:
    limits:
      cpu: 1000m
      memory: 1Gi
    requests:
      cpu: 250m
      memory: 256Mi
  ports:
    - 3000
    - 9090  # Prometheus metrics

service:
  type: NodePort
```

**Result:**
- 5 pod replicas (high availability)
- Each pod guaranteed 250m CPU, 256Mi RAM
- Max 1 CPU core, 1 GB RAM per pod
- Exposes ports 3000 (API) and 9090 (metrics)

---

### Example 3: Java Spring Boot Microservice

**whanos.yml:**
```yaml
deployment:
  replicas: 3
  resources:
    limits:
      cpu: 2000m
      memory: 2Gi
    requests:
      cpu: 500m
      memory: 512Mi
  ports:
    - 8080  # Main API
    - 8081  # Health check
    - 9090  # Metrics

service:
  type: LoadBalancer  # If cloud provider available
```

**Result:**
- 3 pod replicas
- Higher resource allocation (Java needs more RAM)
- Multiple ports for different concerns
- LoadBalancer for external access (if supported)

---

### Example 4: Minimal Dev App

**whanos.yml:**
```yaml
deployment:
  ports:
    - 8080
```

**Result:**
- 1 replica (default)
- No resource limits (default)
- NodePort service on port 8080

**Use case:** Quick testing, development

## 🔍 Troubleshooting

### Common Issues

#### Issue: Pods not starting (ImagePullBackOff)

**Symptoms:**
```bash
kubectl get pods
# NAME                    READY   STATUS             RESTARTS   AGE
# my-app-xxxxx-xxxxx      0/1     ImagePullBackOff   0          1m
```

**Diagnosis:**
```bash
kubectl describe pod my-app-xxxxx-xxxxx
# Events:
#   Failed to pull image "134.209.197.7:5000/my-app:42": connection refused
```

**Solutions:**
1. Check registry is accessible from cluster nodes
2. Verify image exists: `curl http://134.209.197.7:5000/v2/my-app/tags/list`
3. Check K3s registry configuration: `/etc/rancher/k3s/registries.yaml`
4. Verify REGISTRY_URL in `.env` matches master IP

---

#### Issue: Pods crashing (CrashLoopBackOff)

**Symptoms:**
```bash
kubectl get pods
# NAME                    READY   STATUS             RESTARTS   AGE
# my-app-xxxxx-xxxxx      0/1     CrashLoopBackOff   5          3m
```

**Diagnosis:**
```bash
kubectl logs my-app-xxxxx-xxxxx
# Error: Cannot connect to database
# Error: Port 8080 already in use
```

**Solutions:**
1. Check application logs: `kubectl logs -f pod-name`
2. Verify application starts successfully
3. Check port conflicts
4. Verify environment variables
5. Check resource limits (not too restrictive)

---

#### Issue: Pods killed (OOMKilled)

**Symptoms:**
```bash
kubectl describe pod my-app-xxxxx-xxxxx
# Last State:   Terminated
#   Reason:     OOMKilled
```

**Solution:**
Increase memory limits in `whanos.yml`:
```yaml
deployment:
  resources:
    limits:
      memory: 1Gi  # Increased from 512Mi
```

---

#### Issue: Can't access application

**Symptoms:**
```bash
curl http://NODE_IP:30123
# Connection refused
```

**Diagnosis:**
```bash
# Check service
kubectl get svc my-app
# Check if service has endpoints
kubectl describe svc my-app
# Endpoints: 10.42.0.5:8080,10.42.0.6:8080,10.42.0.7:8080

# Check if pods are ready
kubectl get pods -l app=my-app
```

**Solutions:**
1. Verify pod is running and ready
2. Check NodePort is accessible: `netstat -tuln | grep 30123`
3. Verify firewall allows NodePort range (30000-32767)
4. Check application is listening on correct port inside container
5. Verify service selector matches pod labels

---

#### Issue: Deployment not updating

**Symptoms:**
Old version still running after Jenkins build

**Solution:**
```bash
# Force rollout restart
kubectl rollout restart deployment/my-app

# Check rollout status
kubectl rollout status deployment/my-app

# View rollout history
kubectl rollout history deployment/my-app

# Rollback to previous version
kubectl rollout undo deployment/my-app
```

### Useful Commands

**Get all resources for an app:**
```bash
kubectl get all -l app=my-app
```

**Watch pods in real-time:**
```bash
kubectl get pods -w
```

**Stream logs from all pods:**
```bash
kubectl logs -f deployment/my-app
```

**Get NodePort:**
```bash
kubectl get svc my-app -o jsonpath='{.spec.ports[0].nodePort}'
```

**Describe deployment:**
```bash
kubectl describe deployment my-app
```

**Scale deployment:**
```bash
kubectl scale deployment my-app --replicas=10
```

**Delete deployment:**
```bash
helm uninstall my-app
# Or manually:
kubectl delete deployment my-app
kubectl delete service my-app
```

## 📚 Related Documentation

- **[User Guide](USER_GUIDE.md)** - Getting started with Whanos
- **[Architecture Overview](ARCHITECTURE.md)** - System design
- **[Configuration Reference](CONFIGURATION.md)** - All configuration options
- **[Jenkins Documentation](JENKINS.md)** - CI/CD pipeline details

## 🔗 External Resources

- **Helm Documentation:** https://helm.sh/docs/
- **Kubernetes Deployments:** https://kubernetes.io/docs/concepts/workloads/controllers/deployment/
- **Kubernetes Services:** https://kubernetes.io/docs/concepts/services-networking/service/
- **Resource Management:** https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/
- **K3s Documentation:** https://docs.k3s.io/

---

**The Whanos Helm chart provides a simple, powerful way to deploy applications to Kubernetes with minimal configuration.** 🚀
