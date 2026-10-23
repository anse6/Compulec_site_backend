package com.compulec.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour qu'un admin réponde dans une session de chat.
 * Utilisé depuis le dashboard administrateur.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminChatReplyRequest {

    @NotBlank(message = "Le token de session est obligatoire")
    private String sessionToken;

    @NotBlank(message = "Le message est obligatoire")
    @Size(min = 1, max = 2000, message = "Le message doit contenir entre 1 et 2000 caractères")
    private String message;
}
