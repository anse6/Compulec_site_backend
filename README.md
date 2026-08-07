# 🚀 Compulec Backend API

API REST construite avec **Spring Boot 4**, **Spring Security**, **JWT** et **MySQL**.

## 📋 Prérequis

- Java 21+
- MySQL 8.0+
- Maven (inclus via `mvnw`)

## ⚙️ Configuration

### 1. Créer la base de données MySQL

```sql
CREATE DATABASE compulec_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'compulec_user'@'localhost' IDENTIFIED BY 'votre_mot_de_passe';
GRANT ALL PRIVILEGES ON compulec_db.* TO 'compulec_user'@'localhost';
FLUSH PRIVILEGES;
```

### 2. Configurer `application.properties`

Modifier les paramètres dans `src/main/resources/application.properties` :

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/compulec_db?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC
spring.datasource.username=root          # Votre username MySQL
spring.datasource.password=root          # Votre mot de passe MySQL
```

### 3. Lancer l'application

```bash
# Sur Linux/Mac
./mvnw spring-boot:run

# Sur Windows
mvnw.cmd spring-boot:run
```

L'API est accessible sur : `http://localhost:8080/api`

## 📚 Documentation API (Swagger)

Une fois l'application lancée, accédez à :
- **Swagger UI** : http://localhost:8080/api/swagger-ui.html
- **API Docs JSON** : http://localhost:8080/api/api-docs

## 🔐 Authentification

L'API utilise JWT (JSON Web Token) pour l'authentification.

### Endpoints Auth

| Méthode | URL | Description |
|---------|-----|-------------|
| `POST` | `/api/auth/register` | Créer un compte |
| `POST` | `/api/auth/login` | Se connecter |
| `POST` | `/api/auth/refresh-token` | Renouveler le token |
| `POST` | `/api/auth/logout` | Se déconnecter |

### Exemple d'inscription

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "nom": "Dupont",
    "prenom": "Jean",
    "email": "jean.dupont@example.com",
    "password": "MonMotDePasse123!",
    "telephone": "+33612345678"
  }'
```

### Exemple de connexion

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jean.dupont@example.com",
    "password": "MonMotDePasse123!"
  }'
```

### Utiliser le token

```bash
curl -X GET http://localhost:8080/api/users/me \
  -H "Authorization: Bearer <votre_access_token>"
```

## 📁 Structure du Projet

```
compulec_backend/
├── src/main/java/com/compulec/backend/
│   ├── CompulecBackendApplication.java   # Point d'entrée
│   ├── config/
│   │   ├── SecurityConfig.java           # Config Spring Security
│   │   └── OpenApiConfig.java            # Config Swagger
│   ├── controller/
│   │   ├── AuthController.java           # Endpoints Auth
│   │   └── UserController.java           # Endpoints Users
│   ├── dto/
│   │   ├── request/
│   │   │   ├── LoginRequest.java
│   │   │   └── RegisterRequest.java
│   │   └── response/
│   │       ├── ApiResponse.java          # Réponse générique
│   │       ├── AuthResponse.java
│   │       └── UserResponse.java
│   ├── entity/
│   │   ├── User.java                     # Entité utilisateur
│   │   ├── RefreshToken.java             # Entité refresh token
│   │   └── Role.java                     # Enum des rôles
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java   # Handler global
│   │   └── ResourceNotFoundException.java
│   ├── repository/
│   │   ├── UserRepository.java
│   │   └── RefreshTokenRepository.java
│   ├── security/
│   │   ├── JwtService.java               # Service JWT
│   │   └── JwtAuthenticationFilter.java  # Filtre JWT
│   └── service/
│       ├── AuthService.java              # Service Auth
│       └── UserService.java              # Service Users
└── src/main/resources/
    └── application.properties            # Configuration
```

## 🔑 Rôles

| Rôle | Description |
|------|-------------|
| `USER` | Utilisateur standard |
| `ADMIN` | Administrateur (accès complet) |
| `MANAGER` | Manager |

## 🛠️ Technologies

| Technologie | Version |
|-------------|---------|
| Java | 21 |
| Spring Boot | 4.0.0 |
| Spring Security | 6.x |
| MySQL | 8.0+ |
| JWT (jjwt) | 0.12.6 |
| Lombok | Latest |
| Springdoc OpenAPI | 2.8.4 |

## 🧪 Tests

```bash
./mvnw test
```
