package com.compulec.backend.service;

import com.compulec.backend.entity.ChatMessage;
import com.compulec.backend.entity.ChatSenderType;
import com.compulec.backend.entity.ChatSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Assistant Chatling testé contre un faux serveur local (aucun appel réel, aucun crédit consommé).
 */
class ChatlingAssistantTest {

    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<JsonNode> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String body = "{\"status\":\"success\",\"data\":{\"conversation_id\":\"4121189959\","
            + "\"response\":\"Oui, COMPULEC installe des systèmes de **vidéosurveillance**.\\n\\n[[TRANSFERT]]\",\"sources\":[]}}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(json.readTree(exchange.getRequestBody()));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private ChatlingAssistant assistant(String key) {
        ChatlingAssistant assistant = new ChatlingAssistant();
        ReflectionTestUtils.setField(assistant, "enabled", true);
        ReflectionTestUtils.setField(assistant, "apiKey", key);
        ReflectionTestUtils.setField(assistant, "chatbotId", "2526884317");
        ReflectionTestUtils.setField(assistant, "aiModelId", 32);
        ReflectionTestUtils.setField(assistant, "languageId", 1);
        ReflectionTestUtils.setField(assistant, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/v2");
        assistant.init();
        return assistant;
    }

    private static List<ChatMessage> history(String visitorMessage) {
        return List.of(
                ChatMessage.builder().senderType(ChatSenderType.BOT).content("Bonjour !").build(),
                ChatMessage.builder().senderType(ChatSenderType.VISITOR).content(visitorMessage).build());
    }

    @Test
    void sendsTheDocumentedRequest_AndKeepsTheConversationContext() {
        ChatlingAssistant assistant = assistant("cl-test-key");
        ChatSession session = ChatSession.builder().language("fr").build();

        ChatAssistant.Reply reply = assistant.reply(session, history("Installez-vous de la vidéosurveillance ?")).orElseThrow();

        assertEquals("/v2/chatbots/2526884317/ai/kb/chat", lastPath.get());
        assertEquals("Bearer cl-test-key", lastAuth.get());
        JsonNode sent = lastBody.get();
        assertEquals("Installez-vous de la vidéosurveillance ?", sent.path("message").asText());
        assertEquals(32, sent.path("ai_model_id").asInt());
        assertEquals(1, sent.path("language_id").asInt());
        assertTrue(sent.path("instructions").size() >= 3);
        assertTrue(sent.path("conversation_id").isMissingNode(), "Première question : nouvelle conversation");

        // Marqueur retiré, transfert signalé, gras Markdown nettoyé
        assertTrue(reply.suggestTransfer());
        assertEquals("Oui, COMPULEC installe des systèmes de vidéosurveillance.", reply.text());
        assertEquals("4121189959", session.getChatlingConversationId());

        // Question suivante : même conversation Chatling (Chatling se souvient du contexte)
        assistant.reply(session, history("Et la maintenance ?"));
        assertEquals("4121189959", lastBody.get().path("conversation_id").asText());
    }

    @Test
    void chatlingErrorFallsBackToKeywords() {
        status = 429;
        body = "{\"status\":\"error\",\"message\":\"Too many requests\"}";
        assertTrue(assistant("cl-test-key").reply(ChatSession.builder().build(), history("Bonjour")).isEmpty());
    }

    @Test
    void errorStatusInBodyFallsBackToKeywords() {
        body = "{\"status\":\"error\",\"message\":\"Not enough AI credits\"}";
        assertTrue(assistant("cl-test-key").reply(ChatSession.builder().build(), history("Bonjour")).isEmpty());
    }

    @Test
    void withoutKeyTheAssistantIsDisabled() {
        ChatlingAssistant assistant = assistant("");
        assertFalse(assistant.isEnabled());
        assertTrue(assistant.reply(ChatSession.builder().build(), history("Bonjour")).isEmpty());
        assertNull(lastBody.get());
    }
}
