package com.near.api.modules.wallet.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class TransferRequest {

    @NotNull(message = "El ID del destinatario es obligatorio")
    private UUID recipientUserId;

    @NotNull(message = "La cantidad de Nears es obligatoria")
    @Min(value = 1, message = "La transferencia mínima es de 1 Near")
    private Integer nearsAmount;

    private String description;
}
