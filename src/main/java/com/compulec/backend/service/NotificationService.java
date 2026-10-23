package com.compulec.backend.service;

import com.compulec.backend.dto.response.NotificationResponse;
import com.compulec.backend.entity.ChatSessionStatus;
import com.compulec.backend.repository.ChatSessionRepository;
import com.compulec.backend.repository.ContactRepository;
import com.compulec.backend.repository.GalleryRepository;
import com.compulec.backend.repository.ProjetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    /** Nombre maximum de notifications par source. */
    private static final int MAX_PER_SOURCE = 10;

    private final ContactRepository contactRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ProjetRepository projetRepository;
    private final GalleryRepository galleryRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications() {
        List<NotificationResponse> notifications = new ArrayList<>();

        // 1. Messages de contact non lus (regroupés), datés du plus récent
        long unreadContacts = contactRepository.countByLu(false);
        if (unreadContacts > 0) {
            LocalDateTime latest = contactRepository
                    .findByLu(false, PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt")))
                    .stream().findFirst().map(c -> c.getCreatedAt()).orElse(null);
            notifications.add(NotificationResponse.builder()
                    .id("contact_group")
                    .type("CONTACT")
                    .count(unreadContacts)
                    .isRead(false)
                    .date(latest)
                    .build());
        }

        // 2. Conversations en attente d'un conseiller
        chatSessionRepository.findRecentByStatusIn(List.of(ChatSessionStatus.WAITING_ADMIN), PageRequest.of(0, MAX_PER_SOURCE))
                .forEach(session -> notifications.add(NotificationResponse.builder()
                        .id("chat_" + session.getId())
                        .type("CHAT")
                        .subject("#" + session.getSessionToken().substring(0, 8).toUpperCase())
                        .isRead(false)
                        .date(session.getLastActivityAt() != null ? session.getLastActivityAt() : session.getCreatedAt())
                        .build()));

        // 3. Projets publiés récemment (48 dernières heures) — informatif
        LocalDateTime twoDaysAgo = LocalDateTime.now().minusDays(2);
        projetRepository.findAll(PageRequest.of(0, MAX_PER_SOURCE, Sort.by(Sort.Direction.DESC, "createdAt"))).forEach(projet -> {
            if (projet.isPublie() && projet.getCreatedAt() != null && projet.getCreatedAt().isAfter(twoDaysAgo)) {
                notifications.add(NotificationResponse.builder()
                        .id("projet_" + projet.getId())
                        .type("PROJECT")
                        .subject(projet.getTitre())
                        .isRead(true)
                        .date(projet.getCreatedAt())
                        .build());
            }
        });

        // 4. Images ajoutées récemment à la galerie (48 dernières heures) — informatif
        galleryRepository.findAll(PageRequest.of(0, MAX_PER_SOURCE, Sort.by(Sort.Direction.DESC, "createdAt"))).forEach(gallery -> {
            if (gallery.getCreatedAt() != null && gallery.getCreatedAt().isAfter(twoDaysAgo)) {
                notifications.add(NotificationResponse.builder()
                        .id("gallery_" + gallery.getId())
                        .type("GALLERY")
                        .subject(gallery.getTitre())
                        .isRead(true)
                        .date(gallery.getCreatedAt())
                        .build());
            }
        });

        return notifications;
    }

    /** Marque tous les messages de contact comme lus, en une seule requête UPDATE. */
    @Transactional
    public void markAllAsRead() {
        contactRepository.markAllAsRead(LocalDateTime.now());
    }
}
