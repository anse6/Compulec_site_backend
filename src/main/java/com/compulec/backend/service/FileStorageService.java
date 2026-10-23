package com.compulec.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Service pour gérer le stockage local des fichiers et images des projets.
 */
@Service
@Slf4j
public class FileStorageService {

    private final Path fileStorageLocation;

    public FileStorageService() {
        // Enregistrer dans le dossier Bureau/compulec/uploads du projet
        this.fileStorageLocation = Paths.get("uploads")
                .toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
            log.info("Dossier de stockage créé : {}", this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Impossible de créer le dossier de stockage des fichiers.", ex);
        }
    }

    /** Extensions acceptées (déclarées par le navigateur). */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /**
     * Enregistre une image sur le disque local et renvoie son URL relative.
     *
     * Sécurité : le type est déterminé par les premiers octets du fichier (signature),
     * pas par le nom ni par le Content-Type envoyés par le client. Seules les images
     * JPEG, PNG, GIF et WEBP sont acceptées ; un fichier .html, .svg, .js ou un exécutable
     * renommé en .png est refusé. Le nom de fichier est toujours régénéré (UUID).
     */
    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier envoyé est vide.");
        }

        String originalFileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = originalFileName.lastIndexOf('.');
        String declaredExtension = dot >= 0 ? originalFileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_EXTENSIONS.contains(declaredExtension)) {
            throw new IllegalArgumentException(UNSUPPORTED);
        }

        try {
            String extension = detectImageExtension(file);
            if (extension == null) {
                log.warn("Fichier refusé : contenu non reconnu comme image ({})", originalFileName);
                throw new IllegalArgumentException(UNSUPPORTED);
            }

            String fileName = UUID.randomUUID() + "." + extension;
            Path targetLocation = this.fileStorageLocation.resolve(fileName).normalize();
            if (!targetLocation.startsWith(this.fileStorageLocation)) {
                throw new IllegalArgumentException("Chemin de fichier invalide.");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }

            // Retourner l'URI relative pour y accéder via HTTP (sans /api, c'est le context-path)
            return "/uploads/" + fileName;
        } catch (IOException ex) {
            throw new RuntimeException("Impossible de stocker le fichier. Veuillez réessayer.", ex);
        }
    }

    private static final String UNSUPPORTED =
            "Format de fichier non autorisé : seules les images JPG, PNG, GIF et WEBP sont acceptées.";

    /** Reconnaît le format réel à partir de la signature binaire, ou renvoie null. */
    static String detectImageExtension(MultipartFile file) throws IOException {
        byte[] h = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(h, 0, h.length);
        }
        if (read >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (read >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) {
            return "png";
        }
        if (read >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8'
                && (h[4] == '7' || h[4] == '9') && h[5] == 'a') {
            return "gif";
        }
        if (read >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
