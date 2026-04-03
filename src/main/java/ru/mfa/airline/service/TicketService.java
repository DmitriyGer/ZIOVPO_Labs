package ru.mfa.airline.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.mfa.airline.dto.Ticket;
import ru.mfa.airline.dto.TicketResponse;
import ru.mfa.airline.model.Device;
import ru.mfa.airline.model.License;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

@Service
public class TicketService {
    private final long ticketTtlSeconds;
    private final String signatureSecret;

    public TicketService(@Value("${license.ticket.ttl-seconds:300}") long ticketTtlSeconds,
            @Value("${license.ticket.signature-secret:ticket-signature-secret}") String signatureSecret) {
        this.ticketTtlSeconds = ticketTtlSeconds;
        this.signatureSecret = signatureSecret;
    }

    // Формирует ответ с тикетом и подписью для клиента.
    public TicketResponse buildSignedResponse(License license, Device device) {
        Ticket ticket = buildTicket(license, device);
        String signature = signTicket(ticket);
        return new TicketResponse(ticket, signature);
    }

    // Заполняет Ticket данными по лицензии и устройству.
    private Ticket buildTicket(License license, Device device) {
        Ticket ticket = new Ticket();
        ticket.setServerDate(OffsetDateTime.now());
        ticket.setTicketTtlSeconds(ticketTtlSeconds);
        ticket.setActivationDate(license.getFirstActivationDate());
        ticket.setExpirationDate(license.getEndingDate());
        ticket.setUserId(license.getUser() == null ? null : license.getUser().getId());
        ticket.setDeviceId(device.getId());
        ticket.setBlocked(license.isBlocked());
        return ticket;
    }

    // Подписывает Ticket через HMAC-SHA256.
    private String signTicket(Ticket ticket) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(signatureSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] digest = mac.doFinal(ticketPayload(ticket).getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign ticket", ex);
        }
    }

    // Собирает строку тикета для подписи.
    private String ticketPayload(Ticket ticket) {
        return String.join("|",
                String.valueOf(ticket.getServerDate()),
                String.valueOf(ticket.getTicketTtlSeconds()),
                String.valueOf(ticket.getActivationDate()),
                String.valueOf(ticket.getExpirationDate()),
                String.valueOf(ticket.getUserId()),
                String.valueOf(ticket.getDeviceId()),
                String.valueOf(ticket.isBlocked()));
    }

    // Переводит байты подписи в hex-строку.
    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
