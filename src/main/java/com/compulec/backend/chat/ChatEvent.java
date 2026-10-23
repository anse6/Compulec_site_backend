package com.compulec.backend.chat;

/**
 * Événement de chat diffusé en temps réel (après validation en base).
 *
 * @param type         ce qui s'est passé
 * @param sessionToken conversation concernée
 * @param status       statut de la conversation après l'événement
 * @param visitorName  nom du visiteur (pour les notifications de l'équipe)
 */
public record ChatEvent(Type type, String sessionToken, String status, String visitorName) {

    public enum Type {
        /** Nouveau message du visiteur (son à l'équipe si un conseiller est attendu ou en ligne). */
        VISITOR_MESSAGE,
        /** Nouveau message du bot ou d'un conseiller. */
        REPLY,
        /** Le visiteur demande un conseiller (son + notification à l'équipe). */
        TRANSFER_REQUESTED,
        /** Changement de statut ou de conseiller (prise en charge, clôture…). */
        STATUS,
        /** Messages marqués comme lus. */
        READ
    }
}
