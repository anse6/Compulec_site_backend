package com.compulec.backend.entity;

/**
 * Statuts possibles d'une session de chat.
 */
public enum ChatSessionStatus {
    /** Session active : le visiteur dialogue avec le bot Chatling */
    ACTIVE,
    /** En attente d'un admin : le visiteur a demandé un transfert */
    WAITING_ADMIN,
    /** Un admin a pris en charge la session */
    WITH_ADMIN,
    /** Session terminée */
    CLOSED
}
