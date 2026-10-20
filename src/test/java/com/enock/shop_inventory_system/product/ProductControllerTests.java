
package com.enock.shop_inventory_system.product;

import com.enock.shop_inventory_system.SecurityConfig;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "app.security.admin.username=testadmin",
        "app.security.admin.password=test-password-for-tests"
})
class ProductControllerTests {

    private static final String ADMIN_USERNAME = "testadmin";
    private static final String ADMIN_PASSWORD = "test-password-for-tests";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductRepository productRepository;

    private Product createTestProduct(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setName("Test Keyboard");
        product.setSku("TEST-KEYBOARD-001");
        product.setPrice(new BigDecimal("850.00"));
        product.setQuantity(10);
        product.setCategory("Accessories");
        return product;
    }

    @Test
    void shouldCreateProduct() throws Exception {
        when(productRepository.findBySku("TEST-KEYBOARD-001"))
                .thenReturn(Optional.empty());

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> {
                    Product product = invocation.getArgument(0);
                    product.setId(1L);
                    return product;
                });

        mockMvc.perform(post("/api/products")
                        .with(user(ADMIN_USERNAME).roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createTestProduct(null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Keyboard"))
                .andExpect(jsonPath("$.sku").value("TEST-KEYBOARD-001"));
    }

    @Test
    void shouldRejectUnauthenticatedProductCreation() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createTestProduct(null))))
                .andExpect(status().isUnauthorized());

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void shouldRejectProductCreationWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(user(ADMIN_USERNAME).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createTestProduct(null))))
                .andExpect(status().isForbidden());

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void shouldRejectDuplicateSku() throws Exception {
        when(productRepository.findBySku("TEST-KEYBOARD-001"))
                .thenReturn(Optional.of(createTestProduct(1L)));

        mockMvc.perform(post("/api/products")
                        .with(user(ADMIN_USERNAME).roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                createTestProduct(null))))
                .andExpect(status().isConflict());

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void shouldReturnProductById() throws Exception {
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(createTestProduct(1L)));

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Keyboard"));
    }

    @Test
    void shouldReturnNotFoundWhenProductDoesNotExist() throws Exception {
        when(productRepository.findById(999L))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnAllProducts() throws Exception {
        when(productRepository.findAll())
                .thenReturn(List.of(createTestProduct(1L)));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Test Keyboard"));
    }

    @Test
    void shouldDeleteProduct() throws Exception {
        when(productRepository.existsById(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/products/1")
                        .with(user(ADMIN_USERNAME).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(productRepository).deleteById(1L);
    }

    @Test
    void shouldRejectUnauthenticatedProductDeletion() throws Exception {
        mockMvc.perform(delete("/api/products/1")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(productRepository, never()).deleteById(anyLong());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingProduct()
            throws Exception {
        when(productRepository.existsById(999L)).thenReturn(false);

        mockMvc.perform(delete("/api/products/999")
                        .with(user(ADMIN_USERNAME).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(productRepository, never()).deleteById(999L);
    }

    @Test
    void shouldReturnProductBySku() throws Exception {
        when(productRepository.findBySku("TEST-KEYBOARD-001"))
                .thenReturn(Optional.of(createTestProduct(1L)));

        mockMvc.perform(get("/api/products/sku/TEST-KEYBOARD-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sku").value("TEST-KEYBOARD-001"));
    }

    @Test
    void shouldUpdateProduct() throws Exception {
        Product existingProduct = createTestProduct(1L);

        Product updatedProduct = createTestProduct(null);
        updatedProduct.setName("Updated Keyboard");
        updatedProduct.setPrice(new BigDecimal("950.00"));

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(existingProduct));

        when(productRepository.findBySku("TEST-KEYBOARD-001"))
                .thenReturn(Optional.of(existingProduct));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/products/1")
                        .with(user(ADMIN_USERNAME).roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedProduct)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Keyboard"))
                .andExpect(jsonPath("$.price").value(950.00));
    }

    @Test
    void shouldRejectUnauthenticatedProductUpdate() throws Exception {
        Product updatedProduct = createTestProduct(null);
        updatedProduct.setName("Unauthorized Update");

        mockMvc.perform(put("/api/products/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedProduct)))
                .andExpect(status().isUnauthorized());

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void shouldRejectNegativeQuantity() throws Exception {
        Product invalidProduct = createTestProduct(null);
        invalidProduct.setQuantity(-1);

        mockMvc.perform(post("/api/products")
                        .with(user(ADMIN_USERNAME).roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidProduct)))
                .andExpect(status().isBadRequest());

        verify(productRepository, never()).save(any(Product.class));
    }
}
