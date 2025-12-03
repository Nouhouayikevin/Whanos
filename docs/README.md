# Whanos Documentation

Welcome to the Whanos infrastructure documentation. This directory contains comprehensive guides to help you understand, deploy, and maintain your Whanos infrastructure.

## 📚 Documentation Index

### Getting Started
- **[User Guide](USER_GUIDE.md)** - Quick start guide for deploying and using Whanos
- **[Deployment Guide](DEPLOYMENT.md)** - Step-by-step deployment instructions

### Technical Documentation
- **[Architecture Overview](ARCHITECTURE.md)** - System architecture and component interactions
- **[Ansible Automation](ANSIBLE.md)** - Detailed Ansible playbook documentation
- **[Jenkins Pipeline](JENKINS.md)** - Jenkins configuration and job DSL details
- **[Helm Charts](HELM.md)** - Kubernetes deployment with Helm
- **[Configuration Reference](CONFIGURATION.md)** - All configuration options and environment variables

### Additional Resources
- **[Troubleshooting](TROUBLESHOOTING.md)** - Common issues and solutions
- **[Security](SECURITY.md)** - Security best practices and considerations

## 🚀 Quick Links

- **First time deploying Whanos?** → Start with the [User Guide](USER_GUIDE.md)
- **Want to understand how it works?** → Read the [Architecture Overview](ARCHITECTURE.md)
- **Having issues?** → Check the [Troubleshooting Guide](TROUBLESHOOTING.md)

## 📋 Project Overview

**Whanos** is an automated deployment infrastructure that allows developers to deploy applications into a Kubernetes cluster with a simple Git push. It supports multiple programming languages and automatically:

1. Detects the application technology
2. Builds Docker images
3. Pushes to registries (local and Docker Hub)
4. Deploys to Kubernetes cluster (if configured)

### Supported Languages

- C
- Java
- JavaScript
- Python
- Befunge

## 🛠️ Technology Stack

- **Ansible** - Infrastructure automation
- **Jenkins** - CI/CD orchestration
- **Docker** - Containerization
- **Kubernetes (K3s)** - Container orchestration
- **Helm** - Kubernetes package manager
- **Docker Registry** - Image storage

## 📖 Documentation Structure

Each documentation file follows a consistent structure:
- **Overview** - High-level summary
- **Technical Details** - In-depth technical information
- **Configuration** - Setup and configuration options
- **Examples** - Practical usage examples
- **References** - Additional resources

## 🤝 Contributing

When updating documentation:
1. Keep it clear and concise
2. Include practical examples
3. Update related documentation files
4. Test all commands and configurations

## 📝 License

This project is part of the Epitech DevOps curriculum.

---

**Need help?** Start with the [User Guide](USER_GUIDE.md) or check the [Troubleshooting Guide](TROUBLESHOOTING.md).
