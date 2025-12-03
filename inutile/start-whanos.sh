#!/bin/bash
set -e

echo "🚀 Starting Whanos Local Environment"
echo "===================================="
echo ""

# Vérifier si kind cluster existe
if ! kind get clusters 2>/dev/null | grep -q "^whanos$"; then
    echo "⚠️  Cluster kind 'whanos' non trouvé!"
    echo "   Création du cluster kind..."
    if [ -f "kind-config.yaml" ]; then
        kind create cluster --name whanos --config kind-config.yaml
    else
        kind create cluster --name whanos
    fi
    echo "✅ Cluster kind 'whanos' créé"
else
    echo "✓ Cluster kind 'whanos' détecté"
fi

# Arrêter les conteneurs existants
echo "🧹 Cleaning up previous containers..."
docker-compose down 2>/dev/null || true

# Supprimer l'ancien Jenkins local si présent
docker rm -f whanos-jenkins-local 2>/dev/null || true

# Build l'image Jenkins customisée
echo "🔨 Building custom Jenkins image..."
docker build -t whanos-jenkins:local -f Dockerfile.jenkins .

# Démarrer les services
echo "🚀 Starting services..."
docker-compose up -d

echo ""
echo "⏳ Waiting for Jenkins to start (this may take 1-2 minutes)..."
echo "   You can check logs with: docker-compose logs -f jenkins"
echo ""

# Attendre que Jenkins soit prêt
for i in {1..120}; do
    if curl -s http://localhost:8080/login > /dev/null 2>&1; then
        echo ""
        echo "✅ Jenkins is ready!"
        echo ""
        echo "================================================"
        echo "🌐 Jenkins URL: http://localhost:8080"
        echo "👤 Username: admin"
        echo "🔑 Password: ${ADMIN_PASSWORD:-admin123}"
        echo "================================================"
        echo ""
        echo "📦 Docker Registry: whanos-registry:5000 (localhost:5000)"
        echo ""
        echo "☸️  Kubernetes Cluster:"
        docker exec whanos-jenkins kubectl get nodes 2>/dev/null || echo "   (kubectl non configuré)"
        echo ""
        echo "✨ Jobs créés automatiquement:"
        echo "   - Whanos base images/"
        echo "     • whanos-befunge"
        echo "     • whanos-c"
        echo "     • whanos-java"
        echo "     • whanos-javascript"
        echo "     • whanos-python"
        echo "     • Build all base images"
        echo "   - Projects/ (vide au début)"
        echo "   - link-project (job graine)"
        echo ""
        echo "🎯 Pour lier un projet:"
        echo "   1. Aller sur http://localhost:8080/job/link-project/"
        echo "   2. Cliquer 'Build with Parameters'"
        echo "   3. Remplir DISPLAY_NAME et GIT_REPOSITORY"
        echo ""
        echo "📋 Commandes utiles:"
        echo "   docker-compose logs -f jenkins  # Voir les logs"
        echo "   docker-compose down            # Arrêter"
        echo "   docker-compose restart         # Redémarrer"
        echo ""
        exit 0
    fi
    sleep 1
done

echo "❌ Jenkins failed to start in time"
echo "📋 Check logs with: docker-compose logs jenkins"
exit 1
