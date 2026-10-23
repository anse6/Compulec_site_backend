package com.compulec.backend.security;

import com.compulec.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;


/**
 * Service responsable de la génération et validation des tokens JWT.
 *
 * Chaque token porte :
 *  - "typ" : "access", "mfa" ou "mfa_setup" (seul "access" donne accès à l'API)
 *  - "tv"  : la version des jetons de l'utilisateur (révocation immédiate, voir User#tokenVersion)
 */
@Service
public class JwtService {

    public static final String TYPE_CLAIM = "typ";
    public static final String TOKEN_VERSION_CLAIM = "tv";
    private static final String ACCESS = "access";
    private static final String MFA = "mfa";
    private static final String MFA_SETUP = "mfa_setup";
    private static final long MFA_TOKEN_EXPIRATION_MS = 5 * 60 * 1000;
    /** Plus long : le temps d'installer l'application d'authentification et de scanner le QR code. */
    private static final long MFA_SETUP_TOKEN_EXPIRATION_MS = 15 * 60 * 1000;

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    /**
     * Extrait le nom d'utilisateur (email) depuis le token.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extrait une claim spécifique depuis le token.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Génère un access token pour un utilisateur.
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /**
     * Génère un access token avec des claims supplémentaires.
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put(TYPE_CLAIM, ACCESS);
        if (userDetails instanceof User user) {
            claims.put(TOKEN_VERSION_CLAIM, user.getTokenVersion());
        }
        return buildToken(claims, userDetails, jwtExpiration);
    }

    /**
     * Jeton d'étape 2FA : prouve que le mot de passe a été vérifié,
     * mais ne donne accès à rien d'autre qu'aux endpoints /auth/mfa/**.
     *
     * @param setup true pour l'activation facultative (15 min), false pour l'étape du code (5 min)
     */
    public String generateMfaToken(User user, boolean setup) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(TYPE_CLAIM, setup ? MFA_SETUP : MFA);
        claims.put(TOKEN_VERSION_CLAIM, user.getTokenVersion());
        return buildToken(claims, user, setup ? MFA_SETUP_TOKEN_EXPIRATION_MS : MFA_TOKEN_EXPIRATION_MS);
    }

    /**
     * Vérifie un jeton 2FA (signature, expiration, type) et renvoie l'email de l'utilisateur, ou null.
     */
    public String extractMfaSubject(String token, boolean setup) {
        try {
            Claims claims = extractAllClaims(token);
            if (!(setup ? MFA_SETUP : MFA).equals(claims.get(TYPE_CLAIM, String.class))) {
                return null;
            }
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    /** Version des jetons portée par un jeton 2FA déjà vérifié. */
    public Integer extractTokenVersion(String token) {
        return extractAllClaims(token).get(TOKEN_VERSION_CLAIM, Integer.class);
    }

    /**
     * Vérifie si un access token est valide pour un utilisateur donné :
     * signature, sujet, expiration, type "access", compte actif et version des jetons à jour.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final Claims claims = extractAllClaims(token);

        if (!userDetails.getUsername().equals(claims.getSubject())
                || claims.getExpiration().before(new Date())
                || !ACCESS.equals(claims.get(TYPE_CLAIM, String.class))
                || !userDetails.isEnabled()) {
            return false;
        }
        if (userDetails instanceof User user) {
            Integer version = claims.get(TOKEN_VERSION_CLAIM, Integer.class);
            return version != null && version == user.getTokenVersion();
        }
        return true;
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .id(UUID.randomUUID().toString())   // jti unique → évite les collisions de tokens
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
