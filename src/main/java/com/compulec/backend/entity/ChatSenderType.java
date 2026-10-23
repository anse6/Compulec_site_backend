package com.compulec.backend.entity;

/**
 * Type d'expéditeur d'un message dans la session de chat.
 */
public enum ChatSenderType {
    /** Message envoyé par le visiteur anonyme */
    VISITOR,
    /** Réponse générée automatiquement par le bot Chatling */
    BOT,
    /** Message envoyé manuellement par un administrateur Compulec */
    ADMIN
}
