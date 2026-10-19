
package com.enock.shop_inventory_system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> healthCheck() {
        return Map.of(
            "status", "UP",
            "message", "Shop Inventory API is running"
        );
    }
}
