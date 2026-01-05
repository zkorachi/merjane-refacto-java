package com.nimbleways.springboilerplate.services.implementations;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.nimbleways.springboilerplate.util.ProductType;
import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.ProductRepository;

@Service
public class ProductService {

    private final ProductRepository pr;
    private final NotificationService ns;

    public ProductService(ProductRepository pr, NotificationService ns) {
        this.pr = pr;
        this.ns = ns;
    }

    /**
     * Entry point: process one product from an order.
     * Keeps business rules centralized and testable.
     */
    public void processProductForOrder(Product p) {
        ProductType type = p.getProductType();

        switch (type) {
            case NORMAL -> processNormal(p);
            case SEASONAL -> processSeasonal(p);
            case EXPIRABLE -> processExpirable(p);
            default -> throw new IllegalArgumentException("Unknown product type: " + type);
        }
    }

    private void processNormal(Product p) {
        if (hasStock(p)) {
            decrementAndSave(p);
            return;
        }

        Integer leadTime = p.getLeadTime();
        if (leadTime != null && leadTime > 0) {
            notifyDelay(leadTime, p);
        }
    }

    private void processSeasonal(Product p) {
        LocalDate now = LocalDate.now();

        // If currently in season AND in stock => sell
        if (isInSeason(now, p) && hasStock(p)) {
            decrementAndSave(p);
            return;
        }

        // Otherwise handle seasonal rules (delay vs out-of-season / out-of-stock)
        handleSeasonalProduct(p);
    }

    private void processExpirable(Product p) {
        LocalDate now = LocalDate.now();

        // Sell only if in stock AND not expired
        if (hasStock(p) && isNotExpired(now, p)) {
            decrementAndSave(p);
            return;
        }

        // Expired (or no stock) => considered not available and notify
        handleExpiredProduct(p);
    }

    private boolean hasStock(Product p) {
        return p.getAvailable() != null && p.getAvailable() > 0;
    }

    private boolean isInSeason(LocalDate now, Product p) {
        return p.getSeasonStartDate() != null
                && p.getSeasonEndDate() != null
                && now.isAfter(p.getSeasonStartDate())
                && now.isBefore(p.getSeasonEndDate());
    }

    private boolean isNotExpired(LocalDate now, Product p) {
        return p.getExpiryDate() != null && p.getExpiryDate().isAfter(now);
    }

    private void decrementAndSave(Product p) {
        p.setAvailable(p.getAvailable() - 1);
        pr.save(p);
    }

    public void notifyDelay(int leadTime, Product p) {
        p.setLeadTime(leadTime);
        pr.save(p);
        ns.sendDelayNotification(leadTime, p.getName());
    }

    /**
     * Seasonal rule:
     * - If delay goes beyond season end => product considered not available -> notify out of stock, set available=0
     * - If season hasn't started yet => notify out of stock (not available right now)
     * - Else => delay is acceptable -> delay notification
     */
    public void handleSeasonalProduct(Product p) {
        LocalDate now = LocalDate.now();

        // Defensive: if dates are missing, fall back to delay behavior if possible
        if (p.getSeasonStartDate() == null || p.getSeasonEndDate() == null) {
            Integer leadTime = p.getLeadTime();
            if (leadTime != null && leadTime > 0) {
                notifyDelay(leadTime, p);
            }
            return;
        }

        Integer leadTime = p.getLeadTime();
        if (leadTime == null) {
            leadTime = 0;
        }

        // If replenishment date exceeds season end => not available
        if (now.plusDays(leadTime).isAfter(p.getSeasonEndDate())) {
            ns.sendOutOfStockNotification(p.getName());
            p.setAvailable(0);
            pr.save(p);
            return;
        }

        // If we're before season start => not available now (notify)
        if (p.getSeasonStartDate().isAfter(now)) {
            ns.sendOutOfStockNotification(p.getName());
            pr.save(p);
            return;
        }

        // Otherwise => delay notification
        if (leadTime > 0) {
            notifyDelay(leadTime, p);
        }
    }

    /**
     * Expirable rule:
     * - If expired OR no stock => not available -> expiration notification, set available=0
     */
    public void handleExpiredProduct(Product p) {
        ns.sendExpirationNotification(p.getName(), p.getExpiryDate());
        p.setAvailable(0);
        pr.save(p);
    }
}
