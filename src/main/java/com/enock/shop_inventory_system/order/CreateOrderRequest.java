package com.enock.shop_inventory_system.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record CreateOrderRequest(

    @NotBlank(message = "Phone number is required")
    @Pattern(
        regexp = "^(?:\\+254|254|0)7\\d{8}$",
        message = "Enter a valid Kenyan mobile number, e.g. 0712345678 or +254712345678"
    )
    String phoneNumber,

    @NotEmpty(message = "At least one order item is required")
    List<@Valid OrderLineRequest> items

) {

    public record OrderLineRequest(

        @Positive(message = "Product ID must be positive")
        Long productId,

        @Positive(message = "Quantity must be at least 1")
        Integer quantity

    ) {
    }
}
