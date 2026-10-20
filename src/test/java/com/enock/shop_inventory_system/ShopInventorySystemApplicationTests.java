package com.enock.shop_inventory_system;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "app.security.admin.username=testadmin",
    "app.security.admin.password=TestOnlyPassword123!"
})
class ShopInventorySystemApplicationTests {

    @Test
    void contextLoads() {
    }

}
