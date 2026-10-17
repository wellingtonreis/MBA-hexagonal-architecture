# Teste manual — cancelamento de eventos (terminal)

Guia para validar o fluxo REST + banco de dados com `curl` e consultas SQL no MySQL.

## Pré-requisitos

- Java 17+
- Docker
- `curl` no terminal
- (Opcional) `jq` para extrair IDs das respostas JSON

## 1. Subir infraestrutura e aplicação

Na raiz do projeto:

```bash
docker compose up -d
./gradlew :infrastructure:bootRun
```

Aguarde a aplicação iniciar (porta **8080**).

### Acessar o MySQL

Descubra o container (se necessário):

```bash
docker ps
```

Conecte ao banco `events`:

```bash
docker exec -it $(docker ps -qf "ancestor=mysql:8.0.30-debian") mysql -uroot -proot events
```

Ou, se o nome do serviço for `mysql`:

```bash
docker exec -it mba-hexagonal-architecture-mysql-1 mysql -uroot -proot events
```

> As consultas abaixo assumem que você já está conectado ao schema `events` (prompt `mysql>`).

---

## 2. Variáveis de ambiente (recomendado)

No **mesmo terminal** em que vai rodar os `curl` (não dentro do `mysql>`):

```bash
export BASE_URL="http://localhost:8080"
```

Após cada `POST` que retorna `id`, exporte as variáveis. Com `jq`:

```bash
# exemplo após criar parceiro
export PARTNER_ID=$(curl -s -X POST "$BASE_URL/partners" \
  -H "Content-Type: application/json" \
  -d '{"cnpj":"45.123.123/0001-12","email":"disney@gmail.com","name":"Disney"}' | jq -r '.id')
```

Sem `jq`, copie o UUID da resposta manualmente:

```bash
export PARTNER_ID="cole-o-uuid-aqui"
export CUSTOMER_ID="cole-o-uuid-aqui"
export EVENT_ID="cole-o-uuid-aqui"
```

---

## 3. Fluxo feliz — REST (`curl`)

### 3.1 Criar parceiro

```bash
curl -s -X POST "$BASE_URL/partners" \
  -H "Content-Type: application/json" \
  -d '{
    "cnpj": "45.123.123/0001-12",
    "email": "disney@gmail.com",
    "name": "Disney"
  }'
```

**Verificar no MySQL:**

```sql
SELECT BIN_TO_UUID(p.id), p.* FROM partners AS p;
```

---

### 3.2 Criar cliente

```bash
curl -s -X POST "$BASE_URL/customers" \
  -H "Content-Type: application/json" \
  -d '{
    "cpf": "123.456.789-00",
    "email": "john@gmail.com",
    "name": "John Doe"
  }'
```

**Verificar no MySQL:**

```sql
SELECT BIN_TO_UUID(c.id), c.* FROM events.customers AS c;
```

---

### 3.3 Criar evento

Substitua `$PARTNER_ID` pelo UUID do passo 3.1.

```bash
curl -s -X POST "$BASE_URL/events" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"Disney on Ice\",
    \"date\": \"2021-01-01\",
    \"totalSpots\": 100,
    \"partnerId\": \"$PARTNER_ID\"
  }"
```

**Verificar no MySQL** (coluna `status` deve ser `ACTIVE`):

```sql
SELECT BIN_TO_UUID(e.id), e.* FROM events.events AS e;
```

---

### 3.4 Consultar evento (visão completa)

```bash
curl -s "$BASE_URL/events/$EVENT_ID"
```

Resposta esperada: JSON com `id`, `name`, `date`, `totalSpots`, `status` = `ACTIVE`.

---

### 3.5 Consultar evento (visão pública)

```bash
curl -s -H "X-Public: true" "$BASE_URL/events/$EVENT_ID"
```

Resposta esperada: apenas `{"id":"...","status":"ACTIVE"}`.

---

### 3.6 Inscrever cliente no evento (reserva)

O `eventId` vai na URL; no corpo, informe só o `customerId` (o campo `eventId` do JSON pode ser `null`).

```bash
curl -s -X POST "$BASE_URL/events/$EVENT_ID/subscribe" \
  -H "Content-Type: application/json" \
  -d "{
    \"customerId\": \"$CUSTOMER_ID\",
    \"eventId\": null
  }"
```

**Aguarde ~3 a 5 segundos** — o job `OutboxRelay` roda a cada 2 segundos e dispara a criação assíncrona do ingresso (`Ticket`).

**Verificar reservas no evento (tabela de junção):**

```sql
SELECT
	BIN_TO_UUID(et.event_ticket_id),
	BIN_TO_UUID(et.customer_id),
	et.ordering,
	BIN_TO_UUID(et.ticket_id),
	BIN_TO_UUID(et.event_id)
FROM
	events.events_tickets AS et;
```

**Verificar outbox** (mensagens `event-ticket.reserved`; após processamento, `published` = 1):

```sql
SELECT BIN_TO_UUID(o.id), o.content, o.published FROM events.outbox AS o;
```

