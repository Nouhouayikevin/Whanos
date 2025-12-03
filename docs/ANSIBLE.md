# Whanos Ansible Documentation

Technical documentation for the Ansible automation playbook that deploys the entire Whanos infrastructure.

## 📋 Table of Contents

- [Overview](#overview)
- [Playbook Structure](#playbook-structure)
- [Deployment Phases](#deployment-phases)
- [Variables and Configuration](#variables-and-configuration)
- [Tasks Deep Dive](#tasks-deep-dive)
- [Troubleshooting](#troubleshooting)

## 🎯 Overview

The Whanos Ansible playbook (`ansible/deploy_whanos.yml`) is a comprehensive automation script that:

- Provisions 3 Ubuntu VMs into a complete DevOps infrastructure
- Installs and configures Docker on all nodes
- Sets up a 3-node Kubernetes cluster using K3s
- Deploys a Docker Registry for image storage
- Configures and launches a customized Jenkins CI/CD server
- Handles all networking and security configuration

**Key Statistics:**
- **Lines of Code:** 448
- **Number of Phases:** 7
- **Total Tasks:** 50+
- **Deployment Time:** ~15 minutes
- **Target OS:** Ubuntu 20.04 / 22.04

## 📁 Playbook Structure

### File Organization

```
ansible/
├── deploy_whanos.yml      # Main playbook (448 lines)
├── inventory.ini          # VM inventory
└── inventory.ini.example  # Inventory template
```

### Playbook Anatomy

```yaml
---
# Whanos - Déploiement complet infrastructure
# 3 VMs: Jenkins/Registry/K3s-master + 2 K3s workers

# Phase 1: System prerequisites (all hosts)
- name: "Phase 1: Prérequis système"
  hosts: all
  become: yes
  gather_facts: yes
  tasks: [...]

# Phase 2: Docker installation (all hosts)
- name: "Phase 2: Installation Docker"
  hosts: all
  become: yes
  tasks: [...]

# Phases 3-7: Specialized configuration per host group
[...]
```

## 🔧 Deployment Phases

### Phase 1: System Prerequisites

**Targets:** All hosts (master + workers)

**Duration:** 2-3 minutes

**Purpose:** Prepare clean Ubuntu systems for Docker and K3s

**Tasks:**

```yaml
Tasks:
1. Find and remove old Docker configuration files
2. Clean apt cache and update package lists
3. Wait for apt lock release (handles concurrent apt processes)
4. Install base dependencies:
   - curl, wget, git
   - ca-certificates, gnupg
   - apt-transport-https
   - Python 3 and pip
5. Verify Python installation
```

**Technical Details:**

**Docker cleanup:**
```yaml
- name: Find all Docker files in sources.list.d
  find:
    paths: /etc/apt/sources.list.d/
    patterns: '*docker*'
  register: docker_sources

- name: Remove all Docker source files
  file:
    path: "{{ item.path }}"
    state: absent
  loop: "{{ docker_sources.files }}"
```

This ensures no conflicts with previous Docker installations.

**APT lock handling:**
```yaml
- name: Wait for APT lock release
  shell: while fuser /var/lib/dpkg/lock-frontend >/dev/null 2>&1; do sleep 1; done
  changed_when: false
  ignore_errors: yes
```

Prevents "Could not get lock" errors during automated installation.

---

### Phase 2: Docker Installation

**Targets:** All hosts

**Duration:** 3-4 minutes

**Purpose:** Install Docker Engine on all VMs

**Tasks:**

```yaml
Tasks:
1. Create Docker keyring directory
2. Download and add Docker GPG key
3. Detect system architecture (amd64/arm64)
4. Add Docker official repository
5. Update apt cache
6. Install Docker Engine (specific version)
7. Ensure Docker service is running and enabled
8. Add ansible user to docker group
9. Verify Docker installation with 'docker --version'
```

**Technical Details:**

**Docker version pinning:**
```yaml
- name: Install Docker Engine
  apt:
    name:
      - docker-ce=5:27.4.1-1~ubuntu.22.04~jammy
      - docker-ce-cli=5:27.4.1-1~ubuntu.22.04~jammy
      - containerd.io
    state: present
    update_cache: yes
```

Pins to Docker 27.4.1 for consistency across all nodes.

**Repository configuration:**
```yaml
- name: Add Docker repository
  apt_repository:
    repo: "deb [arch={{ arch_mapping[ansible_architecture] }} signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu {{ ansible_distribution_release }} stable"
    state: present
    filename: docker
```

Uses detected architecture and Ubuntu release for correct repository.

---

### Phase 3: K3s Master Setup

**Targets:** k3s_master group (master VM only)

**Duration:** 2-3 minutes

**Purpose:** Initialize Kubernetes control plane

**Tasks:**

```yaml
Tasks:
1. Check if K3s is already installed
2. Download K3s binary (specific version)
3. Install K3s as server (control plane)
4. Wait for K3s service to start
5. Create .kube directory for kubectl config
6. Copy kubeconfig for kubectl access
7. Wait for master node to be Ready
8. Retrieve K3s join token for workers
9. Display cluster status
```

**Technical Details:**

**K3s installation:**
```yaml
- name: Install K3s on master
  shell: |
    curl -sfL https://get.k3s.io | \
    INSTALL_K3S_VERSION="{{ k3s_version }}" \
    sh -s - server \
      --write-kubeconfig-mode 644 \
      --disable traefik
  args:
    creates: /usr/local/bin/k3s
```

**Key options:**
- `--write-kubeconfig-mode 644` - Makes kubeconfig readable
- `--disable traefik` - Disables default ingress (we use NodePort)

**Node readiness check:**
```yaml
- name: Wait for master node to be Ready
  shell: kubectl get nodes | grep -w Ready
  register: node_ready
  until: node_ready.rc == 0
  retries: 30
  delay: 5
```

Ensures control plane is fully operational before proceeding.

**Token retrieval:**
```yaml
- name: Get K3s token
  slurp:
    src: /var/lib/rancher/k3s/server/node-token
  register: k3s_token_file

- name: Save K3s token
  set_fact:
    k3s_token: "{{ k3s_token_file.content | b64decode | trim }}"
```

Token is base64 encoded in file, must decode for use.

---

### Phase 4: K3s Workers Join

**Targets:** k3s_workers group (worker VMs)

**Duration:** 1-2 minutes per worker

**Purpose:** Join worker nodes to cluster

**Tasks:**

```yaml
Tasks:
1. Check if K3s agent already installed
2. Download K3s binary
3. Install K3s as agent (worker)
4. Wait for K3s agent service to start
5. Verify worker joined cluster (from master)
6. Display all cluster nodes
```

**Technical Details:**

**Worker installation:**
```yaml
- name: Install K3s agent on workers
  shell: |
    curl -sfL https://get.k3s.io | \
    INSTALL_K3S_VERSION="{{ k3s_version }}" \
    K3S_URL=https://{{ hostvars['master']['ansible_host'] }}:6443 \
    K3S_TOKEN="{{ hostvars['master']['k3s_token'] }}" \
    sh -s - agent
  args:
    creates: /usr/local/bin/k3s
```

**Key variables:**
- `K3S_URL` - Master API server endpoint (port 6443)
- `K3S_TOKEN` - Secure token from master (retrieved in Phase 3)

**Join verification:**
```yaml
- name: Verify workers joined (from master)
  shell: kubectl get nodes
  register: cluster_nodes
  until: cluster_nodes.stdout_lines | length >= 3
  retries: 20
  delay: 5
  delegate_to: master
```

Ensures all 3 nodes appear in cluster before continuing.

---

### Phase 5: Docker Registry Deployment

**Targets:** jenkins_master group (master VM)

**Duration:** 1 minute

**Purpose:** Deploy local Docker Registry for cluster

**Tasks:**

```yaml
Tasks:
1. Pull official registry:2 image
2. Create persistent volume directory
3. Stop any existing registry container
4. Remove old registry container
5. Start new registry container
6. Wait for registry to be healthy
7. Test registry with curl
8. Display registry catalog
```

**Technical Details:**

**Registry container:**
```yaml
- name: Start Registry container
  docker_container:
    name: whanos-registry
    image: registry:2
    state: started
    restart_policy: unless-stopped
    ports:
      - "5000:5000"
    volumes:
      - /var/lib/registry:/var/lib/registry
```

**Key configuration:**
- Port 5000 (HTTP, no TLS for simplicity)
- Persistent volume at `/var/lib/registry`
- `restart_policy: unless-stopped` - Auto-restart on boot

**Health check:**
```yaml
- name: Wait for registry to be ready
  uri:
    url: "http://localhost:5000/v2/"
    status_code: 200
  register: registry_health
  until: registry_health.status == 200
  retries: 30
  delay: 2
```

Ensures registry API is responding before proceeding.

---

### Phase 6: K3s Registry Configuration

**Targets:** all hosts

**Duration:** 1 minute

**Purpose:** Configure K3s to trust insecure registry

**Tasks:**

```yaml
Tasks:
1. Create K3s registries config directory
2. Generate registries.yaml with insecure registry config
3. Restart K3s service on master
4. Restart K3s agent on workers
5. Wait for services to stabilize
6. Verify configuration applied
```

**Technical Details:**

**Registry configuration file:**
```yaml
- name: Configure K3s to use insecure registry
  copy:
    dest: /etc/rancher/k3s/registries.yaml
    content: |
      mirrors:
        "{{ hostvars['master']['ansible_host'] }}:5000":
          endpoint:
            - "http://{{ hostvars['master']['ansible_host'] }}:5000"
      configs:
        "{{ hostvars['master']['ansible_host'] }}:5000":
          tls:
            insecure_skip_verify: true
```

**Why this is needed:**
- K3s uses containerd as container runtime
- containerd requires explicit configuration for insecure (HTTP) registries
- Without this, image pulls would fail with "x509: certificate signed by unknown authority"

**Service restart:**
```yaml
- name: Restart K3s on master
  systemd:
    name: k3s
    state: restarted
  when: inventory_hostname == 'master'

- name: Restart K3s agent on workers
  systemd:
    name: k3s-agent
    state: restarted
  when: inventory_hostname != 'master'
```

Conditional restart based on node role.

---

### Phase 7: Jenkins Deployment

**Targets:** jenkins_master group (master VM)

**Duration:** 2-3 minutes

**Purpose:** Deploy customized Jenkins with full configuration

**Tasks:**

```yaml
Tasks:
1. Create Jenkins directory structure
2. Copy Dockerfile.jenkins to master
3. Copy Jenkins configuration files:
   - config.yml (JCasC)
   - job_dsl.groovy
   - plugins.txt
   - entrypoint.sh
4. Copy base image Dockerfiles to master
5. Build whanos-jenkins custom image
6. Stop existing Jenkins container
7. Start Jenkins with environment variables
8. Wait for Jenkins to be ready
9. Display access information
```

**Technical Details:**

**Directory structure:**
```yaml
- name: Create Jenkins directories
  file:
    path: "{{ item }}"
    state: directory
  loop:
    - /root/jenkins
    - /root/jenkins/init.groovy.d
    - /root/whanos_images
    - /root/whanos_images/{{ lang }} (for each language)
```

**Build custom Jenkins image:**
```yaml
- name: Build Jenkins image
  docker_image:
    name: whanos-jenkins:vm
    build:
      path: /root/jenkins
      dockerfile: Dockerfile.jenkins
    source: build
    force_source: yes
```

Uses `Dockerfile.jenkins` from repository root.

**Environment variable injection:**
```yaml
- name: Start Jenkins container
  docker_container:
    name: whanos-jenkins
    image: whanos-jenkins:vm
    state: started
    restart_policy: unless-stopped
    ports:
      - "8080:8080"
      - "50000:50000"
    volumes:
      - jenkins_home:/var/jenkins_home
      - /var/run/docker.sock:/var/run/docker.sock
      - /root/whanos_images:/var/jenkins_home/whanos_images
    env:
      ADMIN_PASSWORD: "{{ lookup('env', 'ADMIN_PASSWORD') }}"
      GITHUB_USERNAME: "{{ lookup('env', 'GITHUB_USERNAME') }}"
      GITHUB_TOKEN: "{{ lookup('env', 'GITHUB_TOKEN') }}"
      REGISTRY_URL: "{{ lookup('env', 'REGISTRY_URL') }}"
      DOCKER_HUB_USERNAME: "{{ lookup('env', 'DOCKER_HUB_USERNAME') }}"
      DOCKER_HUB_TOKEN: "{{ lookup('env', 'DOCKER_HUB_TOKEN') }}"
```

**Critical volume mounts:**
- `jenkins_home` - Persistent Jenkins data
- `/var/run/docker.sock` - Docker socket for building images
- `/root/whanos_images` - Base image Dockerfiles

**Startup verification:**
```yaml
- name: Wait for Jenkins to be ready
  uri:
    url: "http://localhost:8080/login"
    status_code: 200
  register: jenkins_ready
  until: jenkins_ready.status == 200
  retries: 60
  delay: 5
```

Waits up to 5 minutes for Jenkins to fully start.

## 🔧 Variables and Configuration

### Playbook Variables

**Defined in playbook:**
```yaml
vars:
  k3s_version: "v1.33.5+k3s1"
  docker_compose_version: "v2.24.5"
  registry_port: 5000
  jenkins_port: 8080
  jenkins_jnlp_port: 50000
```

**Environment variables (from .env):**
```yaml
Environment variables accessed via lookup():
- ADMIN_PASSWORD
- GITHUB_USERNAME
- GITHUB_TOKEN
- REGISTRY_URL
- DOCKER_HUB_USERNAME
- DOCKER_HUB_TOKEN
```

**Host variables (from inventory):**
```yaml
- ansible_host (VM IP address)
- ansible_user (SSH user)
- ansible_ssh_pass (SSH password)
- ansible_python_interpreter (Python path)
```

### Dynamic Variables

**Computed during playbook execution:**

**K3s token:**
```yaml
k3s_token: "{{ k3s_token_file.content | b64decode | trim }}"
```

Retrieved from master, used by workers.

**Architecture detection:**
```yaml
arch_mapping:
  x86_64: amd64
  aarch64: arm64

architecture: "{{ arch_mapping[ansible_architecture] }}"
```

Ensures correct Docker packages for CPU architecture.

## 📝 Tasks Deep Dive

### Idempotency

All tasks are designed to be idempotent (safe to re-run):

**Example: Docker installation**
```yaml
- name: Install Docker
  apt:
    name: docker-ce
    state: present
```

Running multiple times:
- First run: Installs Docker
- Subsequent runs: Checks if installed, skips if present

**Example: K3s installation**
```yaml
- name: Install K3s
  shell: curl -sfL https://get.k3s.io | sh
  args:
    creates: /usr/local/bin/k3s
```

`creates` parameter:
- Ansible checks if file exists
- Only runs if file doesn't exist
- Safe to re-run playbook

### Error Handling

**Retry logic:**
```yaml
- name: Wait for node Ready
  shell: kubectl get nodes | grep Ready
  register: result
  until: result.rc == 0
  retries: 30
  delay: 5
```

Retries up to 30 times with 5 second delay (2.5 minutes total).

**Ignore errors:**
```yaml
- name: Clean old configs
  file:
    path: /etc/docker/old-config
    state: absent
  ignore_errors: yes
```

Continues even if file doesn't exist.

**Conditional execution:**
```yaml
- name: Restart K3s master
  systemd:
    name: k3s
    state: restarted
  when: inventory_hostname == 'master'
```

Only runs on specific host.

### Delegation

**Running tasks on different hosts:**
```yaml
- name: Get cluster info
  hosts: k3s_workers
  tasks:
    - name: Check nodes from master
      shell: kubectl get nodes
      delegate_to: master
```

Runs kubectl command on master while targeting workers in play.

## 🔍 Troubleshooting

### Common Issues

**Issue: APT lock errors**

**Symptoms:**
```
E: Could not get lock /var/lib/dpkg/lock-frontend
```

**Solution:**
Already handled in playbook:
```yaml
- name: Wait for APT lock
  shell: while fuser /var/lib/dpkg/lock-frontend >/dev/null 2>&1; do sleep 1; done
```

**Manual fix if needed:**
```bash
ssh root@VM_IP "killall -9 apt apt-get; rm -f /var/lib/dpkg/lock*"
```

---

**Issue: K3s workers not joining**

**Symptoms:**
```
kubectl get nodes  # Only shows master
```

**Debug:**
```yaml
# Check token was retrieved
- debug:
    var: k3s_token

# Check worker can reach master
- shell: curl -k https://{{ master_ip }}:6443
  delegate_to: worker1
```

**Common causes:**
- Firewall blocking port 6443
- Incorrect token
- Master not fully ready

---

**Issue: Registry not accessible from workers**

**Symptoms:**
```
Failed to pull image: connection refused
```

**Debug:**
```bash
# From worker node
curl http://MASTER_IP:5000/v2/_catalog

# Check containerd config
cat /etc/rancher/k3s/registries.yaml

# Check K3s agent logs
journalctl -u k3s-agent -n 100
```

**Fix:**
Ensure Phase 6 completed successfully and services restarted.

---

**Issue: Jenkins won't start**

**Symptoms:**
```
curl http://MASTER_IP:8080  # Connection refused
```

**Debug:**
```bash
# Check container status
docker ps -a | grep jenkins

# Check logs
docker logs whanos-jenkins

# Common issues:
# - Port 8080 already in use
# - Volume mount errors
# - Missing environment variables
```

**Fix:**
```bash
# Stop conflicting services
systemctl stop jenkins  # System Jenkins
systemctl disable jenkins

# Restart container
docker restart whanos-jenkins
```

### Debugging Commands

**Check playbook syntax:**
```bash
ansible-playbook deploy_whanos.yml --syntax-check
```

**Dry run (no changes):**
```bash
ansible-playbook deploy_whanos.yml --check
```

**Run specific phase:**
```bash
ansible-playbook deploy_whanos.yml --tags "phase3"
```

**Increase verbosity:**
```bash
ansible-playbook deploy_whanos.yml -vvv
```

**Step through tasks:**
```bash
ansible-playbook deploy_whanos.yml --step
```

### Log Locations

**Ansible logs:**
```bash
# Stdout during playbook run
# Set ANSIBLE_LOG_PATH for persistent logs:
export ANSIBLE_LOG_PATH=./ansible.log
```

**Docker logs:**
```bash
ssh root@MASTER_IP "docker logs whanos-jenkins"
ssh root@MASTER_IP "docker logs whanos-registry"
```

**K3s logs:**
```bash
ssh root@MASTER_IP "journalctl -u k3s -f"
ssh root@WORKER_IP "journalctl -u k3s-agent -f"
```

**System logs:**
```bash
ssh root@VM_IP "journalctl -xe"
```

## 📚 Best Practices

### Security

1. **Use SSH keys instead of passwords:**
```ini
# inventory.ini
master ansible_host=IP ansible_user=root
# No ansible_ssh_pass
```

2. **Encrypt sensitive data with ansible-vault:**
```bash
ansible-vault encrypt ansible/inventory.ini
ansible-playbook deploy_whanos.yml --ask-vault-pass
```

3. **Use least privilege:**
```yaml
# Create non-root user with sudo
- name: Create ansible user
  user:
    name: ansible
    groups: sudo
    shell: /bin/bash

# Use become for privilege escalation
become: yes
```

### Performance

1. **Parallel execution:**
```yaml
# In playbook
strategy: free  # Don't wait for all hosts

# Or in ansible.cfg
[defaults]
forks = 10  # Run on 10 hosts simultaneously
```

2. **Fact caching:**
```yaml
# ansible.cfg
[defaults]
gathering = smart
fact_caching = jsonfile
fact_caching_connection = /tmp/ansible_facts
fact_caching_timeout = 86400
```

3. **Pipelining:**
```yaml
# ansible.cfg
[ssh_connection]
pipelining = True
```

### Maintenance

1. **Version pinning:**
```yaml
# Always specify versions
k3s_version: "v1.33.5+k3s1"
docker_version: "5:27.4.1-1~ubuntu.22.04~jammy"
```

2. **Documentation:**
```yaml
# Add comments to complex tasks
- name: Complex task
  # This task does X because Y
  # Related to issue #123
  shell: ...
```

3. **Tags for selective execution:**
```yaml
- name: Install Docker
  tags: [docker, base]
  apt: ...

# Run only docker tasks:
# ansible-playbook deploy_whanos.yml --tags docker
```

## 📖 Related Documentation

- [User Guide](USER_GUIDE.md) - Using the deployed infrastructure
- [Deployment Guide](DEPLOYMENT.md) - Step-by-step deployment
- [Configuration Reference](CONFIGURATION.md) - All configuration options
- [Architecture Overview](ARCHITECTURE.md) - System design

---

This Ansible automation provides a production-ready, repeatable deployment process that can provision a complete Whanos infrastructure in under 15 minutes.
