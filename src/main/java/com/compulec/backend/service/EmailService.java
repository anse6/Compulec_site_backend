package com.compulec.backend.service;

import com.compulec.backend.entity.ChatMessage;
import com.compulec.backend.entity.ChatSession;
import com.compulec.backend.entity.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service d'envoi d'e-mails HTML responsives pour COMPULEC TECHNOVATION.
 *
 * Règles de sécurité :
 *  - aucun e-mail ne contient de mot de passe ni de code d'activation ;
 *  - les sujets restent neutres (rien d'exploitable dans un aperçu de notification) ;
 *  - toute donnée variable est échappée (HtmlUtils.htmlEscape) avant insertion dans le HTML.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");
    private static final DateTimeFormatter TRANSCRIPT_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // =========================================================================
    // COMPTES : INVITATION, ACTIVATION, MOT DE PASSE
    // =========================================================================

    /**
     * Invitation d'un manager : lien à usage unique, sans mot de passe ni code.
     */
    public void sendInvitationEmail(User user, String activationLink, Duration validity) {
        String body = """
                <p class="greeting">Bonjour %s,</p>
                <p>Un administrateur de <strong>COMPULEC</strong> vous a créé un espace sur la plateforme d'administration.</p>
                <p>Pour l'activer, cliquez sur le bouton ci-dessous puis choisissez <strong>vous-même</strong> votre mot de passe.</p>
                %s
                <div class="box">
                    <div class="box-title">🔐 Vous aurez besoin d'un code d'activation</div>
                    Votre administrateur vous communiquera un <strong>code à 6 chiffres</strong> de vive voix,
                    par téléphone ou par message. Ce code n'est jamais envoyé par e-mail.
                </div>
                <p class="muted">Ce lien expire dans %d heures et ne fonctionne qu'une seule fois.<br>
                Vous n'attendiez pas cet e-mail ? Ignorez-le simplement : aucun compte ne sera activé.</p>
                """.formatted(esc(user.getPrenom()), button("Activer mon compte", activationLink), validity.toHours());

        send(user.getEmail(), "COMPULEC – Activation de votre espace", "Activation de votre espace", body);
    }

    /**
     * Confirmation envoyée au manager après activation (alerte si ce n'est pas lui).
     */
    public void sendAccountActivatedEmail(User user) {
        String body = """
                <p class="greeting">Bonjour %s,</p>
                <p>Votre compte COMPULEC a été activé le <strong>%s</strong>. Vous pouvez désormais vous connecter.</p>
                %s
                %s
                """.formatted(
                esc(user.getPrenom()),
                DATE_TIME.format(LocalDateTime.now()),
                button("Se connecter", frontendUrl + "/login"),
                warning("Ce n'est pas vous ? Contactez immédiatement votre administrateur : il pourra désactiver le compte en un clic."));

        send(user.getEmail(), "COMPULEC – Votre compte est activé", "Compte activé", body);
    }

    /**
     * Notification aux administrateurs : un manager vient d'activer son compte.
     */
    public void sendAccountActivatedAdminNotice(User admin, User activatedUser) {
        String body = """
                <p class="greeting">Bonjour %s,</p>
                <p>Le compte de <strong>%s %s</strong> (%s) vient d'être activé le <strong>%s</strong>.</p>
                %s
                %s
                """.formatted(
                esc(admin.getPrenom()),
                esc(activatedUser.getPrenom()), esc(activatedUser.getNom()), esc(activatedUser.getEmail()),
                DATE_TIME.format(LocalDateTime.now()),
                warning("Si cette activation ne vient pas de la bonne personne, désactivez ce compte depuis la page Utilisateurs."),
                button("Gérer les utilisateurs", frontendUrl + "/admin/users"));

        send(admin.getEmail(), "COMPULEC – Un compte manager a été activé", "Compte manager activé", body);
    }

    /**
     * Notification aux administrateurs : invitation annulée après trop de codes incorrects.
     */
    public void sendInvitationRevokedAdminNotice(User admin, User invitedUser) {
        String body = """
                <p class="greeting">Bonjour %s,</p>
                <p>L'invitation de <strong>%s %s</strong> (%s) a été <strong>annulée automatiquement</strong>
                après plusieurs codes d'activation incorrects.</p>
                %s
                %s
                """.formatted(
                esc(admin.getPrenom()),
                esc(invitedUser.getPrenom()), esc(invitedUser.getNom()), esc(invitedUser.getEmail()),
                warning("Il peut s'agir d'une erreur de saisie… ou d'une tentative d'intrusion. Vérifiez avec la personne avant de renvoyer une invitation."),
                button("Gérer les utilisateurs", frontendUrl + "/admin/users"));

        send(admin.getEmail(), "COMPULEC – Invitation annulée (sécurité)", "Invitation annulée", body);
    }

    /**
     * Lien de réinitialisation du mot de passe (usage unique, courte durée).
     */
    public void sendPasswordResetLinkEmail(User user, String resetLink, Duration validity) {
        String body = """
                <p class="greeting">Bonjour %s,</p>
                <p>Nous avons reçu une demande de réinitialisation du mot de passe de votre compte COMPULEC.</p>
                %s
                <p class="muted">Ce lien expire dans %d minutes et ne fonctionne qu'une seule fois.</p>
                %s
                """.formatted(
                esc(user.getPrenom()),
                button("Choisir un nouveau mot de passe", resetLink),
                validity.toMinutes(),
                warning("Vous n'êtes pas à l'origine de cette demande ? Ignorez cet e-mail : votre mot de passe reste inchangé."));

        send(user.getEmail(), "COMPULEC – Réinitialisation du mot de passe", "Réinitialisation du mot de passe", body);
    }

    /**
     * Alerte : le mot de passe vient d'être modifié (toutes les sessions ont été fermées).
     */
    public void sendPasswordChangedEmail(User user) {
        String body = """
                <p class="greeting">Bonjour %s,</p>
                <p>Le mot de passe de votre compte COMPULEC a été modifié le <strong>%s</strong>.
                Par sécurité, toutes vos autres sessions ont été déconnectées.</p>
                %s
                """.formatted(
                esc(user.getPrenom()),
                DATE_TIME.format(LocalDateTime.now()),
                warning("Ce n'est pas vous ? Utilisez immédiatement « Mot de passe oublié » et prévenez votre administrateur."));

        send(user.getEmail(), "COMPULEC – Mot de passe modifié", "Mot de passe modifié", body);
    }

    /**
     * Alerte : compte verrouillé temporairement après trop d'échecs de connexion.
     */
    public void sendAccountLockedEmail(User user, Duration lockDuration) {
        String body = """
                <p class="greeting">Bonjour %s,</p>
                <p>Plusieurs tentatives de connexion avec un mot de passe incorrect ont eu lieu sur votre compte.
                Il est verrouillé pendant <strong>%d minutes</strong>.</p>
                %s
                """.formatted(
                esc(user.getPrenom()),
                lockDuration.toMinutes(),
                warning("Si ce n'était pas vous, quelqu'un essaie peut-être d'accéder à votre compte. "
                        + "Changez votre mot de passe dès que possible et prévenez votre administrateur."));

        send(user.getEmail(), "COMPULEC – Alerte de sécurité sur votre compte", "Alerte de sécurité", body);
    }

    // =========================================================================
    // DOUBLE AUTHENTIFICATION
    // =========================================================================

    /**
     * Alerte : la double authentification vient d'être activée.
     */
    public void sendMfaEnabledEmail(User user) {
        String body = """
                <p class="greeting">Bonjour %s,</p>
                <p>La <strong>double authentification</strong> a été activée sur votre compte COMPULEC le <strong>%s</strong>.
                Chaque connexion demandera désormais un code de votre application d'authentification.</p>
                %s
                """.formatted(
                esc(user.getPrenom()),
                DATE_TIME.format(LocalDateTime.now()),
                warning("Ce n'est pas vous ? Quelqu'un connaît peut-être votre mot de passe : prévenez immédiatement votre administrateur."));

        send(user.getEmail(), "COMPULEC – Double authentification activée", "Double authentification activée", body);
    }

    /**
     * Alerte : un administrateur a réinitialisé la double authentification.
     */
    public void sendMfaResetEmail(User user) {
        String body = """
                <p class="greeting">Bonjour %s,</p>
                <p>Un administrateur a réinitialisé la double authentification de votre compte le <strong>%s</strong>.
                Toutes vos sessions ont été fermées. À votre prochaine connexion, vous devrez la configurer à nouveau.</p>
                %s
                """.formatted(
                esc(user.getPrenom()),
                DATE_TIME.format(LocalDateTime.now()),
                warning("Vous n'avez rien demandé ? Contactez votre administrateur au plus vite."));

        send(user.getEmail(), "COMPULEC – Double authentification réinitialisée", "Double authentification réinitialisée", body);
    }

    /**
     * Alerte : un code de secours a été utilisé.
     */
    public void sendBackupCodeUsedEmail(User user, long remaining) {
        String body = """
                <p class="greeting">Bonjour %s,</p>
                <p>Un <strong>code de secours</strong> a été utilisé pour vous connecter le <strong>%s</strong>.
                Il vous reste <strong>%d</strong> code(s) de secours.</p>
                %s
                """.formatted(
                esc(user.getPrenom()),
                DATE_TIME.format(LocalDateTime.now()),
                remaining,
                warning("Ce n'est pas vous ? Changez votre mot de passe et prévenez votre administrateur. "
                        + "Vous pouvez générer de nouveaux codes dans Paramètres › Sécurité."));

        send(user.getEmail(), "COMPULEC – Code de secours utilisé", "Code de secours utilisé", body);
    }

    // =========================================================================
    // CHAT
    // =========================================================================

    /**
     * Notification à l'admin lorsqu'un visiteur demande à parler à un conseiller.
     */
    public void sendAdminChatNotificationEmail(User admin, ChatSession session) {
        String body = """
                <p class="greeting">Bonjour %s,</p>
                <p>Un visiteur a posé <strong>%d questions</strong> à l'assistant virtuel et souhaite maintenant
                discuter avec un conseiller de <strong>COMPULEC</strong>.</p>
                <div class="box">
                    <div class="box-title">📋 Informations du visiteur</div>
                    <strong>Nom :</strong> %s<br>
                    <strong>Email :</strong> %s<br>
                    <strong>Session :</strong> #%s
                </div>
                %s
                %s
                """.formatted(
                esc(admin.getPrenom()),
                session.getQuestionCount(),
                esc(session.getVisitorName()),
                session.getVisitorEmail() != null ? esc(session.getVisitorEmail()) : "Non renseigné",
                esc(session.getSessionToken().substring(0, 8).toUpperCase()),
                button("💬 Ouvrir le chat", frontendUrl + "/admin/aichat"),
                warning("Le visiteur attend votre réponse. Prenez en charge cette conversation dès que possible."));

        send(admin.getEmail(), "🔔 Un visiteur demande à discuter avec vous – COMPULEC", "Nouvelle demande de chat", body);
    }

    /**
     * Rappel à l'équipe : une demande de conseiller attend toujours une prise en charge.
     */
    public void sendChatWaitingReminderEmail(User member, ChatSession session, Duration waitingFor) {
        String body = """
                <p class="greeting">Bonjour %s,</p>
                <p><strong>%s</strong> attend un conseiller dans le chat depuis plus de <strong>%d minutes</strong>.</p>
                %s
                """.formatted(
                esc(member.getPrenom()),
                esc(session.getVisitorName()),
                waitingFor.toMinutes(),
                button("💬 Prendre en charge", frontendUrl + "/admin/aichat"));

        send(member.getEmail(), "⏰ Un visiteur attend toujours un conseiller – COMPULEC", "Visiteur en attente", body);
    }

    /**
     * Transcription d'une conversation envoyée au visiteur à la clôture.
     */
    public void sendChatTranscriptEmail(ChatSession session, List<ChatMessage> messages) {
        boolean fr = !"en".equals(session.getLanguage());
        StringBuilder lines = new StringBuilder();
        for (ChatMessage m : messages) {
            String who = switch (m.getSenderType()) {
                case VISITOR -> session.getVisitorName();
                case ADMIN -> (m.getAuthor() != null ? m.getAuthor().getPrenom() : (fr ? "Conseiller" : "Advisor")) + " · COMPULEC";
                default -> fr ? "Assistant COMPULEC" : "COMPULEC assistant";
            };
            lines.append("<p style=\"margin:0 0 10px 0;\"><strong>")
                    .append(esc(who))
                    .append("</strong> <span class=\"muted\">")
                    .append(TRANSCRIPT_TIME.format(m.getCreatedAt()))
                    .append("</span><br>")
                    .append(esc(m.getContent()).replace("\n", "<br>"))
                    .append("</p>");
        }
        String body = """
                <p class="greeting">%s %s,</p>
                <p>%s</p>
                <div class="box">%s</div>
                <p class="muted">%s</p>
                """.formatted(
                fr ? "Bonjour" : "Hello",
                esc(session.getVisitorName()),
                fr ? "Merci d'avoir échangé avec l'équipe COMPULEC. Voici la transcription de votre conversation."
                   : "Thank you for chatting with the COMPULEC team. Here is the transcript of your conversation.",
                lines,
                fr ? "Pour toute suite, répondez simplement à cet e-mail ou contactez-nous depuis le site."
                   : "To follow up, simply reply to this email or contact us from the website.");

        send(session.getVisitorEmail(),
                fr ? "COMPULEC – Transcription de votre conversation" : "COMPULEC – Your conversation transcript",
                fr ? "Transcription de conversation" : "Conversation transcript", body);
    }

    // =========================================================================
    // MISE EN PAGE ET ENVOI
    // =========================================================================

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    private static String button(String label, String url) {
        return """
                <div style="text-align:center;">
                    <a href="%s" class="button">%s</a>
                </div>
                """.formatted(esc(url), esc(label));
    }

    private static String warning(String text) {
        return "<div class=\"warning\">⚠️ " + esc(text) + "</div>";
    }

    private void send(String to, String subject, String title, String bodyHtml) {
        sendHtmlEmail(to, subject, layout(title, bodyHtml));
    }

    private static String layout(String title, String bodyHtml) {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>%s</title>
            <style>
                body { margin: 0; padding: 0; width: 100%% !important; background-color: #f8fafc;
                       font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; }
                .wrapper { background-color: #f8fafc; width: 100%%; padding: 40px 10px; }
                .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 12px;
                             overflow: hidden; border: 1px solid #e2e8f0;
                             box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -1px rgba(0,0,0,0.06); }
                .header { background-color: #ffffff; padding: 30px; text-align: center; border-bottom: 3px solid #2563eb; }
                .logo-img { max-height: 80px; width: auto; display: block; margin: 0 auto; }
                .content { padding: 40px 30px; color: #334155; line-height: 1.6; }
                .greeting { font-size: 20px; font-weight: 700; color: #0f172a; margin-top: 0; }
                .box { background-color: #f1f5f9; border-left: 4px solid #2563eb; padding: 18px 20px;
                       border-radius: 0 8px 8px 0; margin: 25px 0; font-size: 15px; }
                .box-title { font-weight: 700; margin-bottom: 8px; color: #0f172a; }
                .button { display: inline-block; padding: 14px 30px; background-color: #2563eb;
                          color: #ffffff !important; text-decoration: none; font-weight: 600;
                          border-radius: 8px; margin: 20px 0; }
                .warning { background-color: #fef3c7; border-left: 4px solid #f59e0b; border-radius: 0 8px 8px 0;
                           padding: 14px 18px; font-size: 14px; color: #92400e; margin-top: 20px; }
                .muted { font-size: 13px; color: #64748b; }
                .footer { background-color: #f1f5f9; padding: 30px 20px; text-align: center;
                          border-top: 1px solid #e2e8f0; color: #64748b; font-size: 13px; }
                .footer-brand { font-weight: 700; color: #334155; margin-bottom: 15px; font-size: 14px; letter-spacing: 1px; }
                .social-icon { display: inline-block; margin: 0 8px; text-decoration: none; }
                .social-icon img { width: 28px; height: 28px; border: none; vertical-align: middle; }
            </style>
        </head>
        <body>
            <div class="wrapper">
                <div class="container">
                    <div class="header">
                        <img src="cid:logo" alt="COMPULEC TECHNOVATION" class="logo-img" />
                    </div>
                    <div class="content">
                        %s
                    </div>
                    <div class="footer">
                        <div class="footer-brand">COMPULEC TECHNOVATION</div>
                        <div>
                            <a href="https://www.facebook.com/share/19B4Vg2Dpi/" class="social-icon" target="_blank">
                                <img src="https://img.icons8.com/color/32/facebook-new.png" alt="Facebook" />
                            </a>
                            <a href="https://www.instagram.com/compulectech?igsh=OXMzNDM5emx1a3Bk" class="social-icon" target="_blank">
                                <img src="https://img.icons8.com/color/32/instagram-new.png" alt="Instagram" />
                            </a>
                            <a href="https://www.tiktok.com/@compulec?_r=1&_t=ZS-98xItroNjFs" class="social-icon" target="_blank">
                                <img src="https://img.icons8.com/color/32/tiktok.png" alt="TikTok" />
                            </a>
                            <a href="https://x.com/CompulecTech" class="social-icon" target="_blank">
                                <img src="https://img.icons8.com/color/32/twitterx--v2.png" alt="X (Twitter)" />
                            </a>
                        </div>
                        <p style="margin: 15px 0 0 0; font-size: 12px; color: #94a3b8;">&copy; 2026 COMPULEC. Tous droits réservés.<br>
                        Nous ne vous demanderons jamais votre mot de passe par e-mail.</p>
                    </div>
                </div>
            </div>
        </body>
        </html>
        """.formatted(esc(title), bodyHtml);
    }

    /**
     * Envoie un e-mail HTML responsive avec intégration du logo en inline (CID).
     */
    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom(senderEmail);

            // Attacher le logo de l'entreprise en ligne (CID: logo) s'il existe dans les ressources
            ClassPathResource logoResource = new ClassPathResource("static/images/logo.png");
            if (logoResource.exists()) {
                helper.addInline("logo", logoResource);
            } else {
                log.warn("Le logo de l'entreprise n'a pas été trouvé dans : src/main/resources/static/images/logo.png");
            }

            mailSender.send(message);
            log.info("E-mail « {} » envoyé", subject);
        } catch (MessagingException e) {
            log.error("Erreur lors de l'envoi de l'e-mail « {} » : {}", subject, e.getMessage());
            throw new RuntimeException("Échec de l'envoi de l'e-mail : " + e.getMessage());
        }
    }
}
