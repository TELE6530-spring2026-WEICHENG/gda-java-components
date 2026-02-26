# CLAUDE.md — Gateway Device App (GDA)

## Project Overview

Java 11 Maven project for the **Gateway Device App** in the TELE6530 "Programming the IoT" course. The GDA bridges constrained devices (Python CDA) with cloud services via MQTT, CoAP, and cloud connectors.

Package root: `programmingtheiot`
Main entry: `programmingtheiot.gda.app.GatewayDeviceApp`

### Key Package Structure

| Package | Purpose |
|---|---|
| `programmingtheiot.common` | Config, constants, interfaces, utilities |
| `programmingtheiot.data` | IoT data model classes (SensorData, ActuatorData, etc.) |
| `programmingtheiot.gda.app` | App lifecycle (GatewayDeviceApp, DeviceDataManager) |
| `programmingtheiot.gda.connection` | MQTT, CoAP, Cloud, SMTP connectors |
| `programmingtheiot.gda.system` | System performance monitoring tasks |

## Build & Test

- **Build:** `mvn compile`
- **Tests:** Run individually from **VS Code** using the Java Test Runner extension — do not run the full test suite via CLI without good reason
- **Integration tests** require external services (MQTT broker, CoAP, cloud endpoints) — never run them automatically

## Code Conventions

### Follow Existing Patterns Exactly

- **Logger:** Always use `java.util.logging.Logger` with the class-name pattern:
  ```java
  private static final Logger _Logger = Logger.getLogger(MyClass.class.getName());
  ```
- **Field naming:** Private fields use underscore prefix for the logger (`_Logger`); other private fields use camelCase without prefix.
- **License header:** Every new `.java` file must start with the MIT license block from existing files.
- **Interfaces first:** New connectors should implement the existing interfaces in `gda.connection` (e.g., `IPubSubClient`, `IRequestResponseClient`).
- **ConfigConst:** All string constants (topic names, config keys) go through `ConfigConst` — never use magic strings inline.
- **ConfigUtil:** Read all configuration from `ConfigUtil` — do not hardcode values.

### Data Model

- All IoT data classes extend `BaseIotData`.
- JSON serialization is done via `DataUtil` (Gson-based) — do not add custom serialization elsewhere.

## Learning Context

The user is a student actively learning IoT system architecture and design. After every change — no matter how small — Claude must provide a brief explanation covering:

1. **What was changed** — the specific file(s), class(es), or method(s) touched
2. **Why it was done that way** — the architectural reasoning, design pattern used, or course concept it relates to (e.g., pub/sub separation, interface-driven design, layered architecture)

This is not optional. Skip the explanation only if the user explicitly says so for a particular task.

## Hard Rules

- **Never auto-commit.** Always ask before creating a git commit.
- **Never modify test files.** Files under `src/test/` are fixed by the course spec.
- **Never modify `pom.xml` dependencies** without explicit instruction.
- Do not add comments or Javadoc to code you didn't change.
- Do not introduce abstractions or helpers for one-off operations.

## Active Protocols (Labs in Progress)

- **MQTT** — `MqttClientConnector` / `Mqttv5ClientConnector` (Eclipse Paho)
- **CoAP** — `CoapClientConnector` / `CoapServerGateway` (Californium 3)
- **Cloud** — `CloudClientConnector`

## Companion Project

The constrained device side is a **Python CDA** in a separate repository. The GDA communicates with it over MQTT/CoAP — do not conflate their codebases.
