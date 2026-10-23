package com.compulec.backend.chat;

import com.compulec.backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Maintenance automatique du chat (réglages dans application.properties, préfixe app.chat.*) :
 * - relance de l'équipe quand une demande de conseiller attend trop longtemps ;
 * - fermeture des conversations inactives ;
 * - suppression des conversations terminées au-delà de la durée de conservation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatMaintenanceJobs {

    private final ChatService chatService;

    @Value("${app.chat.maintenance.enabled:true}")
    private boolean enabled;

    @Value("${app.chat.waiting-reminder-minutes:15}")
    private long waitingReminderMinutes;

    @Value("${app.chat.close-bot-after-hours:24}")
    private long closeBotAfterHours;

    @Value("${app.chat.close-team-after-hours:48}")
    private long closeTeamAfterHours;

    @Value("${app.chat.retention-days:365}")
    private long retentionDays;

    /** Toutes les 5 minutes : relances et fermetures. */
    @Scheduled(fixedDelayString = "${app.chat.maintenance.interval-ms:300000}", initialDelay = 60_000)
    public void everyFewMinutes() {
        if (!enabled) {
            return;
        }
        try {
            chatService.remindUnansweredTransfers(Duration.ofMinutes(waitingReminderMinutes));
            chatService.closeInactive(Duration.ofHours(closeBotAfterHours), Duration.ofHours(closeTeamAfterHours));
        } catch (Exception e) {
            log.error("Maintenance du chat en échec : {}", e.getMessage(), e);
        }
    }

    /** Chaque nuit à 3 h 30 : purge des anciennes conversations. */
    @Scheduled(cron = "${app.chat.purge-cron:0 30 3 * * *}")
    public void nightlyPurge() {
        if (!enabled) {
            return;
        }
        try {
            chatService.purgeClosedOlderThan(Duration.ofDays(retentionDays));
        } catch (Exception e) {
            log.error("Purge des conversations en échec : {}", e.getMessage(), e);
        }
    }
}
