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

## Project layout (planned)

- `src/main` - main source code
    - `java` - Java source code
        - `no.kobler.rtb.app` - main application
        - `no.kobler.rtb.domains` - domain models
        - `no.kobler.rtb.services` - service logic
        - `no.kobler.rtb.adapters` - adapters to external systems (e.g. Redis)
- `src/test` - test code
    - `java` - Java source code
        - `no.kobler.rtb.app.integrationtest` - integration tests
        - `no.kobler.rtb.app.unittest` - unit tests
- `src/main/resources` - configuration files
    - `application.yml` - main application configuration
    - `application-test.yml` - test application configuration
- `docker` - Dockerfiles and docker-compose.yml

---

## ▶️ Getting Started

### **Prerequisites**

- JDK 17+
- Maven 3.6+
- Docker (optional)

### **Clone and build**

```bash
git clone https://github.com/<your-username>/real-time-bidding-app.git
cd real-time-bidding-app
mvn clean verify
mvn spring-boot:run

