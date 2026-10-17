package br.com.fullcycle.infrastructure.repositories;

import br.com.fullcycle.IntegrationTest;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventRepository;
import br.com.fullcycle.domain.event.EventStatus;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.domain.partner.PartnerRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EventStatusPersistenceIT extends IntegrationTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        partnerRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve persistir e restaurar o status do evento")
    void shouldPersistAndRestoreEventStatus() {
        final var partner = partnerRepository.create(
                Partner.newPartner("Disney", "45.123.123/0001-12", "disney@gmail.com")
        );
        final var event = eventRepository.create(
                Event.newEvent("Disney on Ice", "2021-01-01", 100, partner)
        );

        event.cancel();
        eventRepository.update(event);

        final var restored = eventRepository.eventOfId(event.eventId()).orElseThrow();
        Assertions.assertEquals(EventStatus.CANCELLED, restored.status());
    }
}
