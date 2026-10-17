# MBA — Arquitetura Hexagonal e Clean Architecture

Laboratório de cancelamento de eventos com processamento assíncrono de ingressos.

## Pré-requisitos

- Java 17+
- Docker (opcional, para MySQL em desenvolvimento)

## Configuração

### Banco de dados (desenvolvimento)

```bash
docker compose up -d
```

A aplicação usa MySQL em `localhost:3306` (usuário/senha `root`/`root`, database `events`), conforme `infrastructure/src/main/resources/application.properties`.

### Build e execução

```bash
./gradlew :infrastructure:bootRun
```

A API REST ficará disponível na porta padrão do Spring Boot (`8080`). GraphiQL está habilitado em `/graphiql`.

## Testes

```bash
./gradlew test
```

Relatório de cobertura (JaCoCo):

```bash
./gradlew test jacocoTestReport
```

## Fluxo assíncrono de cancelamento

1. **`CancelEventUseCase`** cancela o agregado `Event`, persiste o estado `CANCELLED` e registra o evento de domínio **`EventCancelled`** (`event.cancelled`).
2. **`EventDatabaseRepository`** grava os eventos de domínio na **outbox** (`OutboxEntity`).
3. **`OutboxRelay`** (job agendado) lê mensagens não publicadas e envia o JSON para a **fila** via **`QueueGateway`**.
4. **`ConsumerQueueGateway`** consome a mensagem, identifica `event.cancelled` e executa **`CancelEventTicketsUseCase`**.
5. **`CancelEventTicketsUseCase`** busca os ingressos do evento em **`TicketRepository`** e aplica `cancel()` em cada `Ticket`, mantendo os agregados desacoplados.

## Endpoints principais

| Método | Caminho | Descrição |
|--------|---------|-----------|
| `POST` | `/events/{id}/cancel` | Cancela o evento |
| `GET` | `/events/{id}` | Consulta o evento (use `X-Public` para visão reduzida `{id, status}`) |

GraphQL: mutação `cancelEvent(id: ID!)` e consulta `eventOfId(id: ID!)`.
