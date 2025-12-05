# Whanos - Automatically Deploy (Nearly) Anything

<div align="center">

**Automated deployment infrastructure that detects, builds, and deploys applications to Kubernetes with a single Git push.**

[![Kubernetes](https://img.shields.io/badge/kubernetes-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white)](https://kubernetes.io/)
[![Docker](https://img.shields.io/badge/docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![Jenkins](https://img.shields.io/badge/jenkins-D24939?style=for-the-badge&logo=jenkins&logoColor=white)](https://www.jenkins.io/)
[![Ansible](https://img.shields.io/badge/ansible-EE0000?style=for-the-badge&logo=ansible&logoColor=white)](https://www.ansible.com/)

</div>

---

## Overview

**Whanos** is a powerful DevOps infrastructure that combines Ansible, Jenkins, Docker, and Kubernetes to provide automated application deployment. Simply push your code to a Git repository, and Whanos handles the rest:

1. **Detects** your application's technology
2. **Builds** a Docker image using language-specific base images
3. **Pushes** to both local registry and Docker Hub
4. **Deploys** to a Kubernetes cluster (if configured)

Perfect for teams wanting **GitOps-style automation** without complex CI/CD setup.

## **Features**

- **Multi-language Support:** Python, JavaScript, Java, C, Befunge
- **Automatic Detection:** Recognizes technology from repository structure
- **Dual Registry:** Local registry + Docker Hub backup
- **Kubernetes Ready:** Built-in K3s cluster with Helm deployments
- **Secure:** Credentials management via Jenkins Configuration as Code
- **Observable:** Full build logs and deployment tracking
- ⚡ **Fast:** ~15 minute infrastructure deployment

## Quick Start

### Prerequisites

- **3 Ubuntu VMs** (20.04 or 22.04)
  - VM1: Jenkins master + Docker Registry + K3s master
  - VM2 & VM3: K3s worker nodes
- **Local machine** with Ansible 2.9+
- **Root SSH access** to all VMs

## Installation

### 1. Clone and Configure

```bash
# Clone repository
git clone git@github.com:Nouhouayikevin/Whanos.git
cd whanos

# Configure environment
cp .env.example .env
nano .env  # Edit with your credentials
```

**Required `.env` variables:**
```bash
ADMIN_PASSWORD=your_secure_password
REGISTRY_URL=YOUR_MASTER_IP:5000
DOCKER_HUB_USERNAME=your_dockerhub_username  # Optional but recommended
DOCKER_HUB_TOKEN=your_dockerhub_token
GITHUB_USERNAME=your_github_username         # For private repos
GITHUB_TOKEN=your_github_token
```

### 2. Configure Inventory

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

### 3. Deploy Infrastructure

```bash
# Install Ansible if needed
sudo apt install -y ansible sshpass

# Run deployment script (takes ~15 minutes)
chmod +x deploy.sh
./deploy.sh
```

**What gets deployed:**
- Docker Engine on all VMs
- K3s cluster (1 master + 2 workers)
- Docker Registry on port 5000
- Jenkins with custom configuration
- Base images for all supported languages
- Jenkins jobs for building and deploying

## Usage

### Access Jenkins

Once deployed, access Jenkins at:
- **URL:** `http://YOUR_MASTER_IP:8080`
- **Username:** `admin`
- **Password:** From your `.env` file

### Deploy Your First Application

**1. Prepare your repository:**

Your repository must have an `app/` directory with your source code:

```
your-repo/
├── app/
│   ├── __main__.py     # For Python
│   ├── app.js          # For JavaScript
│   └── requirements.txt
└── whanos.yml          # Optional: deployment config
```

**2. Link project in Jenkins:**

- Go to **"Projects"** → **"link-project"**
- Click **"Build with Parameters"**
- Enter:
  - `DISPLAY_NAME`: my-app
  - `GIT_URL`: https://github.com/user/repo.git
  - `BRANCH`: main

**3. Deploy automatically (optional):**

Add `whanos.yml` to your repository root:

```yaml
deployment:
  replicas: 3
  resources:
    limits:
      cpu: 500m
      memory: 512Mi
    requests:
      cpu: 100m
      memory: 128Mi
  ports:
    - 8080

service:
  type: NodePort
```

Jenkins will automatically deploy to Kubernetes!

### Supported Languages

| Language | Detection File | Example App |
|----------|----------------|-------------|
| Python | `requirements.txt` | [whanos_example_apps/python-hello-world](whanos_example_apps/python-hello-world) |
| JavaScript | `package.json` | [whanos_example_apps/js-hello-world](whanos_example_apps/js-hello-world) |
| Java | `app/pom.xml` | [whanos_example_apps/java-hello-world](whanos_example_apps/java-hello-world) |
| C | `Makefile` | [whanos_example_apps/c-hello-world](whanos_example_apps/c-hello-world) |
| Befunge | `app/main.bf` | [whanos_example_apps/befunge-hello-world](whanos_example_apps/befunge-hello-world) |

##  Architecture

```
┌─────────────┐
│  Developer  │
│  Git Push   │
└──────┬──────┘
       │
       ▼
┌──────────────────┐      ┌─────────────────┐
│  Jenkins Master  │─────▶│  Docker Registry│
│  (Build & Deploy)│      │  (Local + Hub)  │
└────────┬─────────┘      └─────────────────┘
         │
         ▼
┌────────────────────────────────┐
│    Kubernetes Cluster (K3s)    │
│  ┌────────┐ ┌────────┐ ┌─────┐│
│  │ Master │ │Worker 1│ │Wrk 2││
│  └────────┘ └────────┘ └─────┘│
│         Application Pods       │
└────────────────────────────────┘
```

**Components:**
- **Ansible:** Infrastructure automation (Docker, K3s, Jenkins, Registry)
- **Jenkins:** CI/CD orchestration with Job DSL
- **Docker Registry:** Local image storage (+ Docker Hub backup)
- **K3s:** Lightweight Kubernetes cluster
- **Helm:** Application deployment templating

## Project Structure

```
whanos/
├── ansible/
│   ├── deploy_whanos.yml    # Main deployment playbook (7 phases)
│   └── inventory.ini         # VM inventory
├── jenkins/
│   ├── config.yml           # Jenkins Configuration as Code
│   ├── job_dsl.groovy       # Dynamic job creation
│   └── plugins.txt          # Required Jenkins plugins
├── helm/
│   └── Whanos/              # Kubernetes deployment chart
├── images/                   # Base images for each language
│   ├── python/
│   ├── javascript/
│   ├── java/
│   ├── c/
│   └── befunge/
├── whanos_example_apps/     # Example applications
├── docs/                    # Complete documentation
│   ├── USER_GUIDE.md       # Getting started guide
│   ├── ARCHITECTURE.md     # Technical deep dive
│   ├── DEPLOYMENT.md       # Step-by-step deployment
│   ├── CONFIGURATION.md    # All config options
│   ├── ANSIBLE.md          # Ansible playbook details
│   └── JENKINS.md          # Jenkins setup details
├── deploy.sh                # One-command deployment script
└── .env.example            # Environment variables template
```

## Documentation

**Complete documentation is available in the [`docs/`](docs/) directory:**

- **[User Guide](docs/USER_GUIDE.md)** - Start here for deployment and usage
- **[Architecture Overview](docs/ARCHITECTURE.md)** - System design and components
- **[Deployment Guide](docs/DEPLOYMENT.md)** - Detailed installation steps
- **[Configuration Reference](docs/CONFIGURATION.md)** - All configuration options
- **[Ansible Documentation](docs/ANSIBLE.md)** - Playbook technical details
- **[Jenkins Documentation](docs/JENKINS.md)** - Jenkins setup and jobs

## Security Best Practices

- **Never commit `.env`** - Contains sensitive credentials
- Use **SSH keys** instead of passwords for production
- Rotate **GitHub and Docker Hub tokens** regularly
- Use **ansible-vault** for encrypting `inventory.ini`
- Configure **firewall rules** on VMs (ports 8080, 5000, 6443)

## Troubleshooting

### Quick Checks

```bash
# Check cluster status
ssh root@MASTER_IP "kubectl get nodes"

# Check Jenkins
curl http://MASTER_IP:8080/login

# Check Registry
curl http://MASTER_IP:5000/v2/_catalog

# Check deployments
ssh root@MASTER_IP "kubectl get pods"
```

### Common Issues

| Issue | Solution |
|-------|----------|
| Port 8080 in use | `systemctl stop jenkins && systemctl disable jenkins` |
| ImagePullBackOff | Verify registry URL in `.env` matches master IP |
| Worker nodes not joining | Check firewall allows port 6443 |
| Jenkins builds fail | Check Docker socket mounted: `/var/run/docker.sock` |

**For detailed troubleshooting**, see [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)

## Example Applications

Check [`whanos_example_apps/`](whanos_example_apps/) for ready-to-deploy examples:

- **Python Flask app** - `python-hello-world/`
- **Node.js Express app** - `js-hello-world/`
- **Java Spring Boot** - `java-hello-world/`
- **C application** - `c-hello-world/`
- **Befunge interpreter** - `befunge-hello-world/`

Each includes complete source code and optional `whanos.yml` configuration.

## Contributing

This project is part of the **Epitech DevOps curriculum** (G-DOP-500).

**Team:**
- Infrastructure automation with Ansible
- CI/CD orchestration with Jenkins
- Container orchestration with Kubernetes
- Multi-language application support

## License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

**Copyright © 2025 Kevin Nouhouayi & Daniel Kalambo - Epitech**

---

<div align="center">

**Ready to deploy?** Start with the **[User Guide](docs/USER_GUIDE.md)** 

</div>
