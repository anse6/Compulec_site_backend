# 🚀 Compulec Backend API - Documentation

API REST construite avec **Spring Boot 4**, **Spring Security**, **JWT** et **MySQL**.

---

## 📋 Champs de l'Entité `User`

| Champ | Type | Validation / Règle |
|-------|------|---------------------|
| `nom` | String | Requis (2 - 100 caractères) |
| `prenom` | String | Requis (2 - 100 caractères) |
| `email` | String | Requis, unique, format email |
| `phone` | String | Requis, **exactement 9 chiffres** (`^[0-9]{9}$`) |
| `dateNaissance` | LocalDate | Requis (format `YYYY-MM-DD`) |
| `fonction` | String | Requis (ex: "Administrateur Système", "Manager Ventes") |
| `password` | String | Requis (min. 8 caractères) |
| `role` | Enum | `ADMIN`, `MANAGER`, `USER` |
| `actif` | Boolean | `true` par défaut. Si `false`, **connexion impossible**. |

---

## 🔐 Règles de Gestion des Comptes

1. **Premier administrateur** : il n'y a plus d'inscription publique. Au démarrage, si aucun ADMIN
   n'existe, il est créé à partir de `app.bootstrap.admin.email` / `app.bootstrap.admin.password`
   dans `config/secrets.properties` (supprimez ensuite le mot de passe du fichier).

2. **Invitation d'un Manager (`POST /api/users/managers`)** — réservé à un ADMIN :
   - Le compte est créé **inactif, sans mot de passe connu**.
   - Le manager reçoit par e-mail un **lien à usage unique** (24 h).
   - L'admin reçoit un **code à 6 chiffres affiché une seule fois**, à transmettre de vive voix.
   - Le manager active son compte avec le lien **et** le code (5 essais max), et choisit lui-même son mot de passe.
   - `POST /api/users/{id}/invitation` renvoie un nouveau lien et un nouveau code.

3. **Gestion & Désactivation (`PATCH /api/users/{id}/deactivate` & `activate`)** :
   - Un Admin peut **désactiver** ou **réactiver** un Manager/Utilisateur.
   - Dès qu'un compte est désactivé (`actif = false`), tous ses tokens sont révoqués et toute tentative de connexion échoue avec le message :
     > `"Votre compte a été désactivé. Veuillez contacter un administrateur."`

4. **Double authentification obligatoire (TOTP)** pour tous les comptes :
   - À la première connexion, l'utilisateur scanne un QR code (Google Authenticator, Microsoft Authenticator…).
   - 10 codes de secours à usage unique sont donnés à l'activation.
   - Téléphone perdu : un ADMIN utilise `PATCH /api/users/{id}/mfa/reset`.
   - Le secret 2FA est chiffré en base avec `app.security.encryption-key` (ne jamais changer cette clé).

5. **Sessions** : access token de 15 min en mémoire côté navigateur ; refresh token rotatif dans un cookie
   `HttpOnly; SameSite=Strict` (`app.cookie.secure=true` en HTTPS). Ouvrez le site avec le **même hôte**
   que `VITE_API_URL` (ex. tout en `192.168.1.193`, ou tout en `localhost`), sinon le cookie n'est pas envoyé.

6. **Limitation de débit** par IP (429) sur la connexion, la 2FA, les liens et les formulaires publics.

7. **Chat du site**
   - **Assistant IA** (Claude, modèle `claude-opus-5-5`) : répond à partir de
     `src/main/resources/chatbot/knowledge.md` (modifiable à la main), dans la langue du visiteur,
     et propose un conseiller quand c'est utile. Clé : `anthropic.api-key` dans `config/secrets.properties`.
     Sans clé, réponses par mots-clés. Plafond : `app.ai.max-replies-per-session` réponses IA par conversation.
   - **Temps réel** (Server-Sent Events) : `GET /api/chat/stream/{session}` (visiteur) et
     `GET /api/chat/admin/stream` (équipe, avec son et notifications du bureau).
   - **Maintenance automatique** (`app.chat.*`) : relance de l'équipe après 15 min sans prise en charge,
     fermeture des conversations inactives (24 h avec le bot, 48 h avec l'équipe), transcription envoyée
     au visiteur, demande jamais traitée copiée dans « Messages », purge après 365 jours.

---

## 🛠️ Endpoints API

### Authentification (`/api/auth`)

| Méthode | Route | Rôle requis | Description |
|---------|-------|-------------|-------------|
| `POST` | `/api/auth/login` | Public | Étape 1 : mot de passe → `mfa_token` (verrouillage 15 min après 5 échecs) |
| `POST` | `/api/auth/mfa/verify` | Public (mfa_token) | Étape 2 : code de l'application ou code de secours → session |
| `POST` | `/api/auth/mfa/setup` | Public (mfa_token) | Première connexion : QR code de la 2FA |
| `POST` | `/api/auth/mfa/setup/confirm` | Public (mfa_token) | Active la 2FA, ouvre la session, renvoie 10 codes de secours |
| `POST` | `/api/auth/invitations/preview` | Public | Vérifier un lien d'invitation |
| `POST` | `/api/auth/invitations/accept` | Public | Activer son compte (lien + code + mot de passe) |
| `POST` | `/api/auth/forgot-password` | Public | Recevoir un lien de réinitialisation (30 min) |
| `POST` | `/api/auth/reset-password` | Public | Nouveau mot de passe via le lien |
| `POST` | `/api/auth/refresh-token` | Cookie HttpOnly | Nouvel access token (rotation du refresh token) |
| `POST` | `/api/auth/logout` | Cookie HttpOnly | Déconnexion (révocation + suppression du cookie) |

### Gestion des Utilisateurs & Managers (`/api/users`)

| Méthode | Route | Rôle requis | Description |
|---------|-------|-------------|-------------|
| `GET` | `/api/users/me` | Tous | Profil de l'utilisateur connecté |
| `POST` | `/api/users/managers` | `ADMIN` | **Créer un compte Manager** |
| `GET` | `/api/users/managers` | `ADMIN` | Obtenir la liste des Managers |
| `GET` | `/api/users` | `ADMIN` | Liste de tous les utilisateurs |
| `GET` | `/api/users/{id}` | `ADMIN` | Obtenir un utilisateur par son ID |
| `PATCH` | `/api/users/{id}/activate` | `ADMIN` | **Activer** un compte |
| `PATCH` | `/api/users/{id}/deactivate` | `ADMIN` | **Désactiver** un compte |
| `DELETE` | `/api/users/{id}` | `ADMIN` | Supprimer définitivement un compte |

---

## 🚀 Démarrage du Projet

```bash
# 1. Démarrer MySQL via XAMPP
sudo /opt/lampp/lampp startmysql

# 2. Se rendre dans le dossier
cd ~/Bureau/compulec/compulec_site/compulec_backend

# 3. Vérifier que config/secrets.properties existe (voir config/secrets.properties.example)

# 4. Lancer l'application
./mvnw spring-boot:run
```

- **Swagger UI** : [http://localhost:8080/api/swagger-ui/index.html](http://localhost:8080/api/swagger-ui/index.html)


http://localhost:8080/api/swagger-ui/index.html
http://localhost/phpmyadmin
# Démarrer tout XAMPP (Apache + MySQL)
sudo /opt/lampp/lampp start

# OU démarrer SEULEMENT le service MySQL de XAMPP
sudo /opt/lampp/lampp startmysql

# 1. Allez dans le dossier de votre projet backend
cd ~/Bureau/compulec/compulec_backend

# 2. Lancez le serveur Spring Boot
./mvnw spring-boot:run