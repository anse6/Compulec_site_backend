package com.compulec.backend.controller;

import com.compulec.backend.dto.response.ApiResponse;
import com.compulec.backend.dto.response.NotificationResponse;
import com.compulec.backend.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@Tag(name = "Notifications", description = "Endpoints pour la gestion des notifications du dashboard")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Obtenir les notifications", description = "Retourne la liste des notifications dynamiques du dashboard")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications() {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getNotifications()));
    }

    @PostMapping("/mark-all-read")
    @Operation(summary = "Marquer tout comme lu", description = "Marque toutes les notifications (ex: messages de contact) comme lues")
    public ResponseEntity<ApiResponse<String>> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok(ApiResponse.success(null, "Toutes les notifications ont été marquées comme lues."));
    }
}
