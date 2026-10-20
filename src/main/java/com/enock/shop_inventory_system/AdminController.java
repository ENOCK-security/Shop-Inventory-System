package com.enock.shop_inventory_system;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {

    @GetMapping("/api/admin/auth-check")
    public Map<String, Object> checkAuthentication() {
        return Map.of(
            "authenticated", true,
            "message", "Administrator authentication successful",
            "role", "ADMIN"
        );
    }
}
