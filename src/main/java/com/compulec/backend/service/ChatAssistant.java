package com.compulec.backend.service;

import com.compulec.backend.entity.ChatMessage;
import com.compulec.backend.entity.ChatSession;

import java.util.List;
import java.util.Optional;

/**
 * Assistant IA du chat (Chatling ou Claude).
 */
public interface ChatAssistant {

    /** Marqueur que l'IA ajoute quand un conseiller humain doit prendre le relais. */
    String TRANSFER_MARKER = "[[TRANSFERT]]";

    /** Réponse de l'assistant. */
    record Reply(String text, boolean suggestTransfer) {

        /** Construit une réponse à partir du texte brut de l'IA (retire le marqueur de transfert). */
        static Reply fromRawText(String raw) {
            boolean transfer = raw.contains(TRANSFER_MARKER);
            String text = raw.replace(TRANSFER_MARKER, "")
                    .replace("**", "")
                    .replaceAll("\n{3,}", "\n\n")
                    .trim();
            return new Reply(text, transfer);
        }
    }

    boolean isEnabled();

    /**
     * Réponse au dernier message du visiteur (dernier élément de {@code history}).
     *
     * @return vide si l'assistant est désactivé ou en erreur : le chat utilise alors le bot à mots-clés
     */
    Optional<Reply> reply(ChatSession session, List<ChatMessage> history);
}
