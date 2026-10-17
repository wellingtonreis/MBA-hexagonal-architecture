package br.com.fullcycle.application.event;

import br.com.fullcycle.application.repository.InMemoryEventRepository;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventStatus;
import br.com.fullcycle.domain.exceptions.ValidationException;
import br.com.fullcycle.domain.partner.Partner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CancelEventUseCaseTest {

    @Test
    @DisplayName("Deve cancelar um evento ativo")
    void shouldCancelActiveEvent() {
        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 10, aPartner);

        final var eventRepository = new InMemoryEventRepository();
        eventRepository.create(anEvent);

        final var useCase = new CancelEventUseCase(eventRepository);
        final var output = useCase.execute(new CancelEventUseCase.Input(anEvent.eventId().value()));

        Assertions.assertEquals(anEvent.eventId().value(), output.id());
        Assertions.assertEquals(EventStatus.CANCELLED.name(), output.status());

        final var stored = eventRepository.eventOfId(anEvent.eventId()).orElseThrow();
        Assertions.assertEquals(EventStatus.CANCELLED, stored.status());
        Assertions.assertEquals(1, stored.allDomainEvents().size());
    }

    @Test
    @DisplayName("Não deve cancelar evento inexistente")
    void shouldFailWhenEventNotFound() {
        final var eventRepository = new InMemoryEventRepository();
        final var useCase = new CancelEventUseCase(eventRepository);

        final var error = Assertions.assertThrows(
                ValidationException.class,
                () -> useCase.execute(new CancelEventUseCase.Input(EventId.unique().value()))
        );

        Assertions.assertEquals("Event not found", error.getMessage());
    }

    @Test
    @DisplayName("Não deve cancelar evento já cancelado")
    void shouldFailWhenEventAlreadyCancelled() {
        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 10, aPartner);
        anEvent.cancel();

        final var eventRepository = new InMemoryEventRepository();
        eventRepository.create(anEvent);

        final var useCase = new CancelEventUseCase(eventRepository);

        final var error = Assertions.assertThrows(
                ValidationException.class,
                () -> useCase.execute(new CancelEventUseCase.Input(anEvent.eventId().value()))
        );

        Assertions.assertEquals("Event already cancelled", error.getMessage());
    }
}
