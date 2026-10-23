package com.compulec.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO représentant un item de l'activité récente du tableau de bord.
 * Le texte affiché est construit côté frontend (traduit) à partir de type + action + subject.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardActivityResponse {

    /** Type d'activité : PROJECT, MESSAGE, ARTICLE, GALLERY, CHAT */
    private String type;

    /** Action : PUBLISHED, DRAFT (projets/articles), RECEIVED (message), ADDED (galerie), TRANSFERRED (chat) */
    private String action;

    /** Identifiant de l'élément concerné (projet, message, article…) */
    private Long targetId;

    /** Nom de l'élément concerné (titre, nom du visiteur…) ; peut être null */
    private String subject;

    /** Date de l'événement */
    private LocalDateTime date;
}
