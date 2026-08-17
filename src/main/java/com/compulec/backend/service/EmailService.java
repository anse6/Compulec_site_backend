package com.compulec.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Service d'envoi d'e-mails HTML responsives pour COMPULEC TECHNOVATION.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    /**
     * Envoie un e-mail HTML responsive.
     */
    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom("no-reply@compulec.com");

            mailSender.send(message);
            log.info("E-mail envoyé avec succès à : {}", to);
        } catch (MessagingException e) {
            log.error("Erreur lors de l'envoi de l'e-mail à {} : {}", to, e.getMessage());
            throw new RuntimeException("Échec de l'envoi de l'e-mail : " + e.getMessage());
        }
    }

    /**
     * Envoie l'e-mail de bienvenue lors de la création d'un compte.
     */
    public void sendWelcomeEmail(String toEmail, String nom, String prenom, String rawPassword, String role) {
        String subject = "Bienvenue chez COMPULEC - Votre compte est prêt";
        
        String htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Bienvenue chez COMPULEC</title>
            <style>
                body {
                    margin: 0; padding: 0; width: 100%% !important; -webkit-text-size-adjust: 100%%; -ms-text-size-adjust: 100%%;
                    background-color: #f8fafc; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                }
                .wrapper {
                    background-color: #f8fafc; width: 100%%; padding: 40px 10px;
                }
                .container {
                    max-width: 600px; margin: 0 auto; background-color: #ffffff;
                    border-radius: 12px; overflow: hidden;
                    box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
                }
                .header {
                    background: linear-gradient(135deg, #0f172a 0%%, #1e293b 100%%);
                    padding: 40px 30px; text-align: center; border-bottom: 4px solid #2563eb;
                }
                .logo-title {
                    color: #ffffff; font-size: 32px; font-weight: 800; letter-spacing: 2px; margin: 0;
                }
                .logo-subtitle {
                    color: #94a3b8; font-size: 12px; font-weight: 600; letter-spacing: 5px; margin: 5px 0 0 0; text-transform: uppercase;
                }
                .content {
                    padding: 40px 30px; color: #334155; line-height: 1.6;
                }
                .greeting {
                    font-size: 20px; font-weight: 700; color: #0f172a; margin-top: 0;
                }
                .credentials-box {
                    background-color: #f1f5f9; border-left: 4px solid #2563eb;
                    padding: 20px; border-radius: 0 8px 8px 0; margin: 25px 0;
                }
                .credentials-title {
                    font-weight: 700; margin-bottom: 10px; color: #0f172a;
                }
                .credential-item {
                    margin: 5px 0; font-size: 15px;
                }
                .button {
                    display: inline-block; padding: 14px 30px; background-color: #2563eb;
                    color: #ffffff !important; text-decoration: none; font-weight: 600;
                    border-radius: 8px; margin: 20px 0; text-align: center;
                }
                .footer {
                    background-color: #f1f5f9; padding: 25px 30px; text-align: center;
                    border-top: 1px solid #e2e8f0; color: #64748b; font-size: 13px;
                }
                .footer-brand {
                    font-weight: 700; color: #334155; margin-bottom: 5px;
                }
            </style>
        </head>
        <body>
            <div class="wrapper">
                <div class="container">
                    <div class="header">
                        <div class="logo-title">COMPULEC</div>
                        <div class="logo-subtitle">TECHNOVATION</div>
                    </div>
                    <div class="content">
                        <p class="greeting">Bonjour %s %s,</p>
                        <p>Nous avons le plaisir de vous informer que votre compte d'accès à la plateforme **COMPULEC** a été créé avec succès en tant que <strong>%s</strong>.</p>
                        
                        <div class="credentials-box">
                            <div class="credentials-title">🔑 Vos identifiants de connexion :</div>
                            <div class="credential-item"><strong>Email :</strong> %s</div>
                            <div class="credential-item"><strong>Mot de passe :</strong> %s</div>
                        </div>

                        <p>Pour des raisons de sécurité, nous vous recommandons vivement de changer votre mot de passe dès votre première connexion.</p>

                        <div style="text-align: center;">
                            <a href="http://localhost:8080/api/swagger-ui.html" class="button">Accéder à la plateforme</a>
                        </div>
                    </div>
                    <div class="footer">
                        <div class="footer-brand">COMPULEC TECHNOVATION</div>
                        <p>&copy; 2026 COMPULEC. Tous droits réservés.</p>
                    </div>
                </div>
            </div>
        </body>
        </html>
        """.formatted(prenom, nom, role, toEmail, rawPassword);

        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    /**
     * Envoie l'e-mail contenant le code de réinitialisation de mot de passe.
     */
    public void sendResetPasswordCodeEmail(String toEmail, String nom, String prenom, String code) {
        String subject = "Code de réinitialisation - COMPULEC";

        String htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Réinitialisation de mot de passe</title>
            <style>
                body {
                    margin: 0; padding: 0; width: 100%% !important; -webkit-text-size-adjust: 100%%; -ms-text-size-adjust: 100%%;
                    background-color: #f8fafc; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                }
                .wrapper {
                    background-color: #f8fafc; width: 100%%; padding: 40px 10px;
                }
                .container {
                    max-width: 600px; margin: 0 auto; background-color: #ffffff;
                    border-radius: 12px; overflow: hidden;
                    box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
                }
                .header {
                    background: linear-gradient(135deg, #0f172a 0%%, #1e293b 100%%);
                    padding: 40px 30px; text-align: center; border-bottom: 4px solid #2563eb;
                }
                .logo-title {
                    color: #ffffff; font-size: 32px; font-weight: 800; letter-spacing: 2px; margin: 0;
                }
                .logo-subtitle {
                    color: #94a3b8; font-size: 12px; font-weight: 600; letter-spacing: 5px; margin: 5px 0 0 0; text-transform: uppercase;
                }
                .content {
                    padding: 40px 30px; color: #334155; line-height: 1.6; text-align: center;
                }
                .greeting {
                    font-size: 20px; font-weight: 700; color: #0f172a; margin-top: 0; text-align: left;
                }
                .code-box {
                    font-size: 36px; font-weight: 800; letter-spacing: 8px; color: #2563eb;
                    background-color: #f1f5f9; padding: 20px; border-radius: 8px;
                    display: inline-block; margin: 25px 0; border: 1px dashed #cbd5e1;
                }
                .notice {
                    font-size: 14px; color: #64748b; margin-top: 20px; text-align: left;
                }
                .footer {
                    background-color: #f1f5f9; padding: 25px 30px; text-align: center;
                    border-top: 1px solid #e2e8f0; color: #64748b; font-size: 13px;
                }
                .footer-brand {
                    font-weight: 700; color: #334155; margin-bottom: 5px;
                }
            </style>
        </head>
        <body>
            <div class="wrapper">
                <div class="container">
                    <div class="header">
                        <div class="logo-title">COMPULEC</div>
                        <div class="logo-subtitle">TECHNOVATION</div>
                    </div>
                    <div class="content">
                        <p class="greeting">Bonjour %s %s,</p>
                        <p>Nous avons reçu une demande de réinitialisation de mot de passe pour votre compte. Veuillez utiliser le code de vérification ci-dessous pour continuer :</p>
                        
                        <div class="code-box">%s</div>

                        <p>Ce code est valable pendant 15 minutes.</p>
                        
                        <p class="notice">Si vous n'avez pas demandé cette réinitialisation, vous pouvez ignorer cet e-mail en toute sécurité. Votre mot de passe restera inchangé.</p>
                    </div>
                    <div class="footer">
                        <div class="footer-brand">COMPULEC TECHNOVATION</div>
                        <p>&copy; 2026 COMPULEC. Tous droits réservés.</p>
                    </div>
                </div>
            </div>
        </body>
        </html>
        """.formatted(prenom, nom, code);

        sendHtmlEmail(toEmail, subject, htmlContent);
    }
}
