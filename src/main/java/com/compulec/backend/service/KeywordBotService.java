package com.compulec.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


/**
 * Réponses de secours par mots-clés, utilisées quand l'assistant IA est désactivé
 * (pas de clé API) ou indisponible (panne, quota atteint).
 */
@Service
@Slf4j
public class KeywordBotService {

    /**
     * Réponse simple selon quelques mots-clés du message du visiteur.
     *
     * @param userMessage message du visiteur
     * @return réponse textuelle du bot
     */
    public String reply(String userMessage) {
        String lowerMsg = userMessage.toLowerCase();
        
        if (lowerMsg.contains("bonjour") || lowerMsg.contains("salut") || lowerMsg.contains("hello") || lowerMsg.contains("coucou")) {
            return "Bonjour ! Comment puis-je vous aider aujourd'hui avec nos services informatiques ?";
        } 
        
        if (lowerMsg.contains("service") || lowerMsg.contains("prestation") || lowerMsg.contains("faites-vous") || lowerMsg.contains("offrez-vous")) {
            return "COMPULEC propose de nombreux services : développement d'applications sur mesure (web & mobile), maintenance de réseaux, cybersécurité, et conseil en transformation numérique. Souhaitez-vous des détails sur l'un d'entre eux ?";
        } 
        
        if (lowerMsg.contains("prix") || lowerMsg.contains("tarif") || lowerMsg.contains("combien") || lowerMsg.contains("coût")) {
            return "Nos tarifs dépendent de la spécificité et de la complexité de votre projet. Je peux demander à un conseiller de vous recontacter pour vous établir un devis personnalisé.";
        } 
        
        if (lowerMsg.contains("contact") || lowerMsg.contains("téléphone") || lowerMsg.contains("telephone") || lowerMsg.contains("email") || lowerMsg.contains("joindre")) {
            return "Vous pouvez nous joindre directement par email à l'adresse admin@compulec.com ou via la page 'Contact' de notre site web. Un conseiller se fera un plaisir de vous répondre.";
        }
        
        if (lowerMsg.contains("projet") || lowerMsg.contains("portfolio") || lowerMsg.contains("réalisation") || lowerMsg.contains("realisation")) {
            return "Nous avons réalisé de nombreux projets pour des clients variés (applications web, systèmes de gestion, etc.). Vous pouvez consulter la section 'Nos Projets' sur notre site pour voir nos réalisations.";
        }
        
        // Réponse par défaut
        return "C'est une excellente question concernant COMPULEC. En tant qu'assistant virtuel, je peux vous mettre en relation avec un de nos conseillers humains qui saura vous répondre avec plus de précision. (Vous pourrez demander à parler à un admin très bientôt).";
    }
}
