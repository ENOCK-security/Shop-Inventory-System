package com.enock.shop_inventory_system.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public record SimulatePaymentRequest(
            @NotNull(message = "The success field is required")
            Boolean success) {
    }

    @PostMapping("/simulate/{paymentReference}")
    public ResponseEntity<PaymentService.PaymentResult> simulatePayment(
            @PathVariable String paymentReference,
            @Valid @RequestBody SimulatePaymentRequest request) {

        PaymentService.PaymentResult result =
                paymentService.processSimulatedResult(
                        paymentReference,
                        request.success());

        return ResponseEntity.ok(result);
    }
}
