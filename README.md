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

1. **Inscription Administrateur (`POST /api/auth/register`)** :
   - Inscription libre d'un compte **ADMIN**.
   - Nécessite `password` et `confirmPassword` identiques.

2. **Création d'un Manager (`POST /api/users/managers`)** :
   - **Seul un ADMIN connecté** peut créer un compte Manager.
   - Les Managers ne peuvent pas s'inscrire eux-mêmes via `/auth/register`.

3. **Gestion & Désactivation (`PATCH /api/users/{id}/deactivate` & `activate`)** :
   - Un Admin peut **désactiver** ou **réactiver** un Manager/Utilisateur.
   - Dès qu'un compte est désactivé (`actif = false`), tous ses tokens sont révoqués et toute tentative de connexion échoue avec le message :
     > `"Votre compte a été désactivé. Veuillez contacter un administrateur."`

---

## 🛠️ Endpoints API

### Authentification (`/api/auth`)

| Méthode | Route | Rôle requis | Description |
|---------|-------|-------------|-------------|
| `POST` | `/api/auth/register` | Public | Inscription d'un Administrateur |
| `POST` | `/api/auth/login` | Public | Connexion Admin ou Manager |
| `POST` | `/api/auth/refresh-token` | Public | Renouveler l'access token |
| `POST` | `/api/auth/logout` | Authentifié | Déconnexion (révocation tokens) |

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

# 3. Lancer l'application
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