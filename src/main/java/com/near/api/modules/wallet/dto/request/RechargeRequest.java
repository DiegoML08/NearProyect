package com.near.api.modules.wallet.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RechargeRequest {

    @NotNull(message = "La cantidad de Nears es obligatoria")
    @Min(value = 10, message = "La recarga mínima es de 10 Nears")
    private Integer nearsAmount;

    private String paymentGateway; // "stripe", "paypal", "mercadopago", etc.
    
    private String paymentToken; // Token del pago procesado en frontend
    
    private String externalTransactionId; // ID de transacción externa
}
