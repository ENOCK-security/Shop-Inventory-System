package com.enock.shop_inventory_system;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdminController.class, CsrfController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "app.security.admin.username=testadmin",
    "app.security.admin.password=test-password-for-tests"
})
class SecurityAuthenticationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldLoginAdministratorAndEstablishSession() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "testadmin")
                .param("password", "test-password-for-tests")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andReturn();

        HttpSession session = loginResult.getRequest().getSession(false);
        assertNotNull(session, "Successful login should establish a session");

        mockMvc.perform(get("/api/admin/auth-check")
                .session((MockHttpSession) session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void shouldRejectIncorrectAdministratorCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "testadmin")
                .param("password", "incorrect-password")
                .with(csrf()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void shouldRejectAdministratorLoginWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "testadmin")
                .param("password", "test-password-for-tests"))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectAnonymousAdministratorAccess() throws Exception {
        mockMvc.perform(get("/api/admin/auth-check"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void shouldRejectOrderCreationWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"0712345678\",\"items\":[]}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldLogoutAdministrator() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "testadmin")
                .param("password", "test-password-for-tests")
                .with(csrf()))
            .andExpect(status().isOk())
            .andReturn();

        MockHttpSession session =
            (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session, "Successful login should establish a session");

        mockMvc.perform(post("/api/auth/logout")
                .session(session)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false));

        org.junit.jupiter.api.Assertions.assertTrue(
            session.isInvalid(),
            "Logout should invalidate the administrator session"
        );

        org.junit.jupiter.api.Assertions.assertTrue(
            session.isInvalid(),
            "Logout should invalidate the administrator session"
        );
    }
}