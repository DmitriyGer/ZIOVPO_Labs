package ru.mfa.airline.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.mfa.airline.dto.Ticket;
import ru.mfa.airline.dto.TicketResponse;
import ru.mfa.airline.model.Device;
import ru.mfa.airline.model.License;
import ru.mfa.signature.SignatureService;

import java.time.OffsetDateTime;

@Service
public class TicketService {
    private final long ticketTtlSeconds;
    private final SignatureService signatureService;

    public TicketService(@Value("${license.ticket.ttl-seconds:300}") long ticketTtlSeconds,
            SignatureService signatureService) {
        this.ticketTtlSeconds = ticketTtlSeconds;
        this.signatureService = signatureService;
    }

    // Формирует ответ с тикетом и подписью для клиента.
    public TicketResponse buildSignedResponse(License license, Device device) {
        Ticket ticket = buildTicket(license, device);
        String signature = signatureService.sign(ticket);
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
}
