# Whanos Deployment Guide

Complete step-by-step guide for deploying Whanos infrastructure from scratch.

## 📋 Table of Contents

- [Prerequisites Check](#prerequisites-check)
- [Environment Preparation](#environment-preparation)
- [Deployment Process](#deployment-process)
- [Post-Deployment Verification](#post-deployment-verification)
- [Common Deployment Issues](#common-deployment-issues)

## ✅ Prerequisites Check

### Local Machine

Ensure your local machine has:

```bash
# Check Ansible installation
ansible --version
# Required: Ansible 2.9+

# Check Git
git --version

# Check SSH
ssh -V

# Install if missing (Ubuntu/Debian)
sudo apt update
sudo apt install -y ansible sshpass git openssh-client
```

### Virtual Machines

You need **3 Ubuntu VMs** with:

**Minimum Specifications:**
- OS: Ubuntu 20.04 or 22.04 LTS
- CPU: 2 cores
- RAM: 2GB (4GB recommended)
- Disk: 20GB
- Network: Public IP addresses
- Access: Root SSH access

**Verification Checklist:**

```bash
# Test SSH access to each VM
ssh root@VM1_IP "hostname && free -h && df -h"
ssh root@VM2_IP "hostname && free -h && df -h"
ssh root@VM3_IP "hostname && free -h && df -h"
```

Expected output for each:
- Hostname displayed
- At least 2GB RAM available
- At least 10GB disk free

### Network Requirements

**Ports to be used:**

| Port | Service | Access |
|------|---------|--------|
| 8080 | Jenkins Web UI | External |
| 50000 | Jenkins Agent | Internal |
| 5000 | Docker Registry | Internal |
| 6443 | Kubernetes API | Internal |
| 10250 | Kubelet API | Internal |
| 30000-32767 | NodePort Services | External |

**Check port availability:**

```bash
# On each VM
ssh root@VM_IP "netstat -tulpn | grep -E ':(8080|5000|6443|10250)'"
# Should return nothing (ports free)
```

## 🔧 Environment Preparation

### Step 1: Clone Repository

```bash
git clone https://github.com/YourOrg/whanos.git
cd whanos
```

### Step 2: Configure Environment Variables

Create `.env` file from template:

```bash
cp .env.example .env
```

Edit `.env` with your actual values:

```bash
# ===== JENKINS CONFIGURATION =====
# Admin password for Jenkins web interface
ADMIN_PASSWORD=your_secure_password_here

# ===== GITHUB CREDENTIALS =====
# Required for accessing private repositories
GITHUB_USERNAME=your_github_username
# Generate token at: https://github.com/settings/tokens
# Required scopes: repo, read:packages
GITHUB_TOKEN=ghp_your_github_personal_access_token

# ===== DOCKER REGISTRY =====
# IP address of your master VM (VM1)
REGISTRY_URL=YOUR_MASTER_VM_IP:5000

# ===== DOCKER HUB CREDENTIALS =====
# Optional but recommended for public image hosting
DOCKER_HUB_USERNAME=your_dockerhub_username
# Generate token at: https://hub.docker.com/settings/security
DOCKER_HUB_TOKEN_HERE=dckr_pat_your_dockerhub_access_token
```

**Security Best Practices:**
- Use strong passwords (16+ characters, mixed case, symbols)
- Never commit `.env` file (it's in `.gitignore`)
- Rotate tokens regularly
- Use least privilege access tokens

### Step 3: Configure Inventory

Edit `ansible/inventory.ini`:

```ini
# ===== JENKINS MASTER =====
[jenkins_master]
master ansible_host=YOUR_MASTER_VM_IP ansible_user=root ansible_ssh_pass=YOUR_VM_PASSWORD

# ===== KUBERNETES MASTER =====
[k3s_master]
master ansible_host=YOUR_MASTER_VM_IP ansible_user=root ansible_ssh_pass=YOUR_VM_PASSWORD

# ===== KUBERNETES WORKERS =====
[k3s_workers]
worker1 ansible_host=YOUR_WORKER1_VM_IP ansible_user=root ansible_ssh_pass=YOUR_VM_PASSWORD
worker2 ansible_host=YOUR_WORKER2_VM_IP ansible_user=root ansible_ssh_pass=YOUR_VM_PASSWORD

# ===== GLOBAL VARIABLES =====
[all:vars]
ansible_python_interpreter=/usr/bin/python3
```

**Replace:**
- `YOUR_MASTER_VM_IP` - IP of VM1 (master node)
- `YOUR_WORKER1_VM_IP` - IP of VM2 (worker 1)
- `YOUR_WORKER2_VM_IP` - IP of VM3 (worker 2)
- `YOUR_VM_PASSWORD` - Root password for VMs

**Production Setup (SSH Keys):**

For production, use SSH keys instead of passwords:

```bash
# Generate SSH key if you don't have one
ssh-keygen -t ed25519 -C "whanos-deployment"

# Copy to all VMs
ssh-copy-id root@YOUR_MASTER_VM_IP
ssh-copy-id root@YOUR_WORKER1_VM_IP
ssh-copy-id root@YOUR_WORKER2_VM_IP

# Update inventory.ini
[jenkins_master]
master ansible_host=YOUR_MASTER_VM_IP ansible_user=root

[k3s_master]
master ansible_host=YOUR_MASTER_VM_IP ansible_user=root

[k3s_workers]
worker1 ansible_host=YOUR_WORKER1_VM_IP ansible_user=root
worker2 ansible_host=YOUR_WORKER2_VM_IP ansible_user=root

[all:vars]
ansible_python_interpreter=/usr/bin/python3
```

### Step 4: Verify Configuration

```bash
# Test Ansible connectivity
ansible all -i ansible/inventory.ini -m ping

# Expected output:
# master | SUCCESS => { "ping": "pong" }
# worker1 | SUCCESS => { "ping": "pong" }
# worker2 | SUCCESS => { "ping": "pong" }
```

If ping fails:
- Check VM IP addresses
- Verify SSH access manually
- Check password/SSH key
- Verify firewall allows SSH (port 22)

## 🚀 Deployment Process

### Deployment Overview

The deployment consists of **7 phases**:

1. **System Prerequisites** (2-3 min)
2. **Docker Installation** (3-4 min)
3. **K3s Master Setup** (2-3 min)
4. **K3s Workers Join** (1-2 min)
5. **Registry Deployment** (1 min)
6. **K3s Registry Config** (1 min)
7. **Jenkins Deployment** (2-3 min)

**Total Time:** ~15 minutes

### Running the Deployment

```bash
# Make deployment script executable
chmod +x deploy.sh

# Run deployment
./deploy.sh
```

### What Happens During Deployment

**Phase 1: System Prerequisites**
```
✓ Clean old Docker configurations
✓ Update apt package cache
✓ Install curl, wget, git, ca-certificates
✓ Install Python 3 and pip
```

**Phase 2: Docker Installation**
```
✓ Add Docker GPG key
✓ Add Docker repository
✓ Install Docker Engine 27.4.1
✓ Start and enable Docker service
✓ Add user to docker group
✓ Test Docker installation
```

**Phase 3: K3s Master Setup**
```
✓ Download K3s v1.33.5+k3s1
✓ Install K3s server
✓ Configure kubeconfig
✓ Wait for node to be Ready
✓ Save cluster join token
```

**Phase 4: K3s Workers Join**
```
✓ Download K3s on workers
✓ Install K3s agent
✓ Join workers to cluster
✓ Verify all nodes Ready
```

**Phase 5: Registry Deployment**
```
✓ Pull registry:2 image
✓ Create registry volume
✓ Start registry container on port 5000
✓ Verify registry is accessible
```

**Phase 6: K3s Registry Configuration**
```
✓ Create containerd config directory
✓ Configure insecure registry
✓ Restart K3s services
✓ Verify configuration applied
```

**Phase 7: Jenkins Deployment**
```
✓ Copy Dockerfile.jenkins to master
✓ Copy Jenkins configuration files
✓ Copy base image Dockerfiles
✓ Build whanos-jenkins image
✓ Start Jenkins container
✓ Inject environment variables
✓ Wait for Jenkins to start
```

### Monitoring Deployment Progress

The deployment script provides real-time feedback:

```
██╗    ██╗██╗  ██╗ █████╗ ███╗   ██╗ ██████╗ ███████╗
[...]
Automatically Deploy (Nearly) Anything

📝 Chargement de .env...
✓ Fichier .env chargé

🚀 Démarrage du déploiement Whanos...

Configuration:
  Master: 134.209.197.7
  Workers: 2
  Registry: 134.209.197.7:5000
  Jenkins Port: 8080

[PHASE 1/7] Prérequis système...
[PHASE 2/7] Installation Docker...
[PHASE 3/7] Configuration K3s Master...
[PHASE 4/7] Ajout Workers K3s...
[PHASE 5/7] Déploiement Registry...
[PHASE 6/7] Configuration Registry K3s...
[PHASE 7/7] Déploiement Jenkins...

✅ Déploiement terminé avec succès!

Accès Jenkins: http://134.209.197.7:8080
  Username: admin
  Password: <from .env>
```

### Handling Errors During Deployment

If deployment fails at any phase:

1. **Read the error message carefully**
2. **Check the specific phase logs**
3. **Common fixes:**

```bash
# Phase 1 - APT lock errors
ssh root@VM_IP "killall -9 apt apt-get dpkg; rm -f /var/lib/dpkg/lock*"

# Phase 2 - Docker installation fails
ssh root@VM_IP "apt remove -y docker docker-engine docker.io containerd runc"
# Re-run deployment

# Phase 3 - K3s master not ready
ssh root@MASTER_IP "systemctl status k3s"
ssh root@MASTER_IP "journalctl -u k3s -n 100"

# Phase 4 - Workers can't join
ssh root@WORKER_IP "systemctl status k3s-agent"
# Check firewall allows port 6443

# Phase 7 - Jenkins won't start
ssh root@MASTER_IP "docker logs whanos-jenkins"
```

4. **Re-run deployment:**
   - Ansible is idempotent (safe to re-run)
   - It will skip completed tasks

```bash
./deploy.sh
```

## ✅ Post-Deployment Verification

### Step 1: Verify Cluster Status

```bash
# SSH to master VM
ssh root@YOUR_MASTER_VM_IP

# Check all nodes are Ready
kubectl get nodes

# Expected output:
# NAME                    STATUS   ROLES                  AGE   VERSION
# whanos-master-xxx       Ready    control-plane,master   5m    v1.33.5+k3s1
# whanos-node-01-xxx      Ready    <none>                 4m    v1.33.5+k3s1
# whanos-node-02-xxx      Ready    <none>                 4m    v1.33.5+k3s1
```

All nodes should show **STATUS: Ready**

### Step 2: Verify Docker Registry

```bash
# Test registry from master
curl http://localhost:5000/v2/_catalog

# Expected output:
# {"repositories":[]}  # Empty initially, this is OK

# Test registry from worker nodes
ssh root@WORKER1_IP "curl http://YOUR_MASTER_IP:5000/v2/_catalog"
ssh root@WORKER2_IP "curl http://YOUR_MASTER_IP:5000/v2/_catalog"
```

### Step 3: Verify Jenkins

**Web Interface:**
1. Open browser: `http://YOUR_MASTER_IP:8080`
2. Login with:
   - Username: `admin`
   - Password: `<ADMIN_PASSWORD from .env>`

**Expected Jenkins homepage:**
- "Whanos base images" folder visible
- "Projects" folder visible
- "link-project" job visible

**Container Status:**
```bash
ssh root@MASTER_IP "docker ps | grep jenkins"

# Expected output shows whanos-jenkins container running
```

### Step 4: Build Base Images

This is a critical post-deployment step:

1. **In Jenkins, navigate to:** "Whanos base images"
2. **Click:** "Build all base images"
3. **Wait for completion:** ~5-10 minutes

**Monitor progress:**
- Click on build number (e.g., #1)
- View "Console Output"

**Verification:**

```bash
# Check images in local registry
curl http://YOUR_MASTER_IP:5000/v2/_catalog

# Expected output:
# {"repositories":["whanos-befunge","whanos-c","whanos-java","whanos-javascript","whanos-python"]}

# Check Docker Hub (if configured)
# Visit: https://hub.docker.com/r/YOUR_DOCKERHUB_USERNAME/repositories
# You should see: whanos-befunge, whanos-c, whanos-java, whanos-javascript, whanos-python
```

### Step 5: Deploy Test Application

**Using example Python app:**

```bash
# In Jenkins:
# 1. Go to "Projects" folder
# 2. Click "link-project"
# 3. Click "Build with Parameters"
# 4. Enter:
#    DISPLAY_NAME: test-python-app
#    GIT_URL: https://github.com/YourOrg/whanos.git
#    BRANCH: main
#    REPO_SUBDIR: whanos_example_apps/python-hello-world
# 5. Click "Build"
```

**Verify deployment:**

```bash
ssh root@MASTER_IP

# Check pod is running
kubectl get pods

# Expected output:
# NAME                              READY   STATUS    RESTARTS   AGE
# test-python-app-xxxxxxxxxx-xxxxx  1/1     Running   0          30s

# Check service
kubectl get svc

# Get NodePort
kubectl get svc test-python-app -o jsonpath='{.spec.ports[0].nodePort}'
# Example output: 30123

# Test application
curl http://YOUR_MASTER_IP:30123
# Should return application response
```

### Step 6: Verify Complete System Health

```bash
# All Kubernetes resources
kubectl get all --all-namespaces

# Cluster info
kubectl cluster-info

# Node resource usage
kubectl top nodes  # May not work if metrics-server not installed

# Docker containers on master
ssh root@MASTER_IP "docker ps"

# Expected containers:
# - whanos-jenkins
# - whanos-registry
```

## 📊 Deployment Checklist

Print this checklist and mark items as you complete them:

- [ ] Local machine has Ansible 2.9+
- [ ] 3 Ubuntu VMs provisioned with public IPs
- [ ] Each VM has 2+ GB RAM, 20+ GB disk
- [ ] SSH access to all VMs verified
- [ ] `.env` file created with all credentials
- [ ] `ansible/inventory.ini` configured with VM IPs
- [ ] `ansible all -m ping` succeeds
- [ ] `./deploy.sh` executed successfully
- [ ] All 7 deployment phases completed
- [ ] `kubectl get nodes` shows 3 Ready nodes
- [ ] Registry accessible at `MASTER_IP:5000`
- [ ] Jenkins accessible at `MASTER_IP:8080`
- [ ] Successfully logged into Jenkins
- [ ] "Build all base images" job completed
- [ ] All 5 base images in registry
- [ ] Test application deployed successfully
- [ ] Test application accessible via NodePort

## 🐛 Common Deployment Issues

### Issue: "Connection timeout" during Ansible

**Cause:** VMs not accessible or wrong IPs

**Solution:**
```bash
# Test SSH manually
ssh root@VM_IP

# Check firewall
# VMs must allow SSH (port 22)
```

### Issue: "Port 8080 already in use"

**Cause:** Existing service on port 8080

**Solution:**
```bash
ssh root@MASTER_IP

# Check what's using port 8080
netstat -tulpn | grep 8080

# If it's an old Jenkins:
systemctl stop jenkins
systemctl disable jenkins

# Re-run deployment
```

### Issue: "K3s workers not joining"

**Cause:** Network connectivity or firewall

**Solution:**
```bash
# From worker, test master connectivity
ssh root@WORKER_IP "curl -k https://MASTER_IP:6443"

# Check K3s token on master
ssh root@MASTER_IP "cat /var/lib/rancher/k3s/server/node-token"

# Check worker logs
ssh root@WORKER_IP "journalctl -u k3s-agent -n 100"

# Manually retry join
ssh root@WORKER_IP "systemctl restart k3s-agent"
```

### Issue: "Registry connection refused"

**Cause:** Registry not running or not configured

**Solution:**
```bash
# Check registry container
ssh root@MASTER_IP "docker ps | grep registry"

# Restart registry
ssh root@MASTER_IP "docker restart whanos-registry"

# Verify K3s configuration
ssh root@MASTER_IP "cat /etc/rancher/k3s/registries.yaml"
```

### Issue: "ImagePullBackOff" in pods

**Cause:** K3s can't pull from registry

**Solution:**
```bash
# Check if image exists in registry
curl http://MASTER_IP:5000/v2/<image-name>/tags/list

# Check K3s can reach registry from workers
ssh root@WORKER_IP "curl http://MASTER_IP:5000/v2/_catalog"

# Describe failing pod
kubectl describe pod <pod-name>
```

## 🔄 Redeployment

To completely redeploy:

```bash
# Clean all VMs
ssh root@MASTER_IP "k3s-uninstall.sh; docker stop \$(docker ps -aq); docker rm \$(docker ps -aq)"
ssh root@WORKER1_IP "k3s-agent-uninstall.sh"
ssh root@WORKER2_IP "k3s-agent-uninstall.sh"

# Re-run deployment
./deploy.sh
```

## 📚 Next Steps

After successful deployment:

- **[User Guide](USER_GUIDE.md)** - Start deploying applications
- **[Configuration Reference](CONFIGURATION.md)** - Advanced configuration
- **[Architecture Overview](ARCHITECTURE.md)** - Understand the system
- **[Troubleshooting](TROUBLESHOOTING.md)** - Fix common issues

---

**Deployment complete!** 🎉 Your Whanos infrastructure is ready to automatically deploy applications.
