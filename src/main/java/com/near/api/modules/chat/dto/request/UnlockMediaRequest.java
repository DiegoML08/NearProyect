package com.near.api.modules.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnlockMediaRequest {

    @NotBlank(message = "El ID del mensaje es obligatorio")
    private String messageId;
}
