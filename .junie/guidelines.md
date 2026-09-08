## Project Overview
- **Service Name**: Maintenance Service (`maintenance`)
- **Group ID**: `de.chris-wohlbrecht`
- **Artifact ID**: `maintenance`
- **Main Package**: `de.chriswohlbrecht.maintenance`
- **Purpose**: Maintenance service application.

## Project Structure
```
src/
├── main/
│   ├── java/de/publik/dataproxyservice/
│   │   ├── component/             # Business logic
│   │   ├── service/               # Intermediate services
│   │   ├── controller/            # REST Controllers
│   │   ├── configuration/         # Spring Configuration
│   │   ├── persistance/           # Persistance layer JPA models etc.
│   │   └── api/ (generated)       # Generated OpenAPI clients and models
│   └── resources/
│       └── config/                # Application properties
└── test/
    ├── java/de/publik/dataproxyservice/
    │   ├── archunit/              # Architectural constraint tests
    │   ├── service/               # Service integration tests
    │   └── cucumber/              # BDD/Cucumber integration tests
    └── resources/                 # Test properties and resources
```

## Technologies
- **Java Version**: 21
- **Framework**: Spring Boot 4.1.1
- **Build Tool**: Maven Wrapper (`./mvnw`)
- **Key Libraries**: Lombok, Spring Boot Docker Compose, Spring Boot Test

## Build & Configuration
- **Build Command**: `./mvnw clean compile` or `./mvnw clean package`
- **Run Command**: `./mvnw spring-boot:run`
- **Profiles**:
  - `local`: For local development (use `-Dspring-boot.run.profiles=local`)

## Testing Information
### Running Tests
- Run all tests: `./mvnw test`
- Run a specific test: `./mvnw test -Dtest=MaintenanceApplicationTests`
- Skip tests: `./mvnw package -DskipTests`

### Adding New Tests
- **Integration Tests**: Use `Cucumber` for integration/bdd tests.
- **Unit Tests**: Do not use `@SpringBootTest` if not neccessary. Standard JUnit 5 / AssertJ tests.
- **Architectural Tests**: The project uses **ArchUnit** to enforce layer dependencies. See `ArchitecturalLayerTest` for existing rules.
  - *Note*: Ensure the package name in `@AnalyzeClasses` matches the project structure.

### Sample Test Process
1. Create a test class in `src/test/java/...`.
2. Annotate with `@SpringBootTest` for integration tests.
3. Use `@Autowired` to inject dependencies.
4. Run with `./mvnw test -Dtest=YourTestClass`.

## Development Guidelines
- **Code Style**:
  - Use Lombok for boilerplate reduction (`@RequiredArgsConstructor`, `@Getter`, `@Setter`, `@Builder`, etc.).
  - Follow the layered architecture: `Controller` -> `Component` -> `Service`.
- **Architectural Constraints**:
  - `Controller` must not be accessed by any other layer.
  - `Service` may only be accessed by `Component`.
  - `Component` may only be accessed by `Service` or `Controller`.
  - `Service` classes should be annotated with `@Service`.
  - `Component` classes should be annotated with `@Component`.

## Work Style
- Prefer concise, precise responses
- Always reference source code locations
- Ask when uncertain or unclear
- Respect test structure: use @Order for ordered test execution
- Never use `var` for variable declarations.
- Use builder pattern for object creation in tests.
