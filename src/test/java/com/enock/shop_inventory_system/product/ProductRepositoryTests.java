
package com.enock.shop_inventory_system.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ProductRepositoryTests {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void shouldSaveAndFindProductBySku() {
        Product product = new Product();

        product.setName("Test Keyboard");
        product.setSku("TEST-KEYBOARD-900");
        product.setPrice(new BigDecimal("850.00"));
        product.setQuantity(10);
        product.setCategory("Accessories");

        Product savedProduct = productRepository.saveAndFlush(product);

        var foundProduct = productRepository.findBySku(
                "TEST-KEYBOARD-900"
        );

        assertThat(foundProduct).isPresent();
        assertThat(foundProduct.get().getId())
                .isEqualTo(savedProduct.getId());
        assertThat(foundProduct.get().getName())
                .isEqualTo("Test Keyboard");
        assertThat(foundProduct.get().getPrice())
                .isEqualByComparingTo("850.00");
    }
}
