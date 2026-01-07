package com.near.api.modules.wallet.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WithdrawalRequest {

    @NotNull(message = "La cantidad de Nears es obligatoria")
    @Min(value = 50, message = "El retiro mínimo es de 50 Nears")
    private Integer nearsAmount;

    @NotBlank(message = "El método de retiro es obligatorio")
    private String withdrawalMethod; // "bank_transfer", "paypal", "yape", etc.

    // Datos bancarios (opcionales según método)
    private String bankName;
    private String accountNumber;
    private String accountHolderName;
    
    // Para billeteras digitales
    private String phoneNumber;
    private String email;
}
