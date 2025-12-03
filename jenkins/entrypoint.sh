#!/bin/bash
set -e

echo "🚀 Whanos Jenkins Entrypoint"

if docker ps --format '{{.Names}}' | grep -q "whanos-control-plane" 2>/dev/null; then
    echo "📦 Cluster kind détecté, configuration kubeconfig..."
    
    CONTROL_PLANE="whanos-control-plane"
    mkdir -p /var/jenkins_home/.kube
    
    if [ -f /host-kube/config ]; then

        cat /host-kube/config | sed "s|https://127.0.0.1:[0-9]*|https://${CONTROL_PLANE}:6443|g" > /var/jenkins_home/.kube/config
        chmod 600 /var/jenkins_home/.kube/config
        chown jenkins:jenkins /var/jenkins_home/.kube/config
        echo "✅ Kubeconfig configuré pour kind (${CONTROL_PLANE}:6443)"
    else
        echo "⚠️  Kubeconfig hôte non trouvé, skip configuration K8s"
    fi
else
    echo "ℹ️  Pas de cluster kind détecté, skip configuration kubeconfig"
fi

echo "🎯 Démarrage de Jenkins..."
exec /usr/bin/tini -- /usr/local/bin/jenkins.sh "$@"
