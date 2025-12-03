// ===== CRÉATION DES DOSSIERS PRINCIPAUX =====

folder('Whanos base images') {
    displayName('Whanos base images')
    description('Images de base pour chaque langage supporté par Whanos')
}

folder('Projects') {
    displayName('Projects')
    description('Projets déployés via Whanos')
}



def languages = ['befunge', 'c', 'java', 'javascript', 'python']

// ===== JOBS DE BUILD DES IMAGES DE BASE =====

languages.each { lang ->
    freeStyleJob("Whanos base images/whanos-${lang}") {
        displayName("whanos-${lang}")
        description("Build de l'image de base whanos-${lang}")
        
        logRotator {
            numToKeep(10)
        }
        
        wrappers {
            timestamps()
        }
        
        steps {
            shell("""
                #!/bin/bash
                set -ex
                
                echo "Building whanos-${lang} base image..."
                cd /var/jenkins_home/whanos_images/${lang}
                
                # Debug: afficher toutes les variables d'environnement
                echo "=== Variables d'environnement ==="
                echo "REGISTRY_URL: \$REGISTRY_URL"
                echo "DOCKER_HUB_USERNAME: \$DOCKER_HUB_USERNAME"
                echo "DOCKER_HUB_TOKEN: [CONFIGURED]" # Ne pas afficher le token complet
                
                # Utiliser les variables d'environnement du conteneur Jenkins
                REGISTRY_URL=\${REGISTRY_URL:-localhost:5000}
                DOCKER_HUB_USERNAME=\${DOCKER_HUB_USERNAME:-}
                DOCKER_HUB_TOKEN=\${DOCKER_HUB_TOKEN:-}
                
                echo "Registry URL (after default): \$REGISTRY_URL"
                echo "Docker Hub Username (after default): \$DOCKER_HUB_USERNAME"
                
                # Build et push vers le registry local
                docker build -t \$REGISTRY_URL/whanos-${lang}:latest -f Dockerfile.base .
                docker push \$REGISTRY_URL/whanos-${lang}:latest
                
                # Push aussi vers Docker Hub si configuré
                if [ -n "\$DOCKER_HUB_USERNAME" ] && [ -n "\$DOCKER_HUB_TOKEN" ]; then
                    echo "Pushing to Docker Hub..."
                    echo "\$DOCKER_HUB_TOKEN" | docker login -u "\$DOCKER_HUB_USERNAME" --password-stdin
                    docker tag \$REGISTRY_URL/whanos-${lang}:latest \$DOCKER_HUB_USERNAME/whanos-${lang}:latest
                    docker push \$DOCKER_HUB_USERNAME/whanos-${lang}:latest
                    docker logout
                    echo "✅ Pushed to Docker Hub: \$DOCKER_HUB_USERNAME/whanos-${lang}:latest"
                else
                    echo "⚠️  Docker Hub credentials not configured, skipping Docker Hub push"
                fi
                
                echo "✅ Image whanos-${lang} built and pushed successfully"
            """.stripIndent())
        }
    }
}

// ===== JOB POUR BUILDER TOUTES LES IMAGES =====

freeStyleJob('Whanos base images/Build all base images') {
    displayName('Build all base images')
    description('Déclenche le build de toutes les images de base')
    
    logRotator {
        numToKeep(10)
    }
    
    wrappers {
        timestamps()
    }
    
    publishers {
        downstream('Whanos base images/whanos-befunge, Whanos base images/whanos-c, Whanos base images/whanos-java, Whanos base images/whanos-javascript, Whanos base images/whanos-python', 'SUCCESS')
    }
}

// ===== JOB LINK-PROJECT

