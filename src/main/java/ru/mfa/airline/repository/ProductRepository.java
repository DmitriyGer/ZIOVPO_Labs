package ru.mfa.airline.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.mfa.airline.model.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
}
