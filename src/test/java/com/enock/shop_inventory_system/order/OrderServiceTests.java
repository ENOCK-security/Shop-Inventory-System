package com.enock.shop_inventory_system.order;

import com.enock.shop_inventory_system.payment.Payment;
import com.enock.shop_inventory_system.payment.PaymentRepository;
import com.enock.shop_inventory_system.product.Product;
import com.enock.shop_inventory_system.product.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTests {

    @Mock
    private CustomerOrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ProductRepository productRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                orderItemRepository,
                paymentRepository,
                productRepository);
    }

    private Product createProduct(
            Long id, String name, String sku,
            String price, int quantity) {

        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setSku(sku);
        product.setPrice(new BigDecimal(price));
        product.setQuantity(quantity);
        product.setCategory("Electronics");
        return product;
    }

    private CreateOrderRequest createRequest(
            String phone, Long productId, int quantity) {

        return new CreateOrderRequest(
                phone,
                List.of(new CreateOrderRequest.OrderLineRequest(
                        productId, quantity)));
    }

    private void setEntityId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private void prepareOrderSave() throws Exception {
        when(orderRepository.save(any(CustomerOrder.class)))
                .thenAnswer(invocation -> {
                    CustomerOrder order = invocation.getArgument(0);
                    setEntityId(order, 1L);
                    return order;
                });
    }

    @Test
    void createsPendingOrderAndPaymentUsingDatabasePrice() throws Exception {
        Product product = createProduct(
                1L, "Wireless Mouse", "MOUSE-001", "1200.00", 10);

        when(productRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(product));

        prepareOrderSave();
        when(orderItemRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderService.OrderCreationResult result =
                orderService.createOrder(createRequest(
                        "0712345678", 1L, 2));

        assertEquals(1L, result.orderId());
        assertEquals(new BigDecimal("2400.00"), result.totalAmount());
        assertEquals("254712345678", result.phoneNumber());
        assertEquals(
                CustomerOrder.OrderStatus.PENDING_PAYMENT,
                result.orderStatus());
        assertEquals(
                Payment.PaymentStatus.PENDING,
                result.paymentStatus());
        assertTrue(result.paymentReference().startsWith("PAY-"));

        ArgumentCaptor<CustomerOrder> orderCaptor =
                ArgumentCaptor.forClass(CustomerOrder.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(
                CustomerOrder.OrderStatus.PENDING_PAYMENT,
                orderCaptor.getValue().getStatus());

        ArgumentCaptor<List<OrderItem>> itemsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(itemsCaptor.capture());

        List<OrderItem> savedItems = itemsCaptor.getValue();
        assertEquals(1, savedItems.size());
        assertEquals(2, savedItems.get(0).getQuantity());
        assertEquals("Wireless Mouse", savedItems.get(0).getProductName());
        assertEquals(
                new BigDecimal("1200.00"),
                savedItems.get(0).getUnitPrice());

        ArgumentCaptor<Payment> paymentCaptor =
                ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertEquals(
                Payment.PaymentStatus.PENDING,
                paymentCaptor.getValue().getStatus());
        assertEquals(
                new BigDecimal("2400.00"),
                paymentCaptor.getValue().getAmount());
        assertEquals(
                "254712345678",
                paymentCaptor.getValue().getPhoneNumber());

        // Creating an order must not deduct stock.
        assertEquals(10, product.getQuantity());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void combinesDuplicateProductLinesIntoOneOrderItem() throws Exception {
        Product product = createProduct(
                1L, "Keyboard", "KEY-001", "800.00", 10);

        when(productRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(product));

        prepareOrderSave();
        when(orderItemRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateOrderRequest request = new CreateOrderRequest(
                "254712345678",
                List.of(
                        new CreateOrderRequest.OrderLineRequest(1L, 2),
                        new CreateOrderRequest.OrderLineRequest(1L, 3)));

        OrderService.OrderCreationResult result =
                orderService.createOrder(request);

        assertEquals(new BigDecimal("4000.00"), result.totalAmount());

        ArgumentCaptor<List<OrderItem>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(captor.capture());

        assertEquals(1, captor.getValue().size());
        assertEquals(5, captor.getValue().get(0).getQuantity());
        verify(productRepository, times(1)).findByIdForUpdate(1L);
        assertEquals(10, product.getQuantity());
    }

    @Test
    void rejectsUnknownProduct() {
        when(productRepository.findByIdForUpdate(99L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(
                        createRequest("0712345678", 99L, 1)));

        assertEquals(404, exception.getStatusCode().value());
        verify(orderRepository, never()).save(any(CustomerOrder.class));
        verify(orderItemRepository, never()).saveAll(anyList());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void rejectsInsufficientStockWithoutCreatingOrder() {
        Product product = createProduct(
                1L, "Mouse", "MOUSE-001", "1200.00", 2);

        when(productRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(product));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(
                        createRequest("0712345678", 1L, 3)));

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(2, product.getQuantity());
        verify(orderRepository, never()).save(any(CustomerOrder.class));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void rejectsInvalidPhoneNumber() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(
                        createRequest("12345", 1L, 1)));

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(
                productRepository,
                orderRepository,
                orderItemRepository,
                paymentRepository);
    }

    @Test
    void rejectsNullRequest() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(null));

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(
                productRepository,
                orderRepository,
                orderItemRepository,
                paymentRepository);
    }

    @Test
    void rejectsInvalidQuantityWithoutCreatingOrder() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(
                        createRequest("0712345678", 1L, 0)));

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(
                productRepository,
                orderRepository,
                orderItemRepository,
                paymentRepository);
    }

    @Test
    void rejectsCombinedQuantityOverflow() {
        CreateOrderRequest request = new CreateOrderRequest(
                "0712345678",
                List.of(
                        new CreateOrderRequest.OrderLineRequest(
                                1L, Integer.MAX_VALUE),
                        new CreateOrderRequest.OrderLineRequest(1L, 1)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(request));

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(
                productRepository,
                orderRepository,
                orderItemRepository,
                paymentRepository);
    }
}
