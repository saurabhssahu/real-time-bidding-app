# real-time-bidding-app

**Real-Time Bidding service in Java Spring Boot**
Keyword-based campaign management with budget smoothing and low-latency bid responses.

---

## Project overview

This project is a small RTB (Real-Time Bidding) backend that supports:

- Creating and listing advertising **campaigns** (name, keywords, budget, current spending).
- Receiving **bid requests** and returning a bid (or no-bid) based on keyword matching, randomized pricing, budget
  checks, and short-term smoothing (max 10 NOK per 10s).
- Designed for low latency (bid path: ≤ 500 ms) and safe concurrent spending.

---

## Tech stack

- Java 17+
- Spring Boot (Web, Data JPA)
- Maven (build & dependency management)
- H2 (in-memory DB for development)
- Redis (short-term smoothing, used later — optional for dev)
- Testcontainers (for integration tests)
- JUnit 5, Mockito (testing)
- Docker & docker-compose (containerization)

---

## Project Structure

```
src/
├── main/
│   ├── java/
│   │   └── no/kobler/rtb/
│   │       ├── config/           # Configuration classes
│   │       ├── controller/       # REST API controllers
│   │       ├── dto/              # Data Transfer Objects
│   │       ├── error/            # Exception handling
│   │       ├── model/            # JPA entities
│   │       ├── repository/       # Data access layer │
│   │       ├── service/          # Business logic
│   │       │   └── bids/
│   │       └── smoothing/        # Rate limiting/smoothing
│   │
│   └── resources/
│       ├── application.yml       # Main configuration
│       └── rtb-app.postman_collection.json #Postman collection
│
└── test/
    └── java/
        └── no/kobler/rtb/
            ├── controller/        # Controller Integration tests
            ├── repository/        # Repository tests
            └── service/           # Service tests
                └── bids/
```

---

## Smoothing implementations

The app supports two smoothing implementations:

1. **In-memory (default)** — single-instance, used for local dev and default tests.
    - Default is used when `smoothing.type` is not set or is `in-memory`.

2. **Redis-backed (embedded)** — multi-instance safe, enabled when `smoothing.type=redis`.
    - The app will start an embedded Redis server automatically for local dev/tests (no external Redis required).
    - Enable by running:
      ```
      mvn spring-boot:run -Dspring-boot.run.arguments="--smoothing.type=redis"
      ```
    - Note: embedded Redis is for development and CI testing. In production point to a managed Redis instance and ensure
      `smoothing.type=redis`.

**Tests**

- Existing unit and integration tests continue to use the in-memory smoothing (no changes required).
- The test class `RedisSmoothingIntegrationTest` demonstrates Redis smoothing. Run it separately or enable the
  `smoothing.type=redis` property for your test profile to exercise the Redis path.

---

## ▶️ Getting Started

### **Application Access**

- **Application URL**: http://localhost:8080
- **H2 Database Console**: http://localhost:8080/h2-console
    - **JDBC URL**: `jdbc:h2:mem:rtbdb`
    - **Username**: `sa`
    - **Password**: (leave empty)

### **Prerequisites**

- JDK 17+
- Maven 3.6+
- Docker (optional)

### **Clone and build**

```bash
git clone https://github.com/<your-username>/real-time-bidding-app.git
cd real-time-bidding-app
```

#### build the project and run tests

```bash
mvn clean verify
```

#### start the application

```bash
mvn spring-boot:run
```

#### run tests without skipping any

```bash
mvn -DskipTests=false test
```

---

## 🐳 Docker Setup (Optional)

### Prerequisites

- Docker (latest stable version)
- Docker Compose (included with Docker Desktop)

### Building and Running with Docker Compose

1. **Build and start all services** (from the project root directory):
   ```bash
   docker compose up --build -d
   ```
   This will:
    - Build the application Docker image
    - Start the Redis container
    - Start the application container

2. **View logs** (optional):
   ```bash
   docker compose logs -f app
   ```

3. **Stop all services**:
   ```bash
   docker compose down
   ```

4. **Rebuild and restart a single service** (e.g., after code changes):
   ```bash
   docker compose up --build -d --no-deps app
   ```

### Environment Variables

The following environment variables can be configured in the `docker-compose.yml` file:

- `smoothing.type`: Set to `redis` to use Redis for rate limiting (default)
- `JAVA_OPTS`: JVM options (default: `-Xms256m -Xmx512m`)
- `spring.redis.host`: Redis host (default: `localhost`)
- `spring.redis.port`: Redis port (default: `6379`)

### Cleanup

To remove all containers, networks, and volumes:

```bash
docker compose down -v --rmi all --remove-orphans
```

---

## Postman Collection

A Postman collection named `rtb-app.postman_collection.json` is available under:
`src/main/resources/rtb-app.postman_collection.json`

### Important Note

To use the collection:

- Either set a Postman variable named `base_url` (e.g., `http://localhost:8080`)
- OR replace `{{base_url}}` in requests with your server host manually.
    - Default base: `http://localhost:8080`

---

## Final Notes

The application is intentionally simple and focused on correctness, determinism, and latency behavior.

- Redis smoothing and atomic DB updates ensure consistent budget usage under concurrency.
- The fallback logic ensures the best-priced eligible campaign wins without overspending.
- Timeouts are enforced at orchestrator, Redis, and DB levels to guarantee 500ms bid SLAs.
