# Coprogo – Gestion collaborative de groupes et partage de dépenses

**Coprogo** est une application pour gérer collaborativement les dépenses, revenus et remboursements au sein d'un groupe. Le projet suit une architecture **Domain-Driven Design** avec un **backend Kotlin/Micronaut** et un **frontend Angular**, orchestrés par un **monorepo Gradle**.

## Architecture générale

```
coprogo-monorepo/
├── backend/                    # Backend Kotlin/Micronaut (includedBuild)
│   ├── src/main/kotlin        # Domaine, application, infrastructure
│   ├── src/test/kotlin        # Tests de domaine, application, infra
│   └── build.gradle.kts
├── frontend/                   # Frontend Angular (npm)
│   ├── src/
│   └── package.json
├── scripts/                    # Scripts d'aide (ex: dev-stack.sh)
├── build.gradle.kts           # Orchestration Gradle racine
├── settings.gradle.kts        # Configuration includedBuild
└── README.md                  # Cette documentation
```

## Quickstart

### Prérequis

- **Java** 17+
- **Node.js** 24.14.0 (automatiquement téléchargé et provisionné par Gradle si absent)
- **Gradle** 9.4.1 (Gradle Wrapper inclus)
- **Docker** et **docker-compose** (pour PostgreSQL local et services tiers)

### Installation initiale

```bash
# Cloner et se placer à la racine du monorepo
git clone https://github.com/DGCarramona/coprogo.git
cd coprogo

# Bootstrap : installer les dépendances frontend et résoudre le backend
./gradlew bootstrap
```

Cela installe :
- Les dépendances **npm** du frontend via Node.js provisionné
- Les dépendances **Gradle** du backend

### Lancer l'environnement local complet

```bash
# Démarrer backend et frontend ensemble (développement)
./gradlew dev
```

Cela lance :
- Backend sur `http://localhost:8080` (Micronaut)
- Frontend sur `http://localhost:4200` (Angular dev server)

Le script `scripts/dev-stack.sh` orchestrate ces deux processus et les gère jusqu'à arrêt (Ctrl+C).

### Alternative : Lancer front et back séparément

**Terminal 1 – Backend :**
```bash
./gradlew backendRun
```
Le backend démarre sur `http://localhost:8080`.

**Terminal 2 – Frontend :**
```bash
./gradlew frontendServe
```
Le frontend démarre sur `http://localhost:4200`.

## Commandes principales

### 🚀 Exécution

| Commande | Description |
|----------|-------------|
| `./gradlew dev` | Démarrer backend et frontend ensemble |
| `./gradlew backendRun` | Démarrer le backend seul |
| `./gradlew frontendServe` | Démarrer le frontend seul (dev server) |
| `./gradlew bootstrap` | Installer les dépendances (setup initial) |

### 🔍 Vérifications et tests

| Commande | Description |
|----------|-------------|
| `./gradlew checkAll` | Exécuter tous les checks : backend + frontend |
| `./gradlew backendTest` | Exécuter les tests backend |
| `./gradlew backendCheck` | Backend checks (tests + linting + compilation) |
| `./gradlew frontendTest` | Tests frontend (Vitest, watch=false) |
| `./gradlew frontendLint` | Linter frontend (ESLint) |
| `./gradlew frontendFormatCheck` | Vérifier le formatage (Prettier) |
| `./gradlew frontendBuild` | Build production frontend |
| `./gradlew test` | Alias : exécuter les tests backend depuis la racine |

### 🔨 Build

| Commande | Description |
|----------|-------------|
| `./gradlew buildAll` | Compiler frontend et backend |
| `./gradlew backendBuild` | Compiler le backend JAR |
| `./gradlew frontendBuild` | Build production du frontend |
| `./gradlew backendNativeCompile` | Compiler le backend en image native GraalVM |
| `./gradlew cleanAll` | Nettoyer les outputs (root, frontend, backend) |

### 🎨 Formatage

| Commande | Description |
|----------|-------------|
| `./gradlew frontendLint` | Lint et auto-correct ESLint (si possible) |
| `./gradlew ktlintFormat` | Appliquer le formatage Kotlin (backend) |
| `./gradlew ktlintCheck` | Vérifier le formatage Kotlin |

### 🔗 API

| Commande | Description |
|----------|-------------|
| `./gradlew frontendGenerateApi` | Régénérer le client OpenAPI frontend |

