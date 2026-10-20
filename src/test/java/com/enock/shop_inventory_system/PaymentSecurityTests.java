package com.enock.shop_inventory_system;

import com.enock.shop_inventory_system.payment.PaymentController;
import com.enock.shop_inventory_system.payment.PaymentService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "app.security.admin.username=testadmin",
    "app.security.admin.password=test-password-for-tests",
    "app.payment-simulation.enabled=true"
})
class PaymentSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void shouldRejectAnonymousPaymentSimulation() throws Exception {
        mockMvc.perform(post("/api/payments/simulate/PAY-test")
                .with(csrf())
                .contentType("application/json")
                .content("{\"success\":true}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectPaymentSimulationWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/payments/simulate/PAY-test")
                .with(user("testadmin").roles("ADMIN"))
                .contentType("application/json")
                .content("{\"success\":true}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdministratorThroughSecurityChecks() throws Exception {
        mockMvc.perform(post("/api/payments/simulate/PAY-test")
                .with(user("testadmin").roles("ADMIN"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"success\":true}"))
            .andExpect(status().isOk());
    }
}
