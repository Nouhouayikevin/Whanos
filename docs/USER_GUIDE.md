# Whanos User Guide

Welcome to Whanos! This guide will help you deploy your own Whanos infrastructure and start automatically deploying applications.

## 📋 Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Initial Setup](#initial-setup)
- [Deploying Your Infrastructure](#deploying-your-infrastructure)
- [Using Whanos](#using-whanos)
- [Next Steps](#next-steps)

## 🔧 Prerequisites

Before deploying Whanos, ensure you have:

### Local Machine Requirements
- **Ansible** 2.9 or higher
- **Git**
- SSH access to your deployment VMs

### Infrastructure Requirements
- **3 Ubuntu VMs** (20.04 or 22.04)
  - VM1: Jenkins master + Docker Registry + K3s master
  - VM2: K3s worker node
  - VM3: K3s worker node
- **Minimum 2GB RAM** per VM (4GB recommended)
- **Root access** to all VMs
- **Network connectivity** between all VMs

### Optional But Recommended
- **Docker Hub account** (for public image hosting)
- **GitHub account** (for private repository access)

## 🚀 Quick Start

### 1. Clone the Repository

```bash
git clone <your-whanos-repo>
cd whanos
```

### 2. Install Ansible

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install -y ansible sshpass

# Verify installation
ansible --version
```

### 3. Configure Your Environment

Create your environment configuration file:

```bash
cp .env.example .env
```

Edit `.env` with your settings:

```bash
# Jenkins Admin Password
ADMIN_PASSWORD=your_secure_password

# GitHub Credentials (for private repos)
GITHUB_USERNAME=your_github_username
GITHUB_TOKEN=ghp_your_github_token

# Docker Registry
REGISTRY_URL=your_master_vm_ip:5000

# Docker Hub Credentials (optional but recommended)
DOCKER_HUB_USERNAME=your_dockerhub_username
DOCKER_HUB_TOKEN=dckr_pat_your_dockerhub_token
```

### 4. Configure Your Inventory

Edit `ansible/inventory.ini`:

```ini
[jenkins_master]
master ansible_host=YOUR_MASTER_IP ansible_user=root ansible_ssh_pass=YOUR_PASSWORD

[k3s_master]
master ansible_host=YOUR_MASTER_IP ansible_user=root ansible_ssh_pass=YOUR_PASSWORD

[k3s_workers]
worker1 ansible_host=YOUR_WORKER1_IP ansible_user=root ansible_ssh_pass=YOUR_PASSWORD
worker2 ansible_host=YOUR_WORKER2_IP ansible_user=root ansible_ssh_pass=YOUR_PASSWORD

[all:vars]
ansible_python_interpreter=/usr/bin/python3
```

**Security Note:** For production, use SSH keys instead of passwords. See [Security Guide](SECURITY.md).

### 5. Deploy Whanos

Run the deployment script:

```bash
chmod +x deploy.sh
./deploy.sh
```

The deployment takes approximately **10-15 minutes** and includes:
- ✅ Docker installation on all VMs
- ✅ K3s cluster setup (1 master + 2 workers)
- ✅ Docker Registry deployment
- ✅ Jenkins server with custom configuration
- ✅ Base images for all supported languages

## 📊 Initial Setup

### Accessing Jenkins

Once deployment is complete:

1. **Open Jenkins** in your browser:
   ```
   http://YOUR_MASTER_IP:8080
   ```

2. **Login** with credentials:
   - Username: `admin`
   - Password: `<ADMIN_PASSWORD from .env>`

3. **Verify Installation:**
   - Go to "Whanos base images" folder
   - You should see jobs for: befunge, c, java, javascript, python
   - Click "Build all base images" to build all language base images

### Building Base Images

Base images are the foundation for your applications. Build them once:

1. Navigate to **"Whanos base images"**
2. Click **"Build all base images"**
3. Wait for completion (~5-10 minutes)

These images are automatically pushed to:
- **Local Registry:** `YOUR_MASTER_IP:5000/whanos-<language>:latest`
- **Docker Hub:** `YOUR_DOCKERHUB_USERNAME/whanos-<language>:latest`

### Verifying the Cluster

Check your Kubernetes cluster status:

```bash
# SSH to master VM
ssh root@YOUR_MASTER_IP

# Check cluster nodes
kubectl get nodes

# Expected output:
# NAME                    STATUS   ROLES                  AGE   VERSION
# whanos-master-xxx       Ready    control-plane,master   10m   v1.33.5+k3s1
# whanos-node-01-xxx      Ready    <none>                 9m    v1.33.5+k3s1
# whanos-node-02-xxx      Ready    <none>                 9m    v1.33.5+k3s1
```

## 🎯 Using Whanos

### Deploying Your First Application

#### Step 1: Prepare Your Repository

Your repository must follow the Whanos structure:

```
your-repo/
├── app/                    # Your application source code
│   ├── main.py            # (for Python)
│   ├── app.js             # (for JavaScript)
│   └── ...
├── whanos.yml             # (Optional) Deployment configuration
└── Dockerfile             # (Optional) Custom image configuration
```

**Example Python Application:**

`app/__main__.py`:
```python
from flask import Flask
app = Flask(__name__)

@app.route('/')
def hello():
    return "Hello from Whanos!"

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080)
```

`app/requirements.txt`:
```txt
flask==2.0.1
```

#### Step 2: Configure Deployment (Optional)

Create `whanos.yml` at repository root:

```yaml
deployment:
  replicas: 2
  resources:
    limits:
      cpu: 500m
      memory: 512Mi
    requests:
      cpu: 250m
      memory: 256Mi
  ports:
    - 8080

service:
  type: NodePort
```

#### Step 3: Link Your Repository to Jenkins

1. **Go to Jenkins** → **"Projects"** folder
2. **Click "link-project"**
3. **Enter your repository details:**
   - **DISPLAY_NAME:** `my-awesome-app`
   - **GIT_URL:** `https://github.com/yourusername/your-repo.git`
   - **BRANCH:** `main` (or your default branch)

4. **Click "Build with Parameters"**

Jenkins will:
- ✅ Clone your repository
- ✅ Detect the language (Python in this example)
- ✅ Build Docker image using `whanos-python` base
- ✅ Push to both registries
- ✅ Deploy to Kubernetes (if `whanos.yml` exists)

#### Step 4: Access Your Application

Find your application port:

```bash
# SSH to master VM
ssh root@YOUR_MASTER_IP

# Get services
kubectl get services

# Example output:
# NAME              TYPE       CLUSTER-IP      EXTERNAL-IP   PORT(S)          AGE
# my-awesome-app    NodePort   10.43.123.45    <none>        8080:30123/TCP   2m
```

Access your app:
```
http://YOUR_MASTER_IP:30123
```

## 🔄 Continuous Deployment

### Automatic Updates

To enable automatic deployments on Git push:

1. **In Jenkins**, go to your project job
2. **Configure** → **Build Triggers**
3. **Enable** "GitHub hook trigger for GITScm polling"
4. **In GitHub** (your repository):
   - Settings → Webhooks → Add webhook
   - Payload URL: `http://YOUR_MASTER_IP:8080/github-webhook/`
   - Content type: `application/json`
   - Events: "Just the push event"

Now, every push to your repository will trigger a new build and deployment!

### Manual Deployment

Simply go to your project job in Jenkins and click **"Build Now"**.

## 📱 Monitoring Your Deployments

### Via Jenkins

- **Build History:** See all builds in Jenkins job page
- **Console Output:** Click on build number → "Console Output"
- **Build Status:** Green = success, Red = failure

### Via Kubernetes

```bash
# List all deployments
kubectl get deployments

# List all pods
kubectl get pods

# Check pod logs
kubectl logs <pod-name>

# Describe pod (for troubleshooting)
kubectl describe pod <pod-name>
```

## 🛠️ Common Operations

### Scaling Your Application

Edit your `whanos.yml` and push:

```yaml
deployment:
  replicas: 5  # Scale to 5 replicas
```

Or scale manually:

```bash
kubectl scale deployment my-awesome-app --replicas=5
```

### Updating Your Application

1. Push changes to your Git repository
2. Jenkins automatically rebuilds (if webhook configured)
3. Or manually trigger build in Jenkins
4. New version is deployed automatically

### Deleting a Deployment

```bash
# Delete from Kubernetes
kubectl delete deployment my-awesome-app
kubectl delete service my-awesome-app

# Or use Helm
helm uninstall my-awesome-app
```

## 📚 Next Steps

Now that you have Whanos running, explore:

- **[Architecture Overview](ARCHITECTURE.md)** - Understand how Whanos works
- **[Configuration Reference](CONFIGURATION.md)** - Advanced configuration options
- **[Helm Charts](HELM.md)** - Customize Kubernetes deployments
- **[Troubleshooting](TROUBLESHOOTING.md)** - Common issues and solutions

## 🆘 Getting Help

### Check Logs

**Jenkins logs:**
```bash
ssh root@YOUR_MASTER_IP
docker logs whanos-jenkins
```

**K3s logs:**
```bash
journalctl -u k3s -f
```

**Application logs:**
```bash
kubectl logs -f deployment/my-awesome-app
```

### Common Issues

| Problem | Solution |
|---------|----------|
| Jenkins not accessible | Check port 8080 is open, verify container is running |
| Image pull errors | Verify registry URL in `.env`, check K3s can reach registry |
| Build fails | Check console output in Jenkins, verify repository structure |
| Pods not starting | Check `kubectl describe pod`, verify resource limits |

For detailed troubleshooting, see [Troubleshooting Guide](TROUBLESHOOTING.md).

## 🎓 Example Applications

Check the `whanos_example_apps/` directory for example applications in all supported languages:

- `python-hello-world/` - Flask web application
- `javascript-hello-world/` - Express.js server
- `java-hello-world/` - Spring Boot application
- `c-hello-world/` - Simple C application
- `befunge-hello-world/` - Befunge interpreter example

Each example includes:
- Complete source code in `app/` directory
- Dependencies file (if applicable)
- Optional `whanos.yml` for deployment

---

**Congratulations!** 🎉 You now have a fully functional Whanos infrastructure. Start deploying applications and enjoy automated DevOps!
