# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Is

**Jahrbuch-Generator** is a full-stack web application for managing and generating yearbooks/photo albums. It supports photo uploads, DSLR camera capture (via gphoto2), text content, story/album organization, and multi-user access with JWT authentication.

## Commands

### Backend (Quarkus)

```bash
# Dev mode (backend only, live reload)
./mvnw compile quarkus:dev

# Dev mode with frontend bundled
./mvnw -Pfrontend quarkus:dev

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=ExterneBilderResourceTest

# Production build (JAR + frontend)
./mvnw -Pfrontend clean package

# Native build (requires GraalVM or Docker)
./mvnw -Pfrontend,native clean package
```

### Frontend (React)

```bash
cd src/main/frontend

# Dev server (proxies API to localhost:8080)
npm start

# Build
npm run build

# Test
npm test
```

### Database (for local dev without proxy)

```bash
# Local PostgreSQL via Docker
docker run -d --rm --name postgres -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:latest
```

The dev profile connects to `postgresql://localhost:5433/postgres` by default (Fly.io proxy port). For a local Docker instance, update `%dev.quarkus.datasource.reactive.url` in `application.properties`.

### Production / Fly.io Deployment

```bash
# Build native image and push to Docker Hub
./mvnw -Pnative,frontend,fly clean package k8s:build k8s:push

# Deploy to Fly.io
flyctl deploy --image drdboehm/jahrbuch-generator:fly

# Open proxy to Fly.io free-tier PostgreSQL (port 5433)
fly proxy 5433:5432 -a jahrbuch-generator-pg
```

## Architecture

### Backend: Entity → Service → Resource pattern (Quarkus Reactive)

- **Entities** (`User`, `Bild`, `Story`, `Text`) — JPA/Panache reactive entities
- **Services** (`*Service.java`) — `@ApplicationScoped` beans with business logic, return `Uni<T>` (Mutiny)
- **Resources** (`*Resource.java`) — JAX-RS REST endpoints using `@Path`, `@RolesAllowed`, etc.

All database operations are **non-blocking** using Hibernate Reactive Panache. Use `.chain()` for sequential async ops, `.map()` for transformations.

### Frontend: React SPA

- React Router v6 for routing, Redux Toolkit for auth state
- Material-UI (MUI v5) for UI components
- `@dnd-kit` for drag-and-drop reordering (`SortableBildCard.js`, `SortableTextRow.js`)
- API calls use `fetch` with JWT bearer token stored in Redux
- Modules under `src/main/frontend/src/`: `bilder/`, `texte/`, `stories/`, `users/`, `auth/`, `layout/`
- Each module has an `api.js` (fetch calls) and `index.js` (main view component)
- `sortUtils.js` — shared sort helpers (`sortBy`, `byPriorityDesc`, `byIdDesc`)
- Data is polled every 10 seconds for live updates (see `Bilder.js`, `Story.js`)

The SPA is served by `GatewayResource.java`, which routes unknown paths to `index.html`.

### Auth

- JWT-based (`smallrye-jwt`). Keys are in `src/main/resources/jwt/`.
- `@RolesAllowed("user")` protects endpoints; `@PermitAll` for login.
- `AuthService.java` generates tokens; `UserService.java` extracts the current user from the JWT context.

### Key config: `src/main/resources/application.properties`

- `jahrbuch.captures.path` — directory for uploaded/captured images
- `jahrbuch.captures.station` — enable gphoto2 camera capture (default: false, true in dev)
- `jahrbuch.upload.max-size` — max upload size in bytes (default: 2 MB)
- `jahrbuch.upload.allowed-types` — whitelist of file extensions

### Profiles

- `%dev.*` — development settings (CORS for `localhost:3000`, SQL logging, capture station enabled)
- `-Pfrontend` Maven profile — runs `npm install` + `npm run build` during `generate-resources`
- `-Pfly` Maven profile — targets the Fly.io Docker image (`Dockerfile.fly`)
- `-Pnative` Maven profile — GraalVM native compilation

### Domain language (German)

- **Bild** = photo/image
- **Text** = text entry
- **Story** = album/collection grouping Bilder and Texte
- **Jahrbuch** = yearbook

### Data model relationships

- `Story` belongs to a `User` (unique constraint on `name + user_id`)
- `Bild` and `Text` each optionally reference a `Story`; `null` means unassigned ("pending")
- `Bild.complete` and `Text.complete` are boolean flags for workflow state
- `Bild.position` and `Text.position` are integer fields for DnD sort order within a Story

### Entity conventions

- All entities extend `PanacheEntity` (auto `id` field) and use `@Version` for optimistic locking
- `Bild` uses Lombok `@Getter`/`@Setter`; other entities use public fields directly
- `Bild.pfad` stores paths with a leading `/` (e.g., `/20240101_abc.jpg`); full disk path = `capturesPath + pfad.replaceFirst("^/", "")`

### Tests

Tests use plain JUnit 5 without `@QuarkusTest` — resources are instantiated directly and `@ConfigProperty`-injected fields are set via reflection. See `ExterneBilderResourceTest` for the pattern.

### Notable implementation details

- `BilderUploadResource.java` handles multipart file uploads and image rotation (AWT `Graphics2D`)
- `ExterneBilderResource.java` serves image files directly from disk with path-traversal protection and cache headers
- `CaptureService.java` wraps the `gphoto2-java` library for DSLR shutter control
- `RestExceptionHandler.java` maps Hibernate exceptions to HTTP status codes (404 for not found, 409 for conflicts/stale state from `@Version`)
- The `captures/` directory at the project root is the dev capture/upload target (configured in `application.properties`)
