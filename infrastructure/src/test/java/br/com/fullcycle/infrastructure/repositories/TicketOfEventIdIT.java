package br.com.fullcycle.infrastructure.repositories;

import br.com.fullcycle.IntegrationTest;
import br.com.fullcycle.domain.customer.Customer;
import br.com.fullcycle.domain.customer.CustomerRepository;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketRepository;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.domain.partner.PartnerRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TicketOfEventIdIT extends IntegrationTest {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        customerRepository.deleteAll();
        partnerRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve consultar ingressos por evento")
    void shouldFindTicketsByEventId() {
        final var partner = partnerRepository.create(
                Partner.newPartner("Disney", "45.123.123/0001-12", "disney@gmail.com")
        );
        final var event = Event.newEvent("Disney on Ice", "2021-01-01", 100, partner);
        final var customer = customerRepository.create(
                Customer.newCustomer("John Doe", "123.456.789-00", "john@gmail.com")
        );

        ticketRepository.create(Ticket.newTicket(customer.customerId(), event.eventId()));

        final var tickets = ticketRepository.ticketsOfEventId(event.eventId());
        Assertions.assertEquals(1, tickets.size());
        Assertions.assertEquals(event.eventId(), tickets.get(0).eventId());
    }
}
