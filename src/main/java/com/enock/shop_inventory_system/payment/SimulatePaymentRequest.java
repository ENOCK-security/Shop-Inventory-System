package com.enock.shop_inventory_system.payment;

import jakarta.validation.constraints.NotNull;

public record SimulatePaymentRequest(

    @NotNull(message = "A payment outcome is required")
    Outcome outcome

) {

    public enum Outcome {
        SUCCESS,
        FAILED
    }
}
