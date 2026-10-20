package com.enock.shop_inventory_system.payment;

import com.enock.shop_inventory_system.order.CustomerOrder;
import com.enock.shop_inventory_system.order.CustomerOrderRepository;
import com.enock.shop_inventory_system.order.OrderItem;
import com.enock.shop_inventory_system.order.OrderItemRepository;
import com.enock.shop_inventory_system.product.Product;
import com.enock.shop_inventory_system.product.ProductRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final CustomerOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    public PaymentService(
            PaymentRepository paymentRepository,
            CustomerOrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
    }

    public record PaymentResult(
            String paymentReference,
            Payment.PaymentStatus paymentStatus,
            Long orderId,
            CustomerOrder.OrderStatus orderStatus,
            String message) {
    }

    @Transactional
    public PaymentResult processSimulatedResult(
            String paymentReference,
            boolean successful) {

        if (paymentReference == null
                || paymentReference.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment reference is required");
        }

        Payment payment = paymentRepository
                .findByPaymentReferenceForUpdate(
                        paymentReference.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found"));

        CustomerOrder order = payment.getOrder();

        // Repeated results must not process a payment twice.
        if (payment.getStatus() == Payment.PaymentStatus.SUCCESS) {
            if (!successful) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A successful payment cannot be changed to failed");
            }

            return toResult(
                    payment,
                    order,
                    "Payment was already successful; no additional stock deducted.");
        }

        if (payment.getStatus() == Payment.PaymentStatus.FAILED) {
            if (successful) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A failed payment cannot be changed to successful");
            }

            return toResult(
                    payment,
                    order,
                    "Payment was already failed; no stock was deducted.");
        }

        if (order.getStatus()
                != CustomerOrder.OrderStatus.PENDING_PAYMENT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The order is not awaiting payment");
        }

        // A failed simulated payment never changes stock.
        if (!successful) {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            order.setStatus(
                    CustomerOrder.OrderStatus.PAYMENT_FAILED);

            paymentRepository.save(payment);
            orderRepository.save(order);

            return toResult(
                    payment,
                    order,
                    "Simulated payment failed; stock was not changed.");
        }

        List<OrderItem> orderItems =
                orderItemRepository.findByOrderId(order.getId());

        if (orderItems.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot complete an order with no items");
        }

        // Aggregate quantities and lock products in ID order.
        // This also protects against duplicate product lines.
        Map<Long, Integer> requestedQuantities = new TreeMap<>();

        try {
            for (OrderItem item : orderItems) {
                if (item.getProduct() == null
                        || item.getProduct().getId() == null
                        || item.getQuantity() <= 0) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "The order contains invalid items");
                }

                requestedQuantities.merge(
                        item.getProduct().getId(),
                        item.getQuantity(),
                        Math::addExact);
            }
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The combined order quantity is too large");
        }

        Map<Long, Product> lockedProducts = new TreeMap<>();

        // Lock and check every product before changing any stock.
        for (Map.Entry<Long, Integer> entry
                : requestedQuantities.entrySet()) {

            Product product = productRepository
                    .findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "An ordered product no longer exists"));

            if (product.getQuantity() < entry.getValue()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Insufficient stock for product: "
                                + product.getName());
            }

            lockedProducts.put(product.getId(), product);
        }

        // All stock checks passed. Apply the changes.
        for (Map.Entry<Long, Integer> entry
                : requestedQuantities.entrySet()) {

            Product product = lockedProducts.get(entry.getKey());

            product.setQuantity(
                    product.getQuantity() - entry.getValue());
        }

        productRepository.saveAll(lockedProducts.values());

        payment.setStatus(Payment.PaymentStatus.SUCCESS);
        order.setStatus(CustomerOrder.OrderStatus.PAID);

        paymentRepository.save(payment);
        orderRepository.save(order);

        return toResult(
                payment,
                order,
                "Simulated payment successful; order paid and stock deducted.");
    }

    private PaymentResult toResult(
            Payment payment,
            CustomerOrder order,
            String message) {

        return new PaymentResult(
                payment.getPaymentReference(),
                payment.getStatus(),
                order.getId(),
                order.getStatus(),
                message);
    }
}
