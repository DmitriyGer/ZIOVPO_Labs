package ru.mfa.airline.web;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.mfa.airline.dto.SignatureIdsRequest;
import ru.mfa.airline.service.MultipartMixedResponseFactory;
import ru.mfa.airline.service.binary.BinarySignatureExportService;
import ru.mfa.airline.service.binary.BinarySignaturePackage;

import java.time.Instant;

@RestController
@RequestMapping("/api/binary/signatures")
public class BinarySignatureController {
    private final BinarySignatureExportService binarySignatureExportService;
    private final MultipartMixedResponseFactory multipartMixedResponseFactory;

    public BinarySignatureController(BinarySignatureExportService binarySignatureExportService,
            MultipartMixedResponseFactory multipartMixedResponseFactory) {
        this.binarySignatureExportService = binarySignatureExportService;
        this.multipartMixedResponseFactory = multipartMixedResponseFactory;
    }

    // Возвращает полную бинарную выгрузку без DELETED.
    @GetMapping("/full")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<byte[]> getFull() {
        BinarySignaturePackage binaryPackage = binarySignatureExportService.exportFull();
        return multipartMixedResponseFactory.build(binaryPackage);
    }

    // Возвращает бинарный инкремент по updatedAt > since.
    @GetMapping("/increment")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<byte[]> getIncrement(
            @RequestParam("since") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        BinarySignaturePackage binaryPackage = binarySignatureExportService.exportIncrement(since);
        return multipartMixedResponseFactory.build(binaryPackage);
    }

    // Возвращает бинарную выгрузку по списку UUID.
    @PostMapping("/by-ids")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<byte[]> getByIds(@Valid @RequestBody SignatureIdsRequest request) {
        BinarySignaturePackage binaryPackage = binarySignatureExportService.exportByIds(request.getIds());
        return multipartMixedResponseFactory.build(binaryPackage);
    }
}
