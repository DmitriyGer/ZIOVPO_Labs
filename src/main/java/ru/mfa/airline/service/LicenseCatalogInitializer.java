package ru.mfa.airline.service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import ru.mfa.airline.model.LicenseType;
import ru.mfa.airline.model.Product;
import ru.mfa.airline.repository.LicenseTypeRepository;
import ru.mfa.airline.repository.ProductRepository;

@Component
public class LicenseCatalogInitializer implements CommandLineRunner {
    private final ProductRepository productRepository;
    private final LicenseTypeRepository licenseTypeRepository;

    public LicenseCatalogInitializer(ProductRepository productRepository, LicenseTypeRepository licenseTypeRepository) {
        this.productRepository = productRepository;
        this.licenseTypeRepository = licenseTypeRepository;
    }

    // Добавляет базовые записи продуктов и типов лицензий при пустом справочнике.
    @Override
    public void run(String... args) {
        if (productRepository.count() == 0) {
            productRepository.save(new Product("Antivirus", false));
        }

        if (licenseTypeRepository.count() == 0) {
            licenseTypeRepository.save(new LicenseType("TRIAL", 14, "Trial license for 14 days"));
            licenseTypeRepository.save(new LicenseType("MONTH", 30, "License for 30 days"));
            licenseTypeRepository.save(new LicenseType("YEAR", 365, "License for 365 days"));
        }
    }
}
