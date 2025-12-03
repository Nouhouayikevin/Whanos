#!/bin/bash
set -e

# Couleurs pour l'affichage
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${GREEN}"
echo "██╗    ██╗██╗  ██╗ █████╗ ███╗   ██╗ ██████╗ ███████╗"
echo "██║    ██║██║  ██║██╔══██╗████╗  ██║██╔═══██╗██╔════╝"
echo "██║ █╗ ██║███████║███████║██╔██╗ ██║██║   ██║███████╗"
echo "██║███╗██║██╔══██║██╔══██║██║╚██╗██║██║   ██║╚════██║"
echo "╚███╔███╔╝██║  ██║██║  ██║██║ ╚████║╚██████╔╝███████║"
echo " ╚══╝╚══╝ ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═══╝ ╚═════╝ ╚══════╝"
echo -e "${NC}"
echo "Automatically Deploy (Nearly) Anything"
echo ""

# Charger les variables du fichier .env si présent
if [ -f .env ]; then
    echo -e "${YELLOW}📝 Chargement de .env...${NC}"
    set -a
    source .env
    set +a
else
    echo -e "${YELLOW}⚠️  Fichier .env non trouvé, utilisation des valeurs par défaut${NC}"
    ADMIN_PASSWORD="admin123"
fi

# Vérifier si ansible est installé
if ! command -v ansible-playbook &> /dev/null; then
    echo -e "${RED}❌ Ansible n'est pas installé!${NC}"
    echo ""
    echo "Installez Ansible:"
    echo "  sudo apt update && sudo apt install -y ansible sshpass"
    echo ""
    exit 1
fi

# Vérifier si l'inventaire existe
if [ ! -f ansible/inventory.ini ]; then
    echo -e "${RED}❌ Fichier ansible/inventory.ini introuvable!${NC}"
    echo ""
    echo "Le fichier d'inventaire est nécessaire pour déployer sur les VMs"
    exit 1
fi

# Vérifier si le playbook existe
if [ ! -f ansible/deploy_whanos.yml ]; then
    echo -e "${RED}❌ Fichier ansible/deploy_whanos.yml introuvable!${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Configuration chargée${NC}"
echo "   Admin password: ${ADMIN_PASSWORD}"
if [ -n "$GITHUB_USERNAME" ]; then
    echo "   GitHub username: ${GITHUB_USERNAME}"
fi

echo ""
echo -e "${BLUE}📋 Configuration détectée:${NC}"
echo ""

# Lire les IPs depuis l'inventaire
JENKINS_IP=$(grep -A 1 '\[jenkins\]' ansible/inventory.ini | grep ansible_host | awk -F= '{print $2}' | tr -d ' ')
MASTER_IP=$(grep -A 1 '\[k3s_master\]' ansible/inventory.ini | grep ansible_host | awk -F= '{print $2}' | tr -d ' ')
WORKERS_IPS=$(grep -A 10 '\[k3s_workers\]' ansible/inventory.ini | grep ansible_host | awk -F= '{print $2}' | tr -d ' ')

echo "  🏗️  Jenkins + Registry: ${JENKINS_IP}"
echo "  ☸️   K3s Master: ${MASTER_IP}"
echo "  👷  K3s Workers:"
for worker_ip in $WORKERS_IPS; do
    echo "      - ${worker_ip}"
done
echo ""

# Demander confirmation
read -p "🚀 Lancer le déploiement de Whanos? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}Déploiement annulé.${NC}"
    exit 0
fi

echo ""
echo -e "${GREEN}🚀 Démarrage du déploiement Ansible...${NC}"
echo ""

# Lancer Ansible avec le playbook de déploiement
ansible-playbook -i ansible/inventory.ini ansible/deploy_whanos.yml

echo ""
echo -e "${GREEN}✅ DÉPLOIEMENT WHANOS TERMINÉ!${NC}"
echo ""
echo -e "${BLUE}📝 Informations d'accès:${NC}"
echo ""
echo "  🌐 Registry Docker:"
echo "     http://${JENKINS_IP}:5000"
echo ""
echo "  🏗️  Jenkins:"
echo "     URL: http://${JENKINS_IP}:8080"
echo "     Login: admin"
echo "     Password: admin123"
echo ""
echo "  ☸️   Kubernetes (K3s):"
echo "     Master: ${MASTER_IP}"
echo "     Nodes: 3 (1 master + 2 workers)"
echo "     kubectl: ssh root@${MASTER_IP} kubectl get nodes"
echo ""
echo -e "${YELLOW}📌 Prochaines étapes:${NC}"
echo "  1. Accéder à Jenkins: http://${JENKINS_IP}:8080"
echo "  2. Lancer 'Build all base images' pour construire les images de base"
echo "  3. Utiliser 'link-project' pour déployer vos projets"
echo ""
