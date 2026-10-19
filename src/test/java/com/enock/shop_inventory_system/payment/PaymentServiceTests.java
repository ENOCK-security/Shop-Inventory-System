package com.enock.shop_inventory_system.payment;

import com.enock.shop_inventory_system.order.CustomerOrder;
import com.enock.shop_inventory_system.order.CustomerOrderRepository;
import com.enock.shop_inventory_system.order.OrderItem;
import com.enock.shop_inventory_system.order.OrderItemRepository;
import com.enock.shop_inventory_system.product.Product;
import com.enock.shop_inventory_system.product.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentServiceTests {

    private PaymentRepository paymentRepository;
    private CustomerOrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private ProductRepository productRepository;
    private PaymentService paymentService;

    private CustomerOrder order;
    private Payment payment;
    private Product product;
    private OrderItem orderItem;

    private void setEntityId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @BeforeEach
    void setUp() throws Exception {
        paymentRepository = mock(PaymentRepository.class);
        orderRepository = mock(CustomerOrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        productRepository = mock(ProductRepository.class);

        paymentService = new PaymentService(
                paymentRepository,
                orderRepository,
                orderItemRepository,
                productRepository);

        order = new CustomerOrder();
        setEntityId(order, 1L);
        order.setPhoneNumber("254712345678");
        order.setTotalAmount(new BigDecimal("1200.00"));
        order.setStatus(CustomerOrder.OrderStatus.PENDING_PAYMENT);

        payment = new Payment();
        payment.setPaymentReference("PAY-TEST-001");
        payment.setOrder(order);
        payment.setAmount(new BigDecimal("1200.00"));
        payment.setPhoneNumber("254712345678");
        payment.setStatus(Payment.PaymentStatus.PENDING);

        product = new Product();
        product.setId(2L);
        product.setName("Wireless Mouse");
        product.setSku("ELEC-MOUSE-002");
        product.setPrice(new BigDecimal("1200.00"));
        product.setQuantity(14);
        product.setCategory("Electronics");

        orderItem = new OrderItem(
                order, product, "Wireless Mouse",
                new BigDecimal("1200.00"), 1);
    }

    @Test
    void successfulPaymentDeductsStockAndMarksOrderPaid() {
        when(paymentRepository.findByPaymentReferenceForUpdate("PAY-TEST-001"))
                .thenReturn(Optional.of(payment));
        when(orderItemRepository.findByOrderId(1L))
                .thenReturn(List.of(orderItem));
        when(productRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(product));

        PaymentService.PaymentResult result =
                paymentService.processSimulatedResult("PAY-TEST-001", true);

        assertEquals(Payment.PaymentStatus.SUCCESS, result.paymentStatus());
        assertEquals(CustomerOrder.OrderStatus.PAID, result.orderStatus());
        assertEquals(13, product.getQuantity());

        verify(productRepository).saveAll(any());
        verify(paymentRepository).save(payment);
        verify(orderRepository).save(order);
    }

    @Test
    void failedPaymentDoesNotDeductStock() {
        when(paymentRepository.findByPaymentReferenceForUpdate("PAY-TEST-001"))
                .thenReturn(Optional.of(payment));

        PaymentService.PaymentResult result =
                paymentService.processSimulatedResult("PAY-TEST-001", false);

        assertEquals(Payment.PaymentStatus.FAILED, result.paymentStatus());
        assertEquals(CustomerOrder.OrderStatus.PAYMENT_FAILED, result.orderStatus());
        assertEquals(14, product.getQuantity());

        verify(productRepository, never()).saveAll(any());
        verify(orderItemRepository, never()).findByOrderId(any());
    }

    @Test
    void repeatedSuccessfulPaymentDoesNotDeductStockAgain() {
        payment.setStatus(Payment.PaymentStatus.SUCCESS);
        order.setStatus(CustomerOrder.OrderStatus.PAID);

        when(paymentRepository.findByPaymentReferenceForUpdate("PAY-TEST-001"))
                .thenReturn(Optional.of(payment));

        PaymentService.PaymentResult result =
                paymentService.processSimulatedResult("PAY-TEST-001", true);

        assertEquals(Payment.PaymentStatus.SUCCESS, result.paymentStatus());
        assertEquals(14, product.getQuantity());

        verify(productRepository, never()).saveAll(any());
        verify(orderItemRepository, never()).findByOrderId(any());
    }

    @Test
    void repeatedFailedPaymentDoesNotChangeStock() {
        payment.setStatus(Payment.PaymentStatus.FAILED);
        order.setStatus(CustomerOrder.OrderStatus.PAYMENT_FAILED);

        when(paymentRepository.findByPaymentReferenceForUpdate("PAY-TEST-001"))
                .thenReturn(Optional.of(payment));

        PaymentService.PaymentResult result =
                paymentService.processSimulatedResult("PAY-TEST-001", false);

        assertEquals(Payment.PaymentStatus.FAILED, result.paymentStatus());
        assertEquals(14, product.getQuantity());

        verify(productRepository, never()).saveAll(any());
        verify(orderItemRepository, never()).findByOrderId(any());
    }

    @Test
    void insufficientStockRejectsPaymentWithoutChangingStock() {
        product.setQuantity(0);

        when(paymentRepository.findByPaymentReferenceForUpdate("PAY-TEST-001"))
                .thenReturn(Optional.of(payment));
        when(orderItemRepository.findByOrderId(1L))
                .thenReturn(List.of(orderItem));
        when(productRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(product));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.processSimulatedResult(
                        "PAY-TEST-001", true));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(0, product.getQuantity());
        assertEquals(Payment.PaymentStatus.PENDING, payment.getStatus());
        assertEquals(
                CustomerOrder.OrderStatus.PENDING_PAYMENT,
                order.getStatus());

        verify(productRepository, never()).saveAll(any());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(orderRepository, never()).save(any(CustomerOrder.class));
    }

    @Test
    void unknownPaymentReferenceReturnsNotFound() {
        when(paymentRepository.findByPaymentReferenceForUpdate("MISSING"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.processSimulatedResult("MISSING", true));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void failedPaymentCannotLaterBecomeSuccessful() {
        payment.setStatus(Payment.PaymentStatus.FAILED);
        order.setStatus(CustomerOrder.OrderStatus.PAYMENT_FAILED);

        when(paymentRepository.findByPaymentReferenceForUpdate("PAY-TEST-001"))
                .thenReturn(Optional.of(payment));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.processSimulatedResult(
                        "PAY-TEST-001", true));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(productRepository, never()).saveAll(any());
    }

    @Test
    void successfulPaymentCannotLaterBecomeFailed() {
        payment.setStatus(Payment.PaymentStatus.SUCCESS);
        order.setStatus(CustomerOrder.OrderStatus.PAID);

        when(paymentRepository.findByPaymentReferenceForUpdate("PAY-TEST-001"))
                .thenReturn(Optional.of(payment));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.processSimulatedResult(
                        "PAY-TEST-001", false));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(productRepository, never()).saveAll(any());
    }
}
