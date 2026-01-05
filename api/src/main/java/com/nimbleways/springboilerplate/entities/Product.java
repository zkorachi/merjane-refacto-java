package com.nimbleways.springboilerplate.entities;

import com.nimbleways.springboilerplate.util.ProductType;
import lombok.*;

import java.time.LocalDate;
import javax.persistence.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "lead_time")
    private Integer leadTime;

    @Column(name = "available")
    private Integer available;

    /**
     * Stored as String in DB to avoid schema changes
     */
    @Column(name = "type")
    private String type;

    @Column(name = "name")
    private String name;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "season_start_date")
    private LocalDate seasonStartDate;

    @Column(name = "season_end_date")
    private LocalDate seasonEndDate;

    /**
     * Type-safe access to product type (domain concept)
     */
    public ProductType getProductType() {
        return ProductType.valueOf(this.type);
    }

    /**
     * Optional helper for setting type safely
     */
    public void setProductType(ProductType productType) {
        this.type = productType.name();
    }
}
