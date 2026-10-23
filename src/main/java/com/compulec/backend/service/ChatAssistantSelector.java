package com.compulec.backend.service;

import com.compulec.backend.entity.ChatMessage;
import com.compulec.backend.entity.ChatSession;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Choisit l'assistant IA du chat selon app.ai.provider :
 * - "chatling" ou "anthropic" : ce fournisseur uniquement ;
 * - "auto" (défaut) : Chatling s'il est configuré, sinon Claude (Anthropic), sinon aucun (bot à mots-clés).
 */
@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class ChatAssistantSelector implements ChatAssistant {

    private final ChatlingAssistant chatling;
    private final AiChatAssistant anthropic;

    @Value("${app.ai.provider:auto}")
    private String provider;

    private ChatAssistant active;

    @PostConstruct
    void select() {
        active = switch (provider.toLowerCase(Locale.ROOT)) {
            case "chatling" -> chatling;
            case "anthropic", "claude" -> anthropic;
            default -> chatling.isEnabled() ? chatling : anthropic;
        };
        log.info("Chat : assistant IA {}", active.isEnabled()
                ? (active == chatling ? "Chatling" : "Claude (Anthropic)")
                : "désactivé, réponses par mots-clés");
    }

    @Override
    public boolean isEnabled() {
        return active.isEnabled();
    }

    @Override
    public Optional<Reply> reply(ChatSession session, List<ChatMessage> history) {
        return active.reply(session, history);
    }
}