**Ingressos materializados** (consulta extra — não faz parte da lista original, mas confirma o fluxo assíncrono):

```sql
SELECT BIN_TO_UUID(t.id), BIN_TO_UUID(t.event_id), t.status
FROM tickets AS t;
```

Antes do cancelamento do evento, `status` costuma ser `PENDING`.

---

### 3.7 Cancelar o evento

```bash
curl -s -X POST "$BASE_URL/events/$EVENT_ID/cancel"
```

Resposta esperada: `{"id":"...","status":"CANCELLED"}`.

**Aguarde novamente ~3 a 5 segundos** para o `event.cancelled` ser relayado e os ingressos cancelados.

**Verificar evento cancelado:**

```sql
SELECT BIN_TO_UUID(e.id), e.* FROM events.events AS e;
```

**Verificar outbox** (deve aparecer conteúdo com `event.cancelled`):

```sql
SELECT BIN_TO_UUID(o.id), o.content, o.published FROM events.outbox AS o;
```

**Verificar ingressos cancelados:**

```sql
SELECT BIN_TO_UUID(t.id), BIN_TO_UUID(t.event_id), t.status
FROM tickets AS t;
```

Esperado: `status` = `CANCELLED` para ingressos do `EVENT_ID`.

---

### 3.8 Consultar evento após cancelamento

```bash
curl -s "$BASE_URL/events/$EVENT_ID"
```

```bash
curl -s -H "X-Public: true" "$BASE_URL/events/$EVENT_ID"
```

Ambos devem refletir `status` = `CANCELLED`.

---

## 4. Cenários de erro (REST)

### 4.1 Cancelar evento inexistente

```bash
curl -s -w "\nHTTP %{http_code}\n" -X POST "$BASE_URL/events/00000000-0000-0000-0000-000000000099/cancel"
```

Esperado: **HTTP 422** e corpo `Event not found`.

---

### 4.2 Cancelar o mesmo evento duas vezes

```bash
curl -s -w "\nHTTP %{http_code}\n" -X POST "$BASE_URL/events/$EVENT_ID/cancel"
```

Esperado: **HTTP 422** e corpo `Event already cancelled`.

---

### 4.3 Reservar ingresso em evento cancelado

Crie outro cliente (CPF/e-mail diferentes) se necessário e tente:

```bash
curl -s -w "\nHTTP %{http_code}\n" -X POST "$BASE_URL/events/$EVENT_ID/subscribe" \
  -H "Content-Type: application/json" \
  -d "{
    \"customerId\": \"$CUSTOMER_ID\",
    \"eventId\": null
  }"
```

Esperado: **HTTP 422** e corpo `Event is cancelled`.

---

### 4.4 Consultar evento inexistente

```bash
curl -s -w "\nHTTP %{http_code}\n" "$BASE_URL/events/00000000-0000-0000-0000-000000000099"
```

```bash
curl -s -w "\nHTTP %{http_code}\n" -H "X-Public: true" \
  "$BASE_URL/events/00000000-0000-0000-0000-000000000099"
```

Esperado: **HTTP 404** sem corpo de erro.

---

## 5. Rodar SQL a partir do host (sem entrar no `mysql>`)

Útil para scripts ou outro terminal:

```bash
docker exec -i $(docker ps -qf "ancestor=mysql:8.0.30-debian") mysql -uroot -proot events <<'SQL'
SELECT BIN_TO_UUID(p.id), p.* FROM partners AS p;
SELECT BIN_TO_UUID(c.id), c.* FROM events.customers AS c;
SELECT BIN_TO_UUID(e.id), e.* FROM events.events AS e;
SELECT
	BIN_TO_UUID(et.event_ticket_id),
	BIN_TO_UUID(et.customer_id),
	et.ordering,
	BIN_TO_UUID(et.ticket_id),
	BIN_TO_UUID(et.event_id)
FROM events.events_tickets AS et;
SELECT BIN_TO_UUID(o.id), o.content, o.published FROM events.outbox AS o;
SQL
```

---

## 6. Ordem sugerida de validação

| Etapa | Ação `curl` | SQL principal |
|-------|-------------|---------------|
| 1 | `POST /partners` | `partners` |
| 2 | `POST /customers` | `customers` |
| 3 | `POST /events` | `events` (`status` ACTIVE) |
| 4 | `GET /events/{id}` | — |
| 5 | `POST /events/{id}/subscribe` + esperar | `events_tickets`, `outbox`, `tickets` |
| 6 | `POST /events/{id}/cancel` + esperar | `events` (CANCELLED), `outbox`, `tickets` (CANCELLED) |
| 7 | Erros 422/404 | — |

---

## 7. Observações

- O relay da outbox é **periódico (2 s)**; se `published` continuar `0` ou ingressos não mudarem de status, aguarde mais alguns segundos e repita as consultas.
- IDs na API são UUID em texto; no MySQL binário use `BIN_TO_UUID(...)` como nas queries acima.
- Parceiros são expostos via rota funcional `POST /partners` (mesmo contrato JSON do curso).
