package com.nimbleways.springboilerplate.controllers;

import com.nimbleways.springboilerplate.entities.Order;
import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.OrderRepository;
import com.nimbleways.springboilerplate.repositories.ProductRepository;
import com.nimbleways.springboilerplate.services.implementations.NotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.assertEquals;

// import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Specify the controller class you want to test
// This indicates to spring boot to only load UsersController into the context
// Which allows a better performance and needs to do less mocks
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class MyControllerIntegrationTests {
        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private NotificationService notificationService;

        @Autowired
        private OrderRepository orderRepository;

        @Autowired
        private ProductRepository productRepository;

        @Test
        public void processOrderShouldReturn() throws Exception {
                List<Product> allProducts = createProducts();
                Set<Product> orderItems = new HashSet<Product>(allProducts);
                Order order = createOrder(orderItems);
                productRepository.saveAll(allProducts);
                order = orderRepository.save(order);
                mockMvc.perform(post("/orders/{orderId}/processOrder", order.getId())
                                .contentType("application/json"))
                                .andExpect(status().isOk());
                Order resultOrder = orderRepository.findById(order.getId()).get();
                assertEquals(resultOrder.getId(), order.getId());
        }

        private static Order createOrder(Set<Product> products) {
                Order order = new Order();
                order.setItems(products);
                return order;
        }

        private static List<Product> createProducts() {
                List<Product> products = new ArrayList<>();
                products.add(new Product(null, 15, 30, "NORMAL", "USB Cable", null, null, null));
                products.add(new Product(null, 10, 0, "NORMAL", "USB Dongle", null, null, null));
                products.add(new Product(null, 15, 30, "EXPIRABLE", "Butter", LocalDate.now().plusDays(26), null,
                                null));
                products.add(new Product(null, 90, 6, "EXPIRABLE", "Milk", LocalDate.now().minusDays(2), null, null));
                products.add(new Product(null, 15, 30, "SEASONAL", "Watermelon", null, LocalDate.now().minusDays(2),
                                LocalDate.now().plusDays(58)));
                products.add(new Product(null, 15, 30, "SEASONAL", "Grapes", null, LocalDate.now().plusDays(180),
                                LocalDate.now().plusDays(240)));
                return products;
        }

        @Test
        public void processOrder_shouldDecrementNormalInStock_andKeepOrder() throws Exception {
                // GIVEN: one NORMAL in stock
                Product normalInStock = new Product(null, 15, 2, "NORMAL", "USB Cable", null, null, null);
                productRepository.save(normalInStock);

                Order order = new Order();
                order.setItems(new HashSet<>(Collections.singletonList(normalInStock)));
                order = orderRepository.save(order);

                Long orderId = order.getId();
                Long productId = normalInStock.getId();
                Integer before = normalInStock.getAvailable();

                // WHEN
                mockMvc.perform(post("/orders/{orderId}/processOrder", orderId)
                                .contentType("application/json"))
                                .andExpect(status().isOk());

                // THEN: order exists and product available decremented by 1
                Order resultOrder = orderRepository.findById(orderId).orElseThrow();
                assertEquals(orderId, resultOrder.getId());

                Product reloaded = productRepository.findById(productId).orElseThrow();
                assertEquals((Integer) (before - 1), reloaded.getAvailable());

                // No notifications expected
                Mockito.verifyNoInteractions(notificationService);
        }

        @Test
        public void processOrder_normalOutOfStock_shouldSendDelayNotification() throws Exception {
                // GIVEN: NORMAL out of stock with lead time
                Product normalOutOfStock = new Product(null, 7, 0, "NORMAL", "USB Dongle", null, null, null);
                productRepository.save(normalOutOfStock);

                Order order = new Order();
                order.setItems(new HashSet<>(Collections.singletonList(normalOutOfStock)));
                order = orderRepository.save(order);

                // WHEN
                mockMvc.perform(post("/orders/{orderId}/processOrder", order.getId())
                                .contentType("application/json"))
                                .andExpect(status().isOk());

                // THEN: delay notification sent
                Mockito.verify(notificationService, Mockito.times(1))
                                .sendDelayNotification(7, "USB Dongle");
        }

        @Test
        public void processOrder_expiredExpirable_shouldSetAvailableToZero_andSendExpirationNotification()
                        throws Exception {
                // GIVEN: EXPIRABLE expired but available > 0
                LocalDate now = LocalDate.now();
                Product expired = new Product(null, 15, 6, "EXPIRABLE", "Milk", now.minusDays(1), null, null);
                productRepository.save(expired);

                Order order = new Order();
                order.setItems(new HashSet<>(Collections.singletonList(expired)));
                order = orderRepository.save(order);

                // WHEN
                mockMvc.perform(post("/orders/{orderId}/processOrder", order.getId())
                                .contentType("application/json"))
                                .andExpect(status().isOk());

                // THEN: available becomes 0 and expiration notification sent
                Product reloaded = productRepository.findById(expired.getId()).orElseThrow();
                assertEquals((Integer) 0, reloaded.getAvailable());

                Mockito.verify(notificationService, Mockito.times(1))
                                .sendExpirationNotification("Milk", expired.getExpiryDate());
        }

        @Test
        public void processOrder_seasonal_outOfStock_leadTimeBeyondSeasonEnd_shouldNotifyOutOfStock() throws Exception {
                // GIVEN: SEASONAL, out of stock, replenishment would exceed season end
                LocalDate now = LocalDate.now();
                Product seasonal = new Product(null, 20, 0, "SEASONAL", "Strawberries", null,
                                now.minusDays(1), now.plusDays(5));
                productRepository.save(seasonal);

                Order order = new Order();
                order.setItems(new HashSet<>(Collections.singletonList(seasonal)));
                order = orderRepository.save(order);

                // WHEN
                mockMvc.perform(post("/orders/{orderId}/processOrder", order.getId())
                                .contentType("application/json"))
                                .andExpect(status().isOk());

                // THEN
                Mockito.verify(notificationService, Mockito.times(1))
                                .sendOutOfStockNotification("Strawberries");

                Product reloaded = productRepository.findById(seasonal.getId()).orElseThrow();
                assertEquals((Integer) 0, reloaded.getAvailable());
        }
}
