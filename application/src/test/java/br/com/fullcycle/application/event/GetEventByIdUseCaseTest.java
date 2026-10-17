package br.com.fullcycle.application.event;

import br.com.fullcycle.application.repository.InMemoryEventRepository;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventStatus;
import br.com.fullcycle.domain.partner.Partner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetEventByIdUseCaseTest {

    @Test
    @DisplayName("Deve obter um evento por id")
    void shouldGetEventById() {
        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 10, aPartner);

        final var eventRepository = new InMemoryEventRepository();
        eventRepository.create(anEvent);

        final var useCase = new GetEventByIdUseCase(eventRepository);
        final var output = useCase.execute(new GetEventByIdUseCase.Input(anEvent.eventId().value()));

        Assertions.assertTrue(output.isPresent());
        Assertions.assertEquals(anEvent.eventId().value(), output.get().id());
        Assertions.assertEquals("Disney on Ice", output.get().name());
        Assertions.assertEquals("2021-01-01", output.get().date());
        Assertions.assertEquals(10, output.get().totalSpots());
        Assertions.assertEquals(EventStatus.ACTIVE.name(), output.get().status());
    }

    @Test
    @DisplayName("Não deve obter evento inexistente")
    void shouldReturnEmptyWhenEventNotFound() {
        final var eventRepository = new InMemoryEventRepository();
        final var useCase = new GetEventByIdUseCase(eventRepository);

        final var output = useCase.execute(new GetEventByIdUseCase.Input(EventId.unique().value()));

        Assertions.assertTrue(output.isEmpty());
    }
}
