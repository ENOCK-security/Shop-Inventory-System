package com.enock.shop_inventory_system.order;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOrderRepository
        extends JpaRepository<CustomerOrder, Long> {
}
