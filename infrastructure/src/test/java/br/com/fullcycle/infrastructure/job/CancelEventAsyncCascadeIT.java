package br.com.fullcycle.infrastructure.job;

import br.com.fullcycle.IntegrationTest;
import br.com.fullcycle.TestAsyncConfig;
import br.com.fullcycle.application.event.CancelEventUseCase;
import br.com.fullcycle.application.event.SubscribeCustomerToEventUseCase;
import br.com.fullcycle.domain.customer.Customer;
import br.com.fullcycle.domain.customer.CustomerRepository;
import br.com.fullcycle.domain.event.EventCancelled;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventRepository;
import br.com.fullcycle.domain.event.ticket.TicketRepository;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.domain.partner.PartnerRepository;
import br.com.fullcycle.infrastructure.gateways.QueueGateway;
import br.com.fullcycle.infrastructure.jpa.repositories.OutboxJpaRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestAsyncConfig.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class CancelEventAsyncCascadeIT extends IntegrationTest {

    @Autowired
    private SubscribeCustomerToEventUseCase subscribeCustomerToEventUseCase;

    @Autowired
    private CancelEventUseCase cancelEventUseCase;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private QueueGateway queueGateway;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        eventRepository.deleteAll();
        customerRepository.deleteAll();
        partnerRepository.deleteAll();
        outboxJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve cancelar ingressos via outbox, fila e ConsumerQueueGateway")
    void shouldCancelTicketsThroughAsyncCascade() throws Exception {
        final var partner = partnerRepository.create(
                Partner.newPartner("Disney", "45.123.123/0001-12", "disney@gmail.com")
        );
        final var customer1 = customerRepository.create(
                Customer.newCustomer("John Doe", "123.456.789-00", "john@gmail.com")
        );
        final var customer2 = customerRepository.create(
                Customer.newCustomer("Jane Doe", "987.654.321-00", "jane@gmail.com")
        );

        final var event = eventRepository.create(
                br.com.fullcycle.domain.event.Event.newEvent("Disney on Ice", "2021-01-01", 100, partner)
        );
        final var eventId = event.eventId().value();

        subscribeCustomerToEventUseCase.execute(
                new SubscribeCustomerToEventUseCase.Input(customer1.customerId().value(), eventId)
        );
        subscribeCustomerToEventUseCase.execute(
                new SubscribeCustomerToEventUseCase.Input(customer2.customerId().value(), eventId)
        );

        processOutbox();
        waitForAsyncProcessing();

        Assertions.assertEquals(2, ticketRepository.ticketsOfEventId(EventId.with(eventId)).size());
        ticketRepository.ticketsOfEventId(EventId.with(eventId)).forEach(ticket ->
                Assertions.assertEquals(TicketStatus.PENDING, ticket.status())
        );

        cancelEventUseCase.execute(new CancelEventUseCase.Input(eventId));

        processOutbox();
        waitForAsyncProcessing();

        ticketRepository.ticketsOfEventId(EventId.with(eventId)).forEach(ticket ->
                Assertions.assertEquals(TicketStatus.CANCELLED, ticket.status())
        );

        queueGateway.publish(objectMapper.writeValueAsString(new EventCancelled(EventId.with(eventId))));
        waitForAsyncProcessing();

        ticketRepository.ticketsOfEventId(EventId.with(eventId)).forEach(ticket ->
                Assertions.assertEquals(TicketStatus.CANCELLED, ticket.status())
        );
    }

    void processOutbox() {
        transactionTemplate.executeWithoutResult(status -> outboxRelay.execute());
    }

    private void waitForAsyncProcessing() throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            Thread.sleep(250);
        }
    }
}
