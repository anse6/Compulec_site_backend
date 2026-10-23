package com.compulec.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour les données du graphique des interactions (Dashboard) : un point par mois.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionChartResponse {

    /** Mois au format ISO "yyyy-MM" (le nom du mois est formaté côté frontend, dans la langue de l'utilisateur) */
    private String month;
    private long messages;
    private long chats;
    private long projects;
    private long articles;
}
