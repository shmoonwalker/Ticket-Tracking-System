# Ticket Tracking System — API & Project Reference

A complete reference document covering the project summary, architecture, data model, and every REST endpoint exposed by the Ticket Tracking System.

---

## Table of Contents

1. [Project Summary](#1-project-summary)
2. [Tech Stack](#2-tech-stack)
3. [Architecture Overview](#3-architecture-overview)
4. [Data Model](#4-data-model)
5. [Configuration & Setup](#5-configuration--setup)
6. [API Conventions](#6-api-conventions)
7. [Users API](#7-users-api)
8. [Projects API](#8-projects-api)
9. [Tickets API](#9-tickets-api)
10. [Email Notifications](#10-email-notifications)
11. [Error Handling](#11-error-handling)
12. [Testing](#12-testing)

---

## 1. Project Summary

The **Ticket Tracking System** is a REST API for managing software-development style work items. It supports:

- **Users** — people who can be assigned to tickets (CRUD).
- **Projects** — containers for tickets, exposed read-only with per-status ticket counts.
- **Tickets** — work items with a title, description, status, project, and many-to-many assignees. Supports text + status filtering.
- **Email notifications** — whenever a ticket is updated or a user is assigned/unassigned, the assignee receives an HTML email via the Resend API. Dispatch is asynchronous and fire-and-forget; failures are logged, never surfaced to callers.

The codebase is intentionally small, layered (controller → service → repository), and uses `JdbcTemplate` directly instead of JPA — every SQL statement is visible in the repository classes.

---

## 2. Tech Stack

| Layer            | Choice                                                   |
|------------------|----------------------------------------------------------|
| Language         | Java 25                                                  |
| Framework        | Spring Boot 4.1.0                                        |
| Web              | `spring-boot-starter-web` (Spring MVC)                   |
| Persistence      | `spring-boot-starter-jdbc` (JdbcTemplate)                |
| Validation       | `spring-boot-starter-validation` (Jakarta Bean Validation) |
| Async            | `@EnableAsync` for email dispatch                        |
| Database         | PostgreSQL 16+ (with a custom `ticket_status` ENUM type) |
| Email Provider   | Resend HTTP API                                          |
| HTTP Client      | `java.net.http.HttpClient` (JDK built-in)                |
| Boilerplate      | Lombok (`@RequiredArgsConstructor`)                      |
| Tests            | JUnit 5 + Spring Boot Test (integration + service)       |
| Build            | Maven (`./mvnw`)                                         |
| CI               | GitHub Actions (runs `./mvnw verify` against Postgres)   |

---

## 3. Architecture Overview

```
HTTP request
    │
    ▼
┌──────────────┐    ┌─────────────┐    ┌──────────────┐    ┌────────────┐
│  Controller  │──▶ │   Service   │──▶ │  Repository  │──▶ │ PostgreSQL │
└──────────────┘    └─────────────┘    └──────────────┘    └────────────┘
                          │
                          │  (@Async, fire-and-forget)
                          ▼
                  ┌─────────────────────────┐    ┌────────────────┐
                  │ ResendAutomationService │──▶ │   Resend API   │
                  └─────────────────────────┘    └────────────────┘
```

### Layers

- **`controller/`** — Pure HTTP shape: route mapping, request validation (`@Valid`), status codes. No business logic.
- **`service/`** — Business logic. All mutating methods are `@Transactional`; reads are `@Transactional(readOnly = true)`.
- **`repository/`** — Plain JDBC via `JdbcTemplate`. Native SQL, parameter-bound. No JPA, no entity manager.
- **`email/`** — `ResendAutomationService` sends HTML emails asynchronously, uses the JDK `HttpClient`, escapes HTML, logs failures.
- **`exception/`** — `GlobalExceptionHandler` (`@RestControllerAdvice`) maps domain and framework exceptions to a single `ErrorResponse` JSON shape.
- **`dto/`** — Java `record` types for both `request/` and `response/` payloads.
- **`model/`** — Domain records (`User`, `Ticket`) and the `TicketStatus` enum.
- **`config/`** — `EmailConfig` (HttpClient bean) and `StringToTicketStatusConverter` (query parameter binding).

### Key Design Choices

- **Records everywhere** — DTOs and models are immutable records.
- **No JPA** — explicit SQL keeps the layer thin and easy to reason about.
- **Postgres ENUM for status** — the `tickets.status` column is a `ticket_status` ENUM (`OPEN`, `IN_PROGRESS`, `CLOSED`). The API exposes them lowercased / space-separated (`open`, `in progress`, `closed`) via `@JsonValue` / `@JsonCreator` on the enum.
- **Race-free assignment** — `INSERT ... ON CONFLICT DO NOTHING` on the junction table; the affected-rows count signals whether the row was actually inserted.
- **Async email** — `@EnableAsync` on the application class; `@Async` on `sendTicketUpdatedEmail`. Email failures never affect the HTTP response.

---

## 4. Data Model

### Tables

#### `users`
| Column | Type           | Constraints                                  |
|--------|----------------|----------------------------------------------|
| id     | `bigserial`    | PRIMARY KEY                                  |
| name   | `varchar(255)` | NOT NULL, `char_length(name) >= 3`           |
| email  | `varchar(255)` | NOT NULL, UNIQUE                             |

#### `projects`
| Column | Type           | Constraints                                  |
|--------|----------------|----------------------------------------------|
| id     | `bigserial`    | PRIMARY KEY                                  |
| name   | `varchar(255)` | NOT NULL, `char_length(name) >= 3`           |

#### `tickets`
| Column        | Type             | Constraints                                            |
|---------------|------------------|--------------------------------------------------------|
| id            | `bigserial`      | PRIMARY KEY                                            |
| title         | `varchar(255)`   | NOT NULL                                               |
| description   | `text`           | nullable                                               |
| project_id    | `bigint`         | NOT NULL, FK → `projects(id)`                          |
| status        | `ticket_status`  | NOT NULL — Postgres ENUM (`OPEN`, `IN_PROGRESS`, `CLOSED`) |
| creation_date | `timestamp`      | NOT NULL, DEFAULT `CURRENT_TIMESTAMP`                  |
| update_date   | `timestamp`      | nullable, set on every `UPDATE`                        |

Indexes: `idx_tickets_status`, `idx_tickets_project_id`.

#### `user_ticket` (junction)
| Column    | Type     | Constraints                                          |
|-----------|----------|------------------------------------------------------|
| user_id   | `bigint` | NOT NULL, FK → `users(id)`   ON DELETE CASCADE        |
| ticket_id | `bigint` | NOT NULL, FK → `tickets(id)` ON DELETE CASCADE        |

Composite primary key: `(user_id, ticket_id)`. Index: `idx_user_ticket_ticket_id`.

### Entity Relationships

```
users 1 ─── * user_ticket * ─── 1 tickets * ─── 1 projects
```

A user can be assigned to many tickets and a ticket can have many assignees.

---

## 5. Configuration & Setup

### Prerequisites

- Java 25 (Temurin recommended)
- PostgreSQL 16+
- A Resend account + API key

### Database

```bash
psql -U postgres -c "CREATE USER ticket_user WITH PASSWORD 'ticket_password';"
psql -U postgres -c "CREATE DATABASE ticket_tracker OWNER ticket_user;"
psql -U ticket_user -d ticket_tracker -f src/main/resources/schema.sql
```

### Environment Variables

Copy `.env.example` to `.env` and fill in:

| Variable             | Default                                                  | Purpose                            |
|----------------------|----------------------------------------------------------|------------------------------------|
| `DB_USER`            | _(none)_                                                 | Postgres username                  |
| `DB_PASSWORD`        | _(none)_                                                 | Postgres password                  |
| `DB_URL`             | `jdbc:postgresql://localhost:5432/ticket_tracker`        | JDBC URL                           |
| `RESEND_API_KEY`     | _(none)_                                                 | Resend API key                     |
| `RESEND_FROM_EMAIL`  | `Ticket Tracking <info.ticket@paiksa.com>`               | "From" address used in outgoing mail |

### Running the App

```bash
export $(grep -v '^#' .env | xargs)
./mvnw spring-boot:run
```

The API listens on **`http://localhost:8080`**.

---

## 6. API Conventions

- **Base path:** `/api/v1`
- **Content type:** `application/json` for both requests and responses.
- **IDs:** all resource identifiers are `Long` (64-bit integer).
- **Dates:** ISO-8601 `LocalDateTime`, e.g. `2026-06-17T12:00:00`.
- **Validation:** invalid request bodies return `400 Bad Request` with a `validationErrors` map.
- **Ticket status (wire format):**
    - `"open"`  ↔ enum `OPEN`
    - `"in progress"`  ↔ enum `IN_PROGRESS`
    - `"closed"`  ↔ enum `CLOSED`
- Query parameters for `status` accept lowercased, space-separated, or underscored forms (case-insensitive). E.g. `open`, `IN_PROGRESS`, `in-progress`.

---

## 7. Users API

### 7.1 List users

```
GET /api/v1/users
```

**Response — 200 OK**
```json
[
  { "id": 1, "name": "Alice", "email": "alice@example.com" },
  { "id": 2, "name": "Bob",   "email": "bob@example.com"   }
]
```

---

### 7.2 Get user by ID

```
GET /api/v1/users/{id}
```

**Response — 200 OK**
```json
{ "id": 1, "name": "Alice", "email": "alice@example.com" }
```

**Errors**
- `404 Not Found` — user does not exist.

---

### 7.3 Create user

```
POST /api/v1/users
```

**Request body**
```json
{ "name": "Alice", "email": "alice@example.com" }
```

**Validation**
- `name`: not blank, 3–255 chars.
- `email`: not blank, valid email format, max 255 chars.

**Response — 201 Created**
```json
{ "id": 1, "name": "Alice", "email": "alice@example.com" }
```

**Errors**
- `400 Bad Request` — validation failure.
- `409 Conflict` — email already in use.

---

### 7.4 Update user

```
PUT /api/v1/users/{id}
```

Same body and validation as Create.

**Response — 200 OK**
```json
{ "id": 1, "name": "Alice Doe", "email": "alice.doe@example.com" }
```

**Errors**
- `400 Bad Request` — validation failure.
- `404 Not Found` — user does not exist.
- `409 Conflict` — email belongs to another user.

---

### 7.5 Delete user

```
DELETE /api/v1/users/{id}
```

**Response — 204 No Content**

**Errors**
- `404 Not Found` — user does not exist.

> Deleting a user automatically removes their rows in `user_ticket` (cascade).

---

## 8. Projects API

### 8.1 List projects with ticket counts

```
GET /api/v1/projects
```

Returns every project alongside ticket counts grouped by status. Projects with no tickets show zero counts.

**Response — 200 OK**
```json
[
  {
    "id": 1,
    "name": "Project Alpha",
    "openTickets": 10,
    "inProgressTickets": 5,
    "closedTickets": 30
  },
  {
    "id": 2,
    "name": "Project Beta",
    "openTickets": 0,
    "inProgressTickets": 0,
    "closedTickets": 0
  }
]
```

> Projects are read-only via the API. They are inserted directly into the database (e.g. via seed scripts).

---

## 9. Tickets API

### 9.1 List / search tickets

```
GET /api/v1/tickets?text={text}&status={status}
```

| Query     | Type   | Required | Description                                                                                  |
|-----------|--------|----------|----------------------------------------------------------------------------------------------|
| `text`    | string | no       | Case-insensitive substring search over `title` and `description`.                            |
| `status`  | enum   | no       | One of `open`, `in progress`, `closed` (also accepts `in-progress`, `IN_PROGRESS`, etc.).    |

Both filters combine with **AND**.

**Response — 200 OK**
```json
[
  {
    "id": 1,
    "title": "Login button broken",
    "description": "Clicking does nothing on Firefox",
    "projectId": 1,
    "status": "open",
    "assignedUserIds": [1, 2],
    "creationDate": "2026-06-15T09:00:00",
    "updateDate": null
  }
]
```

**Errors**
- `400 Bad Request` — unknown status value.

---

### 9.2 Get ticket by ID

```
GET /api/v1/tickets/{id}
```

**Response — 200 OK** (same `TicketResponse` shape as above)

**Errors**
- `404 Not Found` — ticket does not exist.

---

### 9.3 Create ticket

```
POST /api/v1/tickets
```

**Request body**
```json
{
  "title": "Login button is broken",
  "description": "Clicking does nothing on Firefox",
  "projectId": 1,
  "status": "open"
}
```

**Validation**
- `title`: not blank, max 255 chars.
- `description`: nullable, max 5000 chars.
- `projectId`: required.
- `status`: required, one of the allowed values.

**Response — 201 Created**
```json
{
  "id": 42,
  "title": "Login button is broken",
  "description": "Clicking does nothing on Firefox",
  "projectId": 1,
  "status": "open",
  "assignedUserIds": [],
  "creationDate": "2026-06-17T12:00:00",
  "updateDate": null
}
```

**Errors**
- `400 Bad Request` — validation failure.
- `404 Not Found` — `projectId` does not exist.

---

### 9.4 Update ticket

```
PUT /api/v1/tickets/{id}
```

Updates `title`, `description`, `projectId`, and `status`. Sends an email to **every** assignee describing the diff between the previous and the new ticket.

**Request body** — same shape and validation as Create.

**Response — 200 OK**
```json
{
  "ticket": {
    "id": 42,
    "title": "Login button is broken on Firefox 130",
    "description": "Clicking does nothing on Firefox",
    "projectId": 1,
    "status": "in progress",
    "assignedUserIds": [1, 2],
    "creationDate": "2026-06-17T12:00:00",
    "updateDate": "2026-06-17T13:30:00"
  },
  "emailNotificationsDispatched": true
}
```

`emailNotificationsDispatched` is `true` when at least one assignee email was queued for delivery. The actual HTTP call to Resend happens asynchronously — any failure is logged and does **not** affect the response.

**Errors**
- `400 Bad Request` — validation failure.
- `404 Not Found` — ticket or `projectId` does not exist.

---

### 9.5 Assign user to ticket

```
POST /api/v1/tickets/{ticketId}/assignees/{userId}
```

Adds a row to `user_ticket`. Sends the user an email saying they have been assigned.

**Response — 200 OK** — the updated `TicketResponse`.

**Errors**
- `400 Bad Request` — user is already assigned to this ticket.
- `404 Not Found` — ticket or user does not exist.

---

### 9.6 Unassign user from ticket

```
DELETE /api/v1/tickets/{ticketId}/assignees/{userId}
```

Removes the assignment. Sends the user an email saying they have been removed.

**Response — 200 OK** — the updated `TicketResponse`.

**Errors**
- `404 Not Found` — ticket or user does not exist, or the user was not assigned to this ticket.

---

## 10. Email Notifications

Implemented by `ResendAutomationService`:

- Triggered by **three** flows:
    1. `PUT /api/v1/tickets/{id}` — every assignee receives an email with the diff.
    2. `POST /api/v1/tickets/{ticketId}/assignees/{userId}` — the newly-assigned user is notified.
    3. `DELETE /api/v1/tickets/{ticketId}/assignees/{userId}` — the unassigned user is notified.

- Email body is an HTML template (escaped for safety) containing the ticket ID, title, status, who triggered the update (currently always `"System"`), and a human-readable change summary.

- Transport: HTTP POST to `https://api.resend.com/emails` with a 10-second timeout and a 5-second connect timeout.

- **Failure handling:** non-2xx responses or exceptions are logged at WARN/ERROR. They never propagate to the controller.

---

## 11. Error Handling

All errors share the same envelope (`ErrorResponse`):

```json
{
  "timestamp": "2026-06-17T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/users",
  "validationErrors": {
    "email": "must be a well-formed email address"
  }
}
```

`validationErrors` is only populated for `MethodArgumentNotValidException` (Bean Validation failures); it is `null` otherwise.

### Status-code mapping (`GlobalExceptionHandler`)

| Exception                              | HTTP Status               | Notes                                                  |
|----------------------------------------|---------------------------|--------------------------------------------------------|
| `ResourceNotFoundException`            | `404 Not Found`           | Unknown user/ticket/project ID.                        |
| `DuplicateEmailException`              | `409 Conflict`            | Email already exists.                                  |
| `IllegalArgumentException`             | `400 Bad Request`         | E.g. assigning an already-assigned user, bad status.   |
| `MethodArgumentNotValidException`      | `400 Bad Request`         | Bean Validation errors → `validationErrors` map.       |
| `MethodArgumentTypeMismatchException`  | `400 Bad Request`         | Query/path parameter cannot be converted.              |
| `HttpMessageNotReadableException`      | `400 Bad Request`         | Malformed JSON in the request body.                    |
| `DataIntegrityViolationException`      | `409 Conflict`            | DB constraint violation; cause is logged at WARN.      |
| `Exception` (fallback)                 | `500 Internal Server Error` | Unhandled exceptions; full stack is logged.           |

---

## 12. Testing

```bash
./mvnw verify
```

The integration tests require a running Postgres with the schema applied. CI runs the same command against a containerised Postgres on every push to `main` and every PR (see `.github/workflows/ci.yml`).

### Test inventory

- `TicketTrackingSystemApplicationTests` — context-loads sanity check.
- `controller/UserControllerIntegrationTest` — full HTTP slice for users.
- `controller/ProjectControllerIntegrationTest` — full HTTP slice for projects.
- `controller/TicketControllerIntegrationTest` — full HTTP slice for tickets, including assignment flows.
- `service/UserServiceTest` — unit tests for `UserService` against a mocked repository.
- `service/TicketServiceTest` — unit tests for `TicketService` against mocked repositories + `ResendAutomationService` (verifies emails are dispatched on update/assign/unassign).

---

## Appendix A — Endpoint Cheat Sheet

| Method | Path                                                | Status (success) | Auth | Body | Returns                  |
|--------|-----------------------------------------------------|------------------|------|------|--------------------------|
| GET    | `/api/v1/users`                                     | 200              | —    | —    | `UserResponse[]`         |
| GET    | `/api/v1/users/{id}`                                | 200              | —    | —    | `UserResponse`           |
| POST   | `/api/v1/users`                                     | 201              | —    | yes  | `UserResponse`           |
| PUT    | `/api/v1/users/{id}`                                | 200              | —    | yes  | `UserResponse`           |
| DELETE | `/api/v1/users/{id}`                                | 204              | —    | —    | —                        |
| GET    | `/api/v1/projects`                                  | 200              | —    | —    | `ProjectSummaryResponse[]` |
| GET    | `/api/v1/tickets?text=&status=`                     | 200              | —    | —    | `TicketResponse[]`       |
| GET    | `/api/v1/tickets/{id}`                              | 200              | —    | —    | `TicketResponse`         |
| POST   | `/api/v1/tickets`                                   | 201              | —    | yes  | `TicketResponse`         |
| PUT    | `/api/v1/tickets/{id}`                              | 200              | —    | yes  | `TicketUpdateResponse`   |
| POST   | `/api/v1/tickets/{ticketId}/assignees/{userId}`     | 200              | —    | —    | `TicketResponse`         |
| DELETE | `/api/v1/tickets/{ticketId}/assignees/{userId}`     | 200              | —    | —    | `TicketResponse`         |

> The API is currently unauthenticated — there is no auth layer in the codebase. Add one before exposing this service publicly.

## Appendix B — DTO Reference

### Requests

```text
CreateUserRequest   { name, email }
UpdateUserRequest   { name, email }
CreateTicketRequest { title, description?, projectId, status }
UpdateTicketRequest { title, description?, projectId, status }
```

### Responses

```text
UserResponse           { id, name, email }
ProjectSummaryResponse { id, name, openTickets, inProgressTickets, closedTickets }
TicketResponse         { id, title, description, projectId, status,
                         assignedUserIds[], creationDate, updateDate }
TicketUpdateResponse   { ticket: TicketResponse, emailNotificationsDispatched }
ErrorResponse          { timestamp, status, error, message, path, validationErrors? }
```
