package com.compulec.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Notification de l'en-tête admin. Le texte affiché est construit (et traduit) côté frontend
 * à partir de {@code type}, {@code count} et {@code subject}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private String id;

    /** CONTACT, CHAT, PROJECT, GALLERY */
    private String type;

    /** CONTACT : nombre de messages non lus ; null pour les autres types */
    private Long count;

    /** Élément concerné : n° de session (CHAT), titre du projet ou de l'image ; peut être null */
    private String subject;

    /**
     * Sans @JsonProperty, Lombok génère isRead() et Jackson publie la propriété "read" :
     * le frontend lisait alors {@code n.isRead} = undefined.
     */
    @JsonProperty("isRead")
    private boolean isRead;

    /** Date de l'événement (le « il y a 5 min » est calculé côté frontend, dans la langue de l'utilisateur) */
    private LocalDateTime date;
}