freeStyleJob('link-project') {
    displayName('link-project')
    description('Crée un nouveau job de build pour un projet depuis un repository Git')
    
    parameters {
        stringParam('DISPLAY_NAME', '', 'Nom d affichage du projet')
        stringParam('GIT_REPOSITORY', '', 'URL du repository Git')
    }
    
    logRotator {
        numToKeep(10)
    }
    
    wrappers {
        timestamps()
    }
    
    steps {
        dsl {
            text('''
def projectDisplayName = binding.variables.DISPLAY_NAME
def gitRepo = binding.variables.GIT_REPOSITORY

if (!projectDisplayName || !gitRepo) {
    throw new Exception("DISPLAY_NAME et GIT_REPOSITORY sont requis!")
}

def jobName = projectDisplayName.replaceAll(/[^a-zA-Z0-9-_]/, '-').toLowerCase()

println "Création du job: Projects/${jobName}"

freeStyleJob("Projects/${jobName}") {
    displayName(projectDisplayName)
    description("Build automatique pour ${projectDisplayName} - Repository: ${gitRepo}")
    
    logRotator {
        numToKeep(10)
    }
    
    scm {
        git {
            remote {
                url(gitRepo)
                credentials('github-credentials')
            }
            branch('*/main')
        }
    }
    triggers {
        scm('* * * * *')  
    }
    wrappers {
        timestamps()
    }
    
    steps {
        shell(\'\'\'#!/bin/bash
set -ex

# Utiliser les variables d'environnement du conteneur Jenkins
REGISTRY_URL=${REGISTRY_URL:-localhost:5000}
DOCKER_HUB_USERNAME=${DOCKER_HUB_USERNAME:-}
DOCKER_HUB_TOKEN=${DOCKER_HUB_TOKEN:-}

echo "Détection du langage du projet..."

DETECTION_COUNT=0
LANGUAGE=""

# Détection C (Makefile avec gcc)
if [ -f "Makefile" ] && grep -q "gcc" Makefile 2>/dev/null; then
    DETECTION_COUNT=$((DETECTION_COUNT + 1))
    LANGUAGE="c"
    echo "✓ Critère C détecté (Makefile avec gcc)"
fi

# Détection Java (app/pom.xml)
if [ -f "app/pom.xml" ]; then
    DETECTION_COUNT=$((DETECTION_COUNT + 1))
    LANGUAGE="java"
    echo "✓ Critère Java détecté (app/pom.xml)"
fi

# Détection JavaScript (package.json)
if [ -f "package.json" ]; then
    DETECTION_COUNT=$((DETECTION_COUNT + 1))
    LANGUAGE="javascript"
    echo "✓ Critère JavaScript détecté (package.json)"
fi

# Détection Python (requirements.txt)
if [ -f "requirements.txt" ]; then
    DETECTION_COUNT=$((DETECTION_COUNT + 1))
    LANGUAGE="python"
    echo "✓ Critère Python détecté (requirements.txt)"
fi

# Détection Befunge (app/main.bf)
if [ -f "app/main.bf" ]; then
    DETECTION_COUNT=$((DETECTION_COUNT + 1))
    LANGUAGE="befunge"
    echo "✓ Critère Befunge détecté (app/main.bf)"
fi

# Vérification : un seul critère doit être satisfait
if [ $DETECTION_COUNT -eq 0 ]; then
    echo "❌ ERREUR: Aucun critère de détection Whanos satisfait"
    echo "Repository non compatible Whanos"
    echo ""
    echo "Critères de détection supportés:"
    echo "  - C: Makefile contenant \\'gcc\\'"
    echo "  - Java: Fichier app/pom.xml"
    echo "  - JavaScript: Fichier package.json"
    echo "  - Python: Fichier requirements.txt"
    echo "  - Befunge: Fichier app/main.bf"
    exit 1
elif [ $DETECTION_COUNT -gt 1 ]; then
    echo "❌ ERREUR: Plusieurs critères de détection satisfaits ($DETECTION_COUNT)"
    echo "Un repository Whanos-compatible ne peut correspondre qu\\'à UN SEUL langage"
    echo "Repository non compatible Whanos"
    exit 1
fi

echo "✅ Langage détecté: $LANGUAGE"

PROJECT_NAME="\'\'\' + jobName + \'\'\'"

IMAGE_TAG="${REGISTRY_URL}/${PROJECT_NAME}:${BUILD_NUMBER}"
IMAGE_LATEST="${REGISTRY_URL}/${PROJECT_NAME}:latest"

echo "Building project image..."
echo "Registry URL: ${REGISTRY_URL}"
echo "Docker Hub Username: ${DOCKER_HUB_USERNAME}"

# Préparation de l'image de base
echo "Pulling base image from registry..."
docker pull \${REGISTRY_URL}/whanos-${LANGUAGE}:latest

# Tag l'image de base pour qu'elle soit accessible sans le registry prefix
docker tag \${REGISTRY_URL}/whanos-${LANGUAGE}:latest whanos-${LANGUAGE}:latest

# Détection du Dockerfile à utiliser
if [ -f "Dockerfile" ]; then
    echo "Dockerfile personnalisé détecté, utilisation du Dockerfile du projet"
    DOCKERFILE_PATH="Dockerfile"
else
    echo "Pas de Dockerfile personnalisé, utilisation de Dockerfile.standalone"
    DOCKERFILE_PATH="/var/jenkins_home/whanos_images/${LANGUAGE}/Dockerfile.standalone"
fi

echo "Using Dockerfile: $DOCKERFILE_PATH"

docker build -t ${IMAGE_TAG} -t ${IMAGE_LATEST} \\
    --build-arg BASE_IMAGE=\${REGISTRY_URL}/whanos-${LANGUAGE}:latest \\
    -f ${DOCKERFILE_PATH} .

docker push ${IMAGE_TAG}
docker push ${IMAGE_LATEST}

echo "Image pushed: ${IMAGE_TAG}"

# Push aussi vers Docker Hub si configuré
if [ -n "\${DOCKER_HUB_USERNAME}" ] && [ -n "\${DOCKER_HUB_TOKEN}" ]; then
    echo "Pushing to Docker Hub..."
    echo "\${DOCKER_HUB_TOKEN}" | docker login -u "\${DOCKER_HUB_USERNAME}" --password-stdin
    
    DOCKER_HUB_TAG="\${DOCKER_HUB_USERNAME}/${PROJECT_NAME}:${BUILD_NUMBER}"
    DOCKER_HUB_LATEST="\${DOCKER_HUB_USERNAME}/${PROJECT_NAME}:latest"
    
    docker tag ${IMAGE_TAG} \${DOCKER_HUB_TAG}
    docker tag ${IMAGE_LATEST} \${DOCKER_HUB_LATEST}
    
    docker push \${DOCKER_HUB_TAG}
    docker push \${DOCKER_HUB_LATEST}
    
    docker logout
    echo "✅ Image also pushed to Docker Hub"
fi

# Déploiement Kubernetes (si whanos.yml ou whanos.yaml présent)
if [ -f "whanos.yml" ] || [ -f "whanos.yaml" ]; then
    WHANOS_FILE="whanos.yml"
    [ -f "whanos.yaml" ] && WHANOS_FILE="whanos.yaml"
    
    echo "📦 Déploiement Kubernetes détecté (${WHANOS_FILE})..."
    
    # Vérifier si cluster K8s accessible
    if kubectl cluster-info &>/dev/null; then
        echo "✓ Cluster Kubernetes accessible"
        
        helm upgrade --install ${PROJECT_NAME} /var/jenkins_home/helm/Whanos \\
            --set image.repository=\${REGISTRY_URL}/${PROJECT_NAME} \\
            --set image.tag=${BUILD_NUMBER} \\
            --set nameOverride=${PROJECT_NAME} \\
            --values ${WHANOS_FILE} \\
            --namespace default \\
            --create-namespace
        echo "✅ Déploiement Kubernetes réussi!"
    else
        echo "⚠️  Cluster Kubernetes non accessible"
        echo "   Le build de l'image est OK, mais pas de déploiement K8s"
        echo "   Pour activer K8s:"
        echo "   - Installer kind/minikube localement, ou"
        echo "   - Déployer sur VMs avec cluster K3s via Ansible"
    fi
else
    echo "ℹ️  Pas de whanos.yml/whanos.yaml, skip déploiement Kubernetes"
fi
\'\'\')
    }
}

println "Job Projects/${jobName} créé avec succès!"
            '''.stripIndent())
        }
    }
}

println "✅ Configuration Job DSL terminée!"