**Note :** Le backend doit tourner sur `http://localhost:8080` pour générer le client (l'endpoint `/openapi.yml` doit être accessible).

## Configuration locale

### Backend – Google ID token

Le backend valide les Google ID tokens. Pour tester localement :

1. Créer `coprogo/src/main/resources/application-local.properties` :
   ```properties
   coprogo.security.google-id-token.audiences=your-dev-client-id.apps.googleusercontent.com
   ```
   (Remplacer par votre client ID de développement Google)

2. Ou utiliser plusieurs audiences (comma-separated) :
   ```properties
   coprogo.security.google-id-token.audiences=client1.apps.googleusercontent.com,client2.apps.googleusercontent.com
   ```

Voir `coprogo/README.md` pour plus de détails sur les conventions de test backend.

## Workflow de développement typique

### Avant de commencer une branche

```bash
# S'assurer que tout est à jour
./gradlew checkAll
./gradlew buildAll
```

### Pendant le développement

1. **Si backend** : modifiez `coprogo/src/main/kotlin/...`
   - Tests : `./gradlew backendTest`
   - Linting : `./gradlew ktlintCheck`
   - Dev server : `./gradlew backendRun`

2. **Si frontend** : modifiez `frontend/src/...`
   - Tests : `./gradlew frontendTest`
   - Linting : `./gradlew frontendLint`
   - Dev server : `./gradlew frontendServe`

3. **Si vous touchez à l'API (backend)** :
   - Régénérez le client OpenAPI frontend :
     ```bash
     ./gradlew frontendGenerateApi
     ```

### Avant de pusher / créer une PR

```bash
# Exécuter tous les checks
./gradlew checkAll

# Nettoyer et rebuild (optionnel mais recommandé)
./gradlew cleanAll buildAll
```

Si tout passe (✅), vous êtes prêt à pusher et créer une PR.

## Structure du monorepo

### Backend (`coprogo/`)

Architecture **Domain-Driven Design** :

```
coprogo/src/main/
├── kotlin/tech/justdev/coprogo/
│   ├── domain/           # Domaine métier (indépendant du framework)
│   ├── application/      # Cas d'usage, DTOs, services application
│   ├── infra/           # Adaptateurs R2DBC, Flyway, config Micronaut
│   └── http/            # Contrôleurs REST, présentation
└── resources/
    ├── application.properties          # Config par défaut
    ├── application-runtime.properties  # Environnement runtime
    ├── application-local.properties    # Surcharges locales (gitignored)
    └── db/migration/                   # Migrations Flyway
```

Tests :
```
coprogo/src/test/
├── kotlin/tech/justdev/coprogo/
│   ├── domain/
│   ├── application/
│   ├── infra/
│   └── http/
└── kotlin/tech/justdev/testsupport/   # Support de test partagé
```

**Conventions de test** :
- Utilisez `@PostgresMicronautTest` pour tests avec BD réelle
- Utilisez `@NoDbMicronautTest` pour tests sans BD
- Exécutez `./gradlew test` avant commit

Voir `coprogo/README.md` pour la documentation détaillée des tests.

### Frontend (`frontend/`)

Architecture **composant Angular** :

```
frontend/src/
├── app/
│   ├── core/              # Services globaux (auth, API, storage)
│   ├── shared/            # Composants et utilitaires partagés
│   ├── features/          # Modules par feature (dashboard, expenses, etc.)
│   └── app.component.ts   # Shell de l'application
├── assets/                # Ressources statiques
├── styles/                # Styles globaux
└── main.ts                # Point d'entrée
```

Package scripts :
- `npm run start` : dev server Angular (ctrl+c pour arrêter)
- `npm run build` : build production
- `npm run lint` : ESLint
- `npm run test` : Vitest
- `npm run format:check` : Prettier
- `npm run generate:api` : Générer le client OpenAPI

Voir `frontend/README.md` pour la documentation détaillée du frontend.

## Intégration continue (CI)

Le monorepo exécute les checks via GitHub Actions au sein de chaque PR :

```yaml
# .github/workflows/check.yml exécute :
- ./gradlew checkAll (backend tests, linting, build ; frontend linting, tests, build)
```

Avant de pusher, la même commande localement vous garantit que la CI passera.

## Dépannage courant

### `./gradlew bootstrap` échoue

**Symptôme :** Erreur lors du `npmInstall` ou de la résolution des dépendances backend.

**Solution :**
```bash
# Nettoyer et réessayer
./gradlew cleanAll
./gradlew bootstrap
```

### Backend ne démarre pas

**Symptôme :** `./gradlew backendRun` échoue avec erreur de connexion BD.

**Solution :**
1. Vérifier que PostgreSQL est accessible (voir `application-runtime.properties`)
2. Créer `application-local.properties` et surcharger les credentials si besoin
3. Vérifier les logs : `./gradlew backendRun --info`

### Frontend ne démarre pas

**Symptôme :** Erreur lors de `./gradlew frontendServe` ou `npm start`.

**Solution :**
```bash
./gradlew frontendInstall
./gradlew frontendServe
```

### API client OpenAPI ne génère pas

**Symptôme :** `./gradlew frontendGenerateApi` échoue.

**Solution :**
1. Vérifier que le backend tourne sur `http://localhost:8080`
2. Vérifier que `/openapi.yml` est accessible
3. Vérifier les logs : `./gradlew frontendGenerateApi --info`

## Resources

- **Backend** : Voir `coprogo/README.md`
- **Frontend** : Voir `frontend/README.md`
- **Backlog** : Voir `BACKLOG.md`
- **Gradle Wrapper** : `gradle/wrapper/gradle-wrapper.properties`

## Liens externes

- [Micronaut 5.0.2 Documentation](https://docs.micronaut.io/5.0.2/guide/index.html)
- [Angular CLI](https://angular.dev/tools/cli)
- [Gradle Build Tool](https://gradle.org/)
- [Docker Compose](https://docs.docker.com/compose/)
