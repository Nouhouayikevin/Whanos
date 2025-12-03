# Configuration des Credentials Whanos

## Variables d'Environnement

Créez un fichier `.env` à la racine du projet à partir du fichier d'exemple :

```bash
cp .env.example .env
```

Éditez le fichier `.env` :

```bash
# Mot de passe de l'administrateur Jenkins (REQUIS)
# Utilisateur: admin
# Ce mot de passe sera utilisé pour créer l'utilisateur admin dans Jenkins
ADMIN_PASSWORD=votre_mot_de_passe_securise

# Credentials GitHub (OPTIONNEL - pour dépôts privés)
# Créez un Personal Access Token sur GitHub avec les permissions 'repo'
GITHUB_USERNAME=votre_username
GITHUB_TOKEN=ghp_votre_personal_access_token
```

## Déploiement

### Méthode 1 : Avec le script helper (recommandé)

```bash
./deploy.sh
```

Le script va :
1. Vérifier que `.env` existe
2. Vérifier qu'Ansible est installé
3. Charger les variables d'environnement
4. Afficher la configuration
5. Demander confirmation
6. Lancer le déploiement

### Méthode 2 : Manuelle

Chargez les variables avant de lancer Ansible :

```bash
source .env
export ADMIN_PASSWORD
export GITHUB_USERNAME
export GITHUB_TOKEN

ansible-playbook -i ansible/inventory.ini ansible/deploy_whanos.yml
```

## Connexion à Jenkins

Après le déploiement, connectez-vous à Jenkins :

- **URL**: `http://YOUR_MASTER_IP:8080`
- **Utilisateur**: `admin`
- **Mot de passe**: celui défini dans `ADMIN_PASSWORD`

## Configuration des Credentials GitHub dans Jenkins

Si vous avez configuré `GITHUB_USERNAME` et `GITHUB_TOKEN`, un credential avec l'ID `github-credentials` sera automatiquement créé dans Jenkins.

Pour utiliser ce credential :

1. Lancez le job `link-project`
2. Dans le paramètre `GIT_CREDENTIALS_ID`, laissez `github-credentials`
3. Le job pourra alors accéder à vos dépôts privés

## Sécurité

### ⚠️ Bonnes Pratiques

- **Ne commitez JAMAIS** le fichier `.env` sur Git
- Le fichier `.gitignore` est configuré pour l'exclure automatiquement
- Utilisez des tokens GitHub avec les **permissions minimales** nécessaires (scope: `repo`)
- Changez le mot de passe par défaut `admin` pour un mot de passe sécurisé
- Révocation : Si un token est compromis, révoquez-le immédiatement sur GitHub

### Ansible Vault (Optionnel - pour plus de sécurité)

Pour sécuriser davantage vos credentials Ansible :

```bash
# Chiffrer l'inventaire
ansible-vault encrypt ansible/inventory.ini

# Déployer avec le vault
ansible-playbook -i ansible/inventory.ini ansible/deploy_whanos.yml --ask-vault-pass
```

## Création de Token GitHub

1. Allez sur GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Cliquez sur "Generate new token (classic)"
3. Donnez un nom au token (ex: "Whanos CI/CD")
4. Sélectionnez les scopes :
   - ✅ `repo` (accès complet aux dépôts privés)
5. Cliquez sur "Generate token"
6. **Copiez le token immédiatement** (il ne sera plus affiché)
7. Collez-le dans le fichier `.env` dans la variable `GITHUB_TOKEN`

## Troubleshooting

### Le mot de passe admin ne fonctionne pas

Vérifiez que :
1. Le fichier `.env` est bien sourcé : `source .env`
2. La variable est exportée : `echo $ADMIN_PASSWORD`
3. Jenkins a bien redémarré après la configuration

### Les dépôts privés ne sont pas accessibles

Vérifiez que :
1. `GITHUB_USERNAME` et `GITHUB_TOKEN` sont définis dans `.env`
2. Le token a les bonnes permissions (`repo`)
3. Le token n'est pas expiré
4. Le credential `github-credentials` existe dans Jenkins (Manage Jenkins → Credentials)
