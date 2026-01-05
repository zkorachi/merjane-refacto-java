package com.nimbleways.springboilerplate.services.implementations;

import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.ProductRepository;
import com.nimbleways.springboilerplate.utils.Annotations.UnitTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(SpringExtension.class)
@UnitTest
public class ProductServiceUnitTests {

    @Mock
    private NotificationService notificationService;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    public void notifyDelay_shouldSaveAndSendDelayNotification() {
        // GIVEN
        Product product = new Product(null, 15, 0, "NORMAL", "RJ45 Cable", null, null, null);

        Mockito.when(productRepository.save(product)).thenReturn(product);

        // WHEN
        productService.notifyDelay(product.getLeadTime(), product);

        // THEN
        assertEquals(0, product.getAvailable());
        assertEquals(15, product.getLeadTime());
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
        Mockito.verify(notificationService, Mockito.times(1))
                .sendDelayNotification(product.getLeadTime(), product.getName());
    }

    @Test
    public void processProductForOrder_normal_withStock_shouldDecrementAndSave() {
        // GIVEN
        Product product = new Product(null, 10, 2, "NORMAL", "USB Cable", null, null, null);
        Mockito.when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // WHEN
        productService.processProductForOrder(product);

        // THEN
        assertEquals(1, product.getAvailable());
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
        Mockito.verifyNoInteractions(notificationService);
    }

    @Test
    public void processProductForOrder_normal_outOfStock_shouldNotifyDelay_whenLeadTimePositive() {
        // GIVEN
        Product product = new Product(null, 7, 0, "NORMAL", "USB Dongle", null, null, null);
        Mockito.when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // WHEN
        productService.processProductForOrder(product);

        // THEN
        assertEquals(0, product.getAvailable());
        Mockito.verify(productRepository, Mockito.times(1)).save(product); // from notifyDelay
        Mockito.verify(notificationService, Mockito.times(1))
                .sendDelayNotification(7, "USB Dongle");
    }

    @Test
    public void processProductForOrder_expirable_notExpired_withStock_shouldDecrementAndSave() {
        // GIVEN
        LocalDate now = LocalDate.now();
        Product product = new Product(null, 15, 3, "EXPIRABLE", "Butter", now.plusDays(5), null, null);
        Mockito.when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // WHEN
        productService.processProductForOrder(product);

        // THEN
        assertEquals(2, product.getAvailable());
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
        Mockito.verifyNoInteractions(notificationService);
    }

    @Test
    public void processProductForOrder_expirable_expired_shouldNotifyExpiration_andSetAvailableToZero() {
        // GIVEN
        LocalDate now = LocalDate.now();
        Product product = new Product(null, 15, 10, "EXPIRABLE", "Milk", now.minusDays(1), null, null);
        Mockito.when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // WHEN
        productService.processProductForOrder(product);

        // THEN
        assertEquals(0, product.getAvailable());
        Mockito.verify(notificationService, Mockito.times(1))
                .sendExpirationNotification("Milk", product.getExpiryDate());
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
    }

    @Test
    public void processProductForOrder_seasonal_inSeason_withStock_shouldDecrementAndSave() {
        // GIVEN
        LocalDate now = LocalDate.now();
        Product product = new Product(
                null,
                10,
                5,
                "SEASONAL",
                "Watermelon",
                null,
                now.minusDays(2),
                now.plusDays(10)
        );
        Mockito.when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // WHEN
        productService.processProductForOrder(product);

        // THEN
        assertEquals(4, product.getAvailable());
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
        Mockito.verifyNoInteractions(notificationService);
    }

    @Test
    public void processProductForOrder_seasonal_outOfStock_leadTimeBeyondSeasonEnd_shouldNotifyOutOfStock_andSetAvailableZero() {
        // GIVEN
        LocalDate now = LocalDate.now();
        // Out of stock, in-season dates, but leadTime will exceed season end
        Product product = new Product(
                null,
                20,
                0,
                "SEASONAL",
                "Strawberries",
                null,
                now.minusDays(1),
                now.plusDays(5)
        );
        Mockito.when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // WHEN
        productService.processProductForOrder(product);

        // THEN
        assertEquals(0, product.getAvailable());
        Mockito.verify(notificationService, Mockito.times(1))
                .sendOutOfStockNotification("Strawberries");
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
    }

    @Test
    public void processProductForOrder_seasonal_beforeSeasonStart_shouldNotifyOutOfStock() {
        // GIVEN
        LocalDate now = LocalDate.now();
        Product product = new Product(
                null,
                10,
                0,
                "SEASONAL",
                "Grapes",
                null,
                now.plusDays(30),
                now.plusDays(60)
        );
        Mockito.when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // WHEN
        productService.processProductForOrder(product);

        // THEN
        Mockito.verify(notificationService, Mockito.times(1))
                .sendOutOfStockNotification("Grapes");
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
        // No delay notification in this branch
        Mockito.verify(notificationService, Mockito.never())
                .sendDelayNotification(Mockito.anyInt(), Mockito.anyString());
    }
}
