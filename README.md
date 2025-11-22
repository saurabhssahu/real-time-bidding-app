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
        - `no.kobler.rtb` - main application package
            - `controller` - REST API controllers
            - `dto` - Data Transfer Objects (DTOs)
            - `model` - JPA entities and domain models
            - `repository` - JPA repositories
            - `service` - business logic and services
- `src/test` - test code
    - `java` - Java test code
        - `no.kobler.rtb` - test package structure mirrors main
            - `controller` - controller tests
            - `repository` - repository tests
            - `service` - service layer tests
- `src/main/resources` - configuration files
    - `application.yml` - main application configuration
    - `application-test.yml` - test application configuration
- `docker` - Dockerfiles and docker-compose.yml

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
