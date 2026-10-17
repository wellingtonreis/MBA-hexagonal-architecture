package br.com.fullcycle.application.ticket;

import br.com.fullcycle.application.repository.InMemoryTicketRepository;
import br.com.fullcycle.domain.customer.Customer;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import br.com.fullcycle.domain.partner.Partner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CancelEventTicketsUseCaseTest {

    @Test
    @DisplayName("Deve cancelar todos os ingressos de um evento")
    void shouldCancelAllTicketsOfEvent() {
        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 10, aPartner);
        final var customer1 = Customer.newCustomer("John Doe", "123.456.789-01", "john.doe@gmail.com");
        final var customer2 = Customer.newCustomer("Jane Doe", "987.654.321-00", "jane.doe@gmail.com");

        final var ticketRepository = new InMemoryTicketRepository();
        ticketRepository.create(Ticket.newTicket(customer1.customerId(), anEvent.eventId()));
        ticketRepository.create(Ticket.newTicket(customer2.customerId(), anEvent.eventId()));

        final var useCase = new CancelEventTicketsUseCase(ticketRepository);
        final var output = useCase.execute(new CancelEventTicketsUseCase.Input(anEvent.eventId().value()));

        Assertions.assertEquals(2, output.cancelledTickets());

        ticketRepository.ticketsOfEventId(anEvent.eventId()).forEach(ticket ->
                Assertions.assertEquals(TicketStatus.CANCELLED, ticket.status())
        );
    }

    @Test
    @DisplayName("Deve reprocessar cancelamento de ingressos sem falhar")
    void shouldBeIdempotentWhenReprocessing() {
        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 10, aPartner);
        final var customer = Customer.newCustomer("John Doe", "123.456.789-01", "john.doe@gmail.com");

        final var ticketRepository = new InMemoryTicketRepository();
        ticketRepository.create(Ticket.newTicket(customer.customerId(), anEvent.eventId()));

        final var useCase = new CancelEventTicketsUseCase(ticketRepository);
        useCase.execute(new CancelEventTicketsUseCase.Input(anEvent.eventId().value()));
        useCase.execute(new CancelEventTicketsUseCase.Input(anEvent.eventId().value()));

        ticketRepository.ticketsOfEventId(anEvent.eventId()).forEach(ticket ->
                Assertions.assertEquals(TicketStatus.CANCELLED, ticket.status())
        );
    }
}
