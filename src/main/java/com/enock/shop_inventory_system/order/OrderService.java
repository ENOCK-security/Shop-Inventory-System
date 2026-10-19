package com.enock.shop_inventory_system.order;

import com.enock.shop_inventory_system.payment.Payment;
import com.enock.shop_inventory_system.payment.PaymentRepository;
import com.enock.shop_inventory_system.product.Product;
import com.enock.shop_inventory_system.product.ProductRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class OrderService {

    private final CustomerOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;

    public OrderService(
            CustomerOrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.productRepository = productRepository;
    }

    public record OrderCreationResult(
            Long orderId,
            String paymentReference,
            BigDecimal totalAmount,
            CustomerOrder.OrderStatus orderStatus,
            Payment.PaymentStatus paymentStatus,
            String phoneNumber,
            String message) {
    }

    @Transactional
    public OrderCreationResult createOrder(CreateOrderRequest request) {

        if (request == null
                || request.phoneNumber() == null
                || request.items() == null
                || request.items().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A phone number and at least one order item are required");
        }

        String phoneNumber = normalizePhoneNumber(request.phoneNumber());

        // Combine repeated product IDs and process IDs in sorted order.
        Map<Long, Integer> requestedItems = new TreeMap<>();

        for (CreateOrderRequest.OrderLineRequest line : request.items()) {

            if (line == null
                    || line.productId() == null
                    || line.productId() <= 0
                    || line.quantity() == null
                    || line.quantity() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Every item must have a positive product ID and quantity");
            }

            try {
                requestedItems.merge(
                        line.productId(),
                        line.quantity(),
                        Math::addExact);
            } catch (ArithmeticException exception) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "The combined quantity is too large");
            }
        }

        // Lock each product while checking its current database quantity.
        List<Product> lockedProducts = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (Map.Entry<Long, Integer> entry : requestedItems.entrySet()) {

            Product product = productRepository
                    .findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Product not found: " + entry.getKey()));

            int requestedQuantity = entry.getValue();

            if (product.getQuantity() < requestedQuantity) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Insufficient stock for product: " + product.getName());
            }

            totalAmount = totalAmount.add(
                    product.getPrice().multiply(
                            BigDecimal.valueOf(requestedQuantity)));

            lockedProducts.add(product);
        }

        CustomerOrder order = new CustomerOrder();
        order.setPhoneNumber(phoneNumber);
        order.setTotalAmount(totalAmount);
        order.setStatus(CustomerOrder.OrderStatus.PENDING_PAYMENT);

        order = orderRepository.save(order);

        List<OrderItem> orderItems = new ArrayList<>();

        for (int i = 0; i < lockedProducts.size(); i++) {
            Product product = lockedProducts.get(i);
            int quantity = requestedItems.get(product.getId());

            orderItems.add(new OrderItem(
                    order,
                    product,
                    product.getName(),
                    product.getPrice(),
                    quantity));
        }

        orderItemRepository.saveAll(orderItems);

        Payment payment = new Payment();
        payment.setPaymentReference(
                "PAY-" + UUID.randomUUID().toString().replace("-", ""));
        payment.setOrder(order);
        payment.setAmount(totalAmount);
        payment.setPhoneNumber(phoneNumber);
        payment.setStatus(Payment.PaymentStatus.PENDING);

        paymentRepository.save(payment);

        return new OrderCreationResult(
                order.getId(),
                payment.getPaymentReference(),
                totalAmount,
                order.getStatus(),
                payment.getStatus(),
                phoneNumber,
                "Order created. Simulated payment is pending.");
    }

    private String normalizePhoneNumber(String phoneNumber) {

        String normalized = phoneNumber.trim();

        if (normalized.startsWith("+254")) {
            normalized = normalized.substring(1);
        } else if (normalized.startsWith("0")) {
            normalized = "254" + normalized.substring(1);
        }

        if (!normalized.matches("2547\\d{8}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Enter a valid Kenyan mobile number");
        }

        return normalized;
    }
}
