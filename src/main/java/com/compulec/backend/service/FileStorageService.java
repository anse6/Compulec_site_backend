package com.compulec.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

    /**
     * Enregistre un fichier sur le disque local et renvoie son URL relative.
     */
    public String storeFile(MultipartFile file) {
        // Nettoyer le nom du fichier
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) {
            originalFileName = "unnamed.jpg";
        }

        // Générer un nom unique pour éviter les collisions
        String fileExtension = "";
        int i = originalFileName.lastIndexOf('.');
        if (i > 0) {
            fileExtension = originalFileName.substring(i);
        }
        String fileName = UUID.randomUUID().toString() + fileExtension;

        try {
            // Vérifier les caractères invalides
            if (fileName.contains("..")) {
                throw new IllegalArgumentException("Le nom du fichier contient des séquences de chemin invalides " + fileName);
            }

            // Copier le fichier dans le dossier cible
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // Retourner l'URI relative pour y accéder via HTTP
            return "/api/uploads/" + fileName;
        } catch (IOException ex) {
            throw new RuntimeException("Impossible de stocker le fichier " + fileName + ". Veuillez réessayer.", ex);
        }
    }
}
