package com.compulec.backend.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Temps réel du chat par Server-Sent Events (SSE).
 *
 * Les flux ne transportent que des signaux légers ({type, sessionToken, status}) : à la réception,
 * le navigateur recharge les données par l'API habituelle. Une seule source de vérité, et rien
 * de sensible ne transite par le flux.
 *
 * - Visiteur : un flux par conversation (GET /chat/stream/{token}), 3 connexions max par conversation.
 * - Équipe : un flux global (GET /chat/admin/stream), 5 connexions max par personne, fermé après
 *   15 minutes (durée d'un access token) : la reconnexion revérifie l'authentification.
 */
@Service
@Slf4j
public class ChatStreamService {

    private static final long VISITOR_TIMEOUT_MS = 30 * 60 * 1000L;
    private static final long STAFF_TIMEOUT_MS = 15 * 60 * 1000L;
    private static final int MAX_PER_VISITOR_SESSION = 3;
    private static final int MAX_PER_STAFF = 5;
    private static final int MAX_TOTAL = 1000;

    private final Map<String, Set<SseEmitter>> visitorStreams = new ConcurrentHashMap<>();
    private final Map<Long, Set<SseEmitter>> staffStreams = new ConcurrentHashMap<>();
    private final AtomicInteger total = new AtomicInteger();

    /** Ouvre un flux pour le visiteur d'une conversation. */
    public SseEmitter subscribeVisitor(String sessionToken) {
        return register(visitorStreams, sessionToken, MAX_PER_VISITOR_SESSION, VISITOR_TIMEOUT_MS);
    }

    /** Ouvre un flux pour un membre de l'équipe. */
    public SseEmitter subscribeStaff(Long userId) {
        return register(staffStreams, userId, MAX_PER_STAFF, STAFF_TIMEOUT_MS);
    }

    /**
     * Diffuse un événement une fois la transaction validée (ou immédiatement s'il n'y en a pas).
     */
    @TransactionalEventListener(fallbackExecution = true)
    public void onChatEvent(ChatEvent event) {
        Map<String, Object> visitorPayload = Map.of(
                "type", event.type().name(),
                "status", event.status());
        send(visitorStreams.get(event.sessionToken()), visitorPayload);

        Map<String, Object> staffPayload = Map.of(
                "type", event.type().name(),
                "sessionToken", event.sessionToken(),
                "status", event.status(),
                "visitorName", event.visitorName() == null ? "" : event.visitorName());
        staffStreams.values().forEach(emitters -> send(emitters, staffPayload));
    }

    /** Signal de vie toutes les 25 s : garde les connexions ouvertes à travers les proxys et détecte les clients partis. */
    @Scheduled(fixedRate = 25_000, initialDelay = 25_000)
    public void heartbeat() {
        visitorStreams.values().forEach(this::ping);
        staffStreams.values().forEach(this::ping);
    }

    /** Nombre de flux ouverts (supervision et tests). */
    public int openStreams() {
        return total.get();
    }

    // ===== Méthodes privées =====

    private <K> SseEmitter register(Map<K, Set<SseEmitter>> registry, K key, int maxPerKey, long timeoutMs) {
        Set<SseEmitter> emitters = registry.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>());
        if (emitters.size() >= maxPerKey || total.get() >= MAX_TOTAL) {
            throw new IllegalArgumentException("Trop de connexions temps réel ouvertes. Fermez un onglet et réessayez.");
        }
        SseEmitter emitter = new SseEmitter(timeoutMs);
        emitters.add(emitter);
        total.incrementAndGet();

        Runnable remove = () -> {
            if (emitters.remove(emitter)) {
                total.decrementAndGet();
            }
            if (emitters.isEmpty()) {
                registry.remove(key, emitters);
            }
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(() -> {
            remove.run();
            emitter.complete();
        });
        emitter.onError(e -> remove.run());

        try {
            // Délai de reconnexion conseillé au navigateur + confirmation immédiate
            emitter.send(SseEmitter.event().reconnectTime(3000).name("ready").data("{}", MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    private void send(Set<SseEmitter> emitters, Map<String, Object> payload) {
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("chat").data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                emitter.completeWithError(e);
            }
        }
    }

    private void ping(Set<SseEmitter> emitters) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                emitter.completeWithError(e);
            }
        }
    }
}
