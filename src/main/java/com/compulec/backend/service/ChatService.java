package com.compulec.backend.service;

import com.compulec.backend.chat.ChatEvent;
import com.compulec.backend.dto.request.AdminChatReplyRequest;
import com.compulec.backend.dto.request.ChatMessageRequest;
import com.compulec.backend.dto.request.ChatStartRequest;
import com.compulec.backend.dto.response.ChatBotReplyResponse;
import com.compulec.backend.dto.response.ChatMessageResponse;
import com.compulec.backend.dto.response.ChatSessionResponse;
import com.compulec.backend.entity.*;
import com.compulec.backend.exception.ConflictException;
import com.compulec.backend.repository.ChatMessageRepository;
import com.compulec.backend.repository.ChatSessionRepository;
import com.compulec.backend.repository.ContactRepository;
import com.compulec.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service principal du module de chat Compulec.
 * Gère le cycle de vie complet d'une session de chat :
 * - Démarrage de la session (visiteur anonyme), dans la langue du site
 * - Réponses de l'assistant IA (ou du bot à mots-clés en secours)
 * - Transfert vers l'équipe, alerte de tous les admins et managers actifs
 * - Prise en charge, réponses signées, messages non lus
 * - Fermeture (manuelle ou automatique), transcription envoyée au visiteur
 * - Diffusion en temps réel de chaque changement (événements ChatEvent)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChatService {

    /** Sans IA : nombre de questions après lequel on propose un conseiller. */
    private static final int TRANSFER_THRESHOLD = 5;
    private static final int PREVIEW_LENGTH = 120;
    private static final int MAX_PAGE_SIZE = 50;
    private static final DateTimeFormatter TRANSCRIPT_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private static final String SESSION_NOT_FOUND = "Session de chat introuvable";

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ContactRepository contactRepository;
    private final KeywordBotService keywordBot;
    private final ChatAssistant assistant;
    private final EmailService emailService;
    private final ApplicationEventPublisher events;

    /** Plafond de réponses IA par conversation (maîtrise des coûts). */
    @Value("${app.ai.max-replies-per-session:30}")
    private int maxAiRepliesPerSession;

    // =========================================================================
    // CÔTÉ VISITEUR
    // =========================================================================

    /**
     * Démarre une nouvelle session de chat pour un visiteur anonyme.
     * Génère un token UUID unique qui identifie la session.
     *
     * @param request nom, email optionnel et langue du site
     * @return la session créée sous forme de DTO
     */
    public ChatSessionResponse startSession(ChatStartRequest request) {
        String language = request.getLanguage() == null ? "fr" : request.getLanguage().toLowerCase(Locale.ROOT);

        ChatSession session = chatSessionRepository.save(ChatSession.builder()
                .visitorName(request.getVisitorName().trim())
                .visitorEmail(request.getVisitorEmail())
                .sessionToken(UUID.randomUUID().toString())
                .status(ChatSessionStatus.ACTIVE)
                .language(language)
                .questionCount(0)
                .build());
        log.info("Nouvelle session de chat démarrée : {}", session.getId());

        String name = session.getVisitorName();
        recordMessage(session, ChatSenderType.BOT, "fr".equals(language)
                ? "Bonjour " + name + " ! 👋 Je suis l'assistant virtuel de COMPULEC. Posez-moi vos questions sur nos "
                  + "services, nos projets ou la façon de nous contacter. Un conseiller peut aussi prendre le relais à tout moment."
                : "Hello " + name + "! 👋 I'm COMPULEC's virtual assistant. Ask me about our services, our projects "
                  + "or how to reach us. An advisor can also take over at any time.", null);

        return toSessionResponse(session, true, false);
    }

    /**
     * Message du visiteur. Avec le bot, l'assistant IA répond (ou le bot à mots-clés en secours).
     * Une fois l'équipe sollicitée, le bot se tait et le message est compté comme non lu.
     *
     * Volontairement hors transaction : l'appel à l'IA peut prendre plusieurs secondes et ne doit pas
     * bloquer une connexion à la base. Chaque écriture s'enregistre (et se diffuse) immédiatement.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChatBotReplyResponse sendMessage(ChatMessageRequest request) {
        ChatSession session = findSessionByToken(request.getSessionToken());

        if (session.getStatus() == ChatSessionStatus.CLOSED) {
            throw new ConflictException("Cette session de chat est terminée.");
        }

        recordMessage(session, ChatSenderType.VISITOR, request.getMessage(), null);

        if (session.getStatus() == ChatSessionStatus.WITH_ADMIN
                || session.getStatus() == ChatSessionStatus.WAITING_ADMIN) {
            // Pas de réponse du bot : le message attend l'équipe (cette phrase n'est pas enregistrée)
            return replyResponse(session, "fr".equals(session.getLanguage())
                    ? "Votre message a été transmis. Un conseiller va vous répondre très prochainement."
                    : "Your message has been forwarded. An advisor will reply shortly.", false);
        }

        session.setQuestionCount(session.getQuestionCount() + 1);

        String botReply;
        boolean showAdminTransfer;
        if (assistant.isEnabled() && session.getAiReplyCount() < maxAiRepliesPerSession) {
            List<ChatMessage> history = chatMessageRepository.findBySessionOrderByCreatedAtAsc(session);
            ChatAssistant.Reply reply = assistant.reply(session, history).orElse(null);
            if (reply != null) {
                session.setAiReplyCount(session.getAiReplyCount() + 1);
                botReply = reply.text();
                showAdminTransfer = reply.suggestTransfer();
            } else {
                botReply = keywordBot.reply(request.getMessage());
                showAdminTransfer = true;
            }
        } else if (assistant.isEnabled()) {
            // Plafond atteint : on oriente vers un conseiller
            botReply = "fr".equals(session.getLanguage())
                    ? "Pour aller plus loin, un conseiller COMPULEC peut prendre le relais de cette conversation."
                    : "To go further, a COMPULEC advisor can take over this conversation.";
            showAdminTransfer = true;
        } else {
            botReply = keywordBot.reply(request.getMessage());
            showAdminTransfer = session.getQuestionCount() >= TRANSFER_THRESHOLD;
        }

        // Un conseiller a pu prendre la main pendant que l'IA réfléchissait : on ne répond plus à sa place
        ChatSession fresh = findSessionByToken(session.getSessionToken());
        if (fresh.getStatus() != ChatSessionStatus.ACTIVE) {
            return replyResponse(fresh, "", false);
        }
        fresh.setQuestionCount(session.getQuestionCount());
        fresh.setAiReplyCount(session.getAiReplyCount());
        fresh.setChatlingConversationId(session.getChatlingConversationId());
        recordMessage(fresh, ChatSenderType.BOT, botReply, null);
        return replyResponse(fresh, botReply, showAdminTransfer);
    }

    /**
     * Le visiteur demande un conseiller : la session passe en attente et
     * TOUS les admins et managers actifs sont prévenus (temps réel immédiat, e-mails en arrière-plan).
     *
     * Idempotent : si la demande est déjà faite (double clic, plusieurs onglets), on renvoie
     * simplement la conversation, sans nouveau message ni nouvel e-mail.
     */
    public ChatSessionResponse requestAdminTransfer(String sessionToken) {
        ChatSession session = findSessionByToken(sessionToken);

        if (session.getStatus() == ChatSessionStatus.CLOSED) {
            throw new ConflictException("Cette session de chat est terminée.");
        }
        if (session.getStatus() != ChatSessionStatus.ACTIVE) {
            return toSessionResponse(session, true, false);
        }

        boolean fr = "fr".equals(session.getLanguage());
        List<User> team = activeTeam();
        if (team.isEmpty()) {
            log.warn("Aucun membre de l'équipe actif pour la session {}", session.getId());
            recordMessage(session, ChatSenderType.BOT, fr
                    ? "Nous sommes désolés, aucun conseiller n'est disponible pour le moment. "
                      + "Laissez-nous un message depuis la page Contact : nous vous répondrons rapidement."
                    : "Sorry, no advisor is available right now. "
                      + "Leave us a message from the Contact page and we will get back to you quickly.", null);
            return toSessionResponse(session, true, false);
        }

        // Transition atomique en base : un seul des appels simultanés la réussit
        if (chatSessionRepository.transition(session.getId(), ChatSessionStatus.ACTIVE, ChatSessionStatus.WAITING_ADMIN) == 0) {
            return toSessionResponse(findSessionByToken(sessionToken), true, false);
        }
        session.setStatus(ChatSessionStatus.WAITING_ADMIN);
        session.setUnreadCount(Math.max(1, session.getUnreadCount()));
        log.info("Session {} transférée : {} membre(s) de l'équipe à alerter", session.getId(), team.size());

        recordMessage(session, ChatSenderType.BOT, fr
                ? "✅ Parfait ! Un conseiller COMPULEC a été notifié et va prendre en charge "
                  + "votre conversation très prochainement. Merci de patienter quelques instants."
                : "✅ Great! A COMPULEC advisor has been notified and will take over "
                  + "your conversation shortly. Thank you for your patience.", null);
        publish(ChatEvent.Type.TRANSFER_REQUESTED, session);

        return toSessionResponse(session, true, false);
    }

    /**
     * Historique d'une session pour le visiteur (sans données internes à l'équipe).
     */
    @Transactional(readOnly = true)
    public ChatSessionResponse getSessionHistory(String sessionToken) {
        return toSessionResponse(findSessionByToken(sessionToken), true, false);
    }

    /** Vérifie qu'une session existe (avant d'ouvrir un flux temps réel). */
    @Transactional(readOnly = true)
    public void assertSessionExists(String sessionToken) {
        findSessionByToken(sessionToken);
    }

    // =========================================================================
    // CÔTÉ ÉQUIPE (ADMIN / MANAGER)
    // =========================================================================

    /**
     * Détail complet d'une session pour l'équipe.
     */
    @Transactional(readOnly = true)
    public ChatSessionResponse getSessionForStaff(String sessionToken) {
        return toSessionResponse(findSessionByToken(sessionToken), true, true);
    }

    /**
     * Conversations d'un statut, triées par dernière activité, paginées, avec recherche.
     */
    @Transactional(readOnly = true)
    public Page<ChatSessionResponse> searchSessions(String status, String query, int page, int size) {
        ChatSessionStatus parsedStatus;
        try {
            parsedStatus = ChatSessionStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Statut de conversation inconnu : " + status);
        }
        String search = query == null || query.isBlank()
                ? null
                : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return chatSessionRepository.search(parsedStatus, search, pageable)
                .map(s -> toSessionResponse(s, false, true));
    }

    /**
     * Compteurs pour les onglets : nombre de conversations par statut + messages non lus.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> countSessions() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ChatSessionStatus status : ChatSessionStatus.values()) {
            counts.put(status.name(), 0L);
        }
        for (Object[] row : chatSessionRepository.countByStatus()) {
            counts.put(((ChatSessionStatus) row[0]).name(), (Long) row[1]);
        }
        counts.put("UNREAD", chatSessionRepository.sumUnread(
                List.of(ChatSessionStatus.WAITING_ADMIN, ChatSessionStatus.WITH_ADMIN)));
        return counts;
    }

    /**
     * Un membre de l'équipe prend en charge la conversation (aussi possible directement
     * depuis une conversation encore avec le bot). C'est son prénom que voit le visiteur.
     */
    public ChatSessionResponse takeOver(String sessionToken, String staffEmail) {
        ChatSession session = findSessionByToken(sessionToken);
        assignIfNeeded(session, findStaff(staffEmail));
        return toSessionResponse(session, true, true);
    }

    /**
     * Réponse d'un membre de l'équipe. S'il n'a pas encore pris la conversation en charge,
     * il le fait automatiquement. Le message est signé de son prénom.
     */
    public ChatSessionResponse adminReply(AdminChatReplyRequest request, String staffEmail) {
        ChatSession session = findSessionByToken(request.getSessionToken());
        User staff = findStaff(staffEmail);

        if (session.getStatus() == ChatSessionStatus.CLOSED) {
            throw new ConflictException("Cette session est terminée. Impossible de répondre.");
        }
        if (session.getStatus() != ChatSessionStatus.WITH_ADMIN || session.getAssignedAdmin() == null) {
            assignIfNeeded(session, staff);
        }

        session.setUnreadCount(0);
        recordMessage(session, ChatSenderType.ADMIN, request.getMessage().trim(), staff);
        return toSessionResponse(session, true, true);
    }

    /**
     * Marque les messages du visiteur comme lus.
     */
    public void markAsRead(String sessionToken) {
        ChatSession session = findSessionByToken(sessionToken);
        if (session.getUnreadCount() != 0) {
            session.setUnreadCount(0);
            chatSessionRepository.save(session);
            publish(ChatEvent.Type.READ, session);
        }
    }

    /**
     * Ferme une session. Le visiteur en est informé et reçoit la transcription par e-mail
     * s'il a donné son adresse et échangé avec un conseiller.
     */
    public void closeSession(String sessionToken, String staffEmail) {
        ChatSession session = findSessionByToken(sessionToken);
        if (session.getStatus() == ChatSessionStatus.CLOSED) {
            return;
        }
        boolean fr = "fr".equals(session.getLanguage());
        close(session, fr
                ? "Cette conversation a été clôturée par l'équipe COMPULEC. Merci pour votre échange ! "
                  + "Vous pouvez démarrer une nouvelle conversation à tout moment."
                : "This conversation was closed by the COMPULEC team. Thank you! "
                  + "You can start a new conversation at any time.");
        log.info("Session {} fermée par {}", session.getId(), staffEmail);
    }

    // =========================================================================
    // MAINTENANCE AUTOMATIQUE (appelée par ChatMaintenanceJobs)
    // =========================================================================

    /**
     * Demandes de conseiller restées sans prise en charge : rappel à l'équipe et message d'attente au visiteur.
     *
     * @return nombre de conversations relancées
     */
    public int remindUnansweredTransfers(Duration waitingFor) {
        LocalDateTime cutoff = LocalDateTime.now().minus(waitingFor);
        List<ChatSession> waiting = chatSessionRepository.findWaitingWithoutReminder(ChatSessionStatus.WAITING_ADMIN, cutoff);
        List<User> team = activeTeam();
        for (ChatSession session : waiting) {
            session.setReminderSentAt(LocalDateTime.now());
            boolean fr = "fr".equals(session.getLanguage());
            boolean hasEmail = session.getVisitorEmail() != null && !session.getVisitorEmail().isBlank();
            recordMessage(session, ChatSenderType.BOT, fr
                    ? "Nos conseillers sont très sollicités en ce moment, merci de votre patience. "
                      + (hasEmail ? "Si vous devez partir, nous vous répondrons par e-mail."
                                  : "Si vous devez partir, laissez-nous votre e-mail ou votre téléphone dans ce chat : nous vous recontacterons.")
                    : "Our advisors are very busy right now, thank you for your patience. "
                      + (hasEmail ? "If you need to leave, we will reply by email."
                                  : "If you need to leave, share your email or phone number in this chat and we will get back to you."), null);
            for (User member : team) {
                try {
                    emailService.sendChatWaitingReminderEmail(member, session, waitingFor);
                } catch (Exception e) {
                    log.error("Échec du rappel chat pour l'utilisateur {} : {}", member.getId(), e.getMessage());
                }
            }
        }
        if (!waiting.isEmpty()) {
            log.info("{} demande(s) de conseiller sans réponse : équipe relancée", waiting.size());
        }
        return waiting.size();
    }

    /**
     * Ferme les conversations inactives. Une demande de conseiller jamais traitée devient
     * un message dans « Messages » (si le visiteur a laissé son e-mail) pour ne perdre aucun contact.
     *
     * @return nombre de conversations fermées
     */
    public int closeInactive(Duration botInactivity, Duration teamInactivity) {
        LocalDateTime now = LocalDateTime.now();
        int closed = 0;

        for (ChatSession session : chatSessionRepository.findInactive(
                List.of(ChatSessionStatus.ACTIVE), now.minus(botInactivity))) {
            close(session, "fr".equals(session.getLanguage())
                    ? "Cette conversation a été fermée après une période d'inactivité. N'hésitez pas à en démarrer une nouvelle."
                    : "This conversation was closed after a period of inactivity. Feel free to start a new one.");
            closed++;
        }

        for (ChatSession session : chatSessionRepository.findInactive(
                List.of(ChatSessionStatus.WAITING_ADMIN, ChatSessionStatus.WITH_ADMIN), now.minus(teamInactivity))) {
            boolean neverAnswered = session.getStatus() == ChatSessionStatus.WAITING_ADMIN;
            if (neverAnswered) {
                saveAsContactMessage(session);
            }
            close(session, "fr".equals(session.getLanguage())
                    ? "Cette conversation a été fermée après une période d'inactivité. "
                      + (neverAnswered ? "Votre demande a été transmise à notre équipe, qui vous recontactera." : "Merci pour votre échange !")
                    : "This conversation was closed after a period of inactivity. "
                      + (neverAnswered ? "Your request has been passed on to our team, who will get back to you." : "Thank you!"));
            closed++;
        }

        if (closed > 0) {
            log.info("{} conversation(s) inactive(s) fermée(s) automatiquement", closed);
        }
        return closed;
    }

    /**
     * Supprime définitivement les conversations terminées depuis plus de {@code retention}
     * (protection des données personnelles : noms, e-mails, contenus).
     *
     * @return nombre de conversations supprimées
     */
    public int purgeClosedOlderThan(Duration retention) {
        LocalDateTime cutoff = LocalDateTime.now().minus(retention);
        chatMessageRepository.deleteForClosedSessionsBefore(ChatSessionStatus.CLOSED, cutoff);
        int deleted = chatSessionRepository.deleteClosedBefore(ChatSessionStatus.CLOSED, cutoff);
        if (deleted > 0) {
            log.info("{} conversation(s) de plus de {} jours supprimée(s)", deleted, retention.toDays());
        }
        return deleted;
    }

    // =========================================================================
    // MÉTHODES PRIVÉES UTILITAIRES
    // =========================================================================

    /**
     * Enregistre un message, met à jour la session (dernière activité, aperçu, non-lus)
     * et diffuse l'événement en temps réel.
     */
    private void recordMessage(ChatSession session, ChatSenderType type, String content, User author) {
        chatMessageRepository.save(ChatMessage.builder()
                .session(session)
                .senderType(type)
                .content(content)
                .author(author)
                .build());

        session.setLastActivityAt(LocalDateTime.now());
        String flat = content.replaceAll("\\s+", " ").trim();
        session.setLastMessagePreview(flat.length() > PREVIEW_LENGTH ? flat.substring(0, PREVIEW_LENGTH) + "…" : flat);
        if (type == ChatSenderType.VISITOR
                && (session.getStatus() == ChatSessionStatus.WAITING_ADMIN
                || session.getStatus() == ChatSessionStatus.WITH_ADMIN)) {
            session.setUnreadCount(session.getUnreadCount() + 1);
        }
        chatSessionRepository.save(session);
        publish(type == ChatSenderType.VISITOR ? ChatEvent.Type.VISITOR_MESSAGE : ChatEvent.Type.REPLY, session);
    }

    /**
     * Désigne le membre de l'équipe si la conversation n'est pas déjà à lui, et prévient le visiteur.
     */
    private void assignIfNeeded(ChatSession session, User staff) {
        if (session.getStatus() == ChatSessionStatus.CLOSED) {
            throw new ConflictException("Cette session est terminée.");
        }
        boolean alreadyMine = session.getStatus() == ChatSessionStatus.WITH_ADMIN
                && session.getAssignedAdmin() != null
                && session.getAssignedAdmin().getId().equals(staff.getId());
        if (alreadyMine) {
            return;
        }
        session.setAssignedAdmin(staff);
        session.setStatus(ChatSessionStatus.WITH_ADMIN);
        chatSessionRepository.save(session);
        recordMessage(session, ChatSenderType.BOT, "fr".equals(session.getLanguage())
                ? "👋 " + staff.getPrenom() + " de l'équipe COMPULEC a rejoint la conversation."
                : "👋 " + staff.getPrenom() + " from the COMPULEC team has joined the conversation.", null);
        publish(ChatEvent.Type.STATUS, session);
        log.info("Session {} prise en charge par l'utilisateur {}", session.getId(), staff.getId());
    }

    /** Clôture : message au visiteur, statut CLOSED, transcription par e-mail si pertinent. */
    private void close(ChatSession session, String message) {
        recordMessage(session, ChatSenderType.BOT, message, null);
        session.setStatus(ChatSessionStatus.CLOSED);
        session.setUnreadCount(0);
        chatSessionRepository.save(session);
        publish(ChatEvent.Type.STATUS, session);
        sendTranscriptIfRelevant(session);
    }

    private void sendTranscriptIfRelevant(ChatSession session) {
        if (session.getVisitorEmail() == null || session.getVisitorEmail().isBlank()) {
            return;
        }
        List<ChatMessage> messages = chatMessageRepository.findBySessionOrderByCreatedAtAsc(session);
        boolean talkedToTeam = messages.stream().anyMatch(m -> m.getSenderType() == ChatSenderType.ADMIN);
        if (!talkedToTeam) {
            return;
        }
        try {
            emailService.sendChatTranscriptEmail(session, messages);
        } catch (Exception e) {
            log.error("Échec de l'envoi de la transcription de la session {} : {}", session.getId(), e.getMessage());
        }
    }

    /** Demande jamais traitée : on la range dans « Messages » pour qu'un membre de l'équipe rappelle le visiteur. */
    private void saveAsContactMessage(ChatSession session) {
        if (session.getVisitorEmail() == null || session.getVisitorEmail().isBlank()) {
            return;
        }
        String transcript = chatMessageRepository.findBySessionOrderByCreatedAtAsc(session).stream()
                .filter(m -> m.getSenderType() == ChatSenderType.VISITOR)
                .map(m -> "[" + TRANSCRIPT_TIME.format(m.getCreatedAt()) + "] " + m.getContent())
                .collect(Collectors.joining("\n"));
        contactRepository.save(Contact.builder()
                .nom(session.getVisitorName())
                .email(session.getVisitorEmail())
                .objet("Demande de chat restée sans réponse")
                .message("Ce visiteur a demandé un conseiller dans le chat du site, sans réponse de l'équipe.\n\n"
                        + "Ses messages :\n" + transcript)
                .build());
        log.info("Session {} sans réponse enregistrée dans les messages de contact", session.getId());
    }

    private void publish(ChatEvent.Type type, ChatSession session) {
        events.publishEvent(new ChatEvent(type, session.getSessionToken(), session.getStatus().name(), session.getVisitorName()));
    }

    private ChatBotReplyResponse replyResponse(ChatSession session, String botReply, boolean showAdminTransfer) {
        return ChatBotReplyResponse.builder()
                .sessionToken(session.getSessionToken())
                .botReply(botReply)
                .questionCount(session.getQuestionCount())
                .showAdminTransfer(showAdminTransfer)
                .sessionStatus(session.getStatus().name())
                .build();
    }

    private List<User> activeTeam() {
        List<User> team = new ArrayList<>(userRepository.findByRoleAndActifTrue(Role.ADMIN));
        team.addAll(userRepository.findByRoleAndActifTrue(Role.MANAGER));
        return team;
    }

    private User findStaff(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
    }

    /**
     * Recherche une session par token. Lance une exception si non trouvée.
     * Le message ne recopie pas le token (il ne doit pas apparaître dans les journaux).
     */
    private ChatSession findSessionByToken(String token) {
        return chatSessionRepository.findBySessionToken(token)
                .orElseThrow(() -> new IllegalArgumentException(SESSION_NOT_FOUND));
    }

    /**
     * Convertit une entité ChatSession en DTO.
     *
     * @param withMessages inclure l'historique des messages
     * @param forStaff     true pour l'équipe ; false pour le visiteur (pas d'email interne,
     *                     seul le prénom du conseiller est montré)
     */
    ChatSessionResponse toSessionResponse(ChatSession session, boolean withMessages, boolean forStaff) {
        ChatSessionResponse.ChatSessionResponseBuilder builder = ChatSessionResponse.builder()
                .id(session.getId())
                .sessionToken(session.getSessionToken())
                .visitorName(session.getVisitorName())
                .visitorEmail(session.getVisitorEmail())
                .questionCount(session.getQuestionCount())
                .status(session.getStatus().name())
                .unreadCount(forStaff ? session.getUnreadCount() : 0)
                .lastMessagePreview(forStaff ? session.getLastMessagePreview() : null)
                .lastActivityAt(session.getLastActivityAt() != null ? session.getLastActivityAt() : session.getCreatedAt())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt());

        User assigned = session.getAssignedAdmin();
        if (assigned != null) {
            builder.assignedAdminName(forStaff ? assigned.getPrenom() + " " + assigned.getNom() : assigned.getPrenom());
            if (forStaff) {
                builder.assignedAdminEmail(assigned.getEmail());
            }
        }

        if (withMessages) {
            List<ChatMessageResponse> messages = chatMessageRepository
                    .findBySessionOrderByCreatedAtAsc(session)
                    .stream()
                    .map(msg -> ChatMessageResponse.builder()
                            .id(msg.getId())
                            .senderType(msg.getSenderType().name())
                            .content(msg.getContent())
                            .authorName(authorName(msg.getAuthor(), forStaff))
                            .createdAt(msg.getCreatedAt())
                            .build())
                    .collect(Collectors.toList());
            builder.messages(messages);
        }

        return builder.build();
    }

    private static String authorName(User author, boolean forStaff) {
        if (author == null) {
            return null;
        }
        return forStaff ? author.getPrenom() + " " + author.getNom() : author.getPrenom();
    }
}
