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
│   │   ├── service/               # Services only for communication with other services
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
- **Java Version**: 25
- **Framework**: Spring Boot 4.1.2
- **Build Tool**: Maven Wrapper (`./mvnw`)
- **Key Libraries**: Lombok, Spring Boot Docker Compose, Spring Boot Test, Cucumber, MapStruct

## Build & Configuration
- **Build Command**: `./mvnw clean compile` or `./mvnw clean package`
- **Run Command**: `./mvnw spring-boot:run`
- **Profiles**:
  - `local`: For local development (use `-Dspring-boot.run.profiles=local`)

## Testing Information
### Running Tests
- Run all tests: `./mvnw test`
- Run a specific test: `./mvnw test -Dtest=VehicleComponentImplTest`
- Skip tests: `./mvnw package -DskipTests`

### Adding New Tests
**Do not load the whole Spring context unless it is really needed.** A unit test must not start the application;
`@SpringBootTest` is reserved for the Cucumber suites and the rare test that needs MVC, security and configuration
together (currently only `SwaggerConfigurationTest`). `SpringTestRulesTest` (ArchUnit) fails the build for any
other `@SpringBootTest`. Pick the lightest option that tests what matters:

| What is tested | How |
|---|---|
| Logic of a component, helper, mapper | Plain JUnit 5 + AssertJ, collaborators mocked with Mockito, objects built with builders |
| Controller: request mapping, validation, status codes | `@WebMvcTest(controllers = …)` (web slice only), components mocked |
| SQL, JPA mapping, repository queries, transactions | `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@ActiveProfiles("test")` (only JPA, Flyway, H2) |
| Flyway migrations | Plain JUnit: `Flyway.configure()` against a fresh in-memory H2 (see `FlywayMigrationTest`) |
| Configuration properties, conditions, auto-configuration settings | `ApplicationContextRunner` with only the configuration under test (see `MailConfigurationTest`, `PortalStartupCheckTest`) |
| HTTP behaviour end to end (status codes, security, ownership, both operating modes) | Cucumber scenario in `src/test/resources/features` |

- **Profile `test`:** every test that starts Spring (`@SpringBootTest`, `@DataJpaTest`) activates
  `@ActiveProfiles("test")`. `src/test/resources/application-test.properties` only holds the deviations from the
  main `application.properties` (H2, fixed `bm.mode`, test secrets); `SpringTestRulesTest` fails if it is missing.
- **Architectural Tests**: The project uses **ArchUnit** to enforce layer dependencies. See `ArchitecturalLayerTest` for existing rules.
  - *Note*: Ensure the package name in `@AnalyzeClasses` matches the project structure.
- **Coverage**: `./mvnw verify` fails below 90 % lines and 90 % branches (JaCoCo, unit and Cucumber tests together).

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
- Write version numbers in the pom.xml in the properties section.
- In the pom.xml, separate every `<dependency>` block from the next one with an empty line (in `<dependencies>` as
  well as in `<dependencyManagement>`).
