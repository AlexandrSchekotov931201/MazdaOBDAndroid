# Agent Workflow

Before starting any task:

- Check the available project and system skills first.
- If any skill matches the request, even partially, use it immediately.
- Read all documentation and local instruction files related to the task, including referenced documents.
- Follow the relevant documentation and skill instructions precisely.
- If no skill matches, inspect the relevant code and local docs before writing or changing code.
- Do not skip safety, validation, or quality gates described by matching skills or project docs.

For any change that touches OBD, ELM, CAN, telemetry, connection recovery, or vehicle monitoring:

- Read and follow the OBD Architecture section in this file before editing code.
- Treat the architecture as a required design constraint, not as optional background information.
- If a requested change conflicts with it, stop and explain the conflict before implementing it.

# Git Workflow

For any new code change:

- Do not start coding directly on `main`.
- First make sure the working tree is clean, or intentionally preserve existing user changes.
- Update `main` from the remote before branching.
- Create a new feature branch from the updated `main`.
- Make all code changes on that feature branch.
- Run relevant validation before committing.
- Commit only the files related to the task.

If there are uncommitted user changes, do not overwrite or discard them. Ask before moving, stashing, or changing unrelated work.

# Vehicle Diagnostic Safety Rules

This project must remain read-only with respect to any vehicle.

## Allowed

- Read standard telemetry and discovery data, such as Mode 01 PIDs, RPM, temperature, and supported PID discovery.
- Configure the ELM adapter for read-only communication, such as echo, line feeds, headers, and protocol selection.
- Parse and log raw responses, negative responses, unsupported PIDs, timeouts, and transport errors.
- Reduce polling frequency or skip unsupported PIDs when that makes communication safer, quieter, or more reliable.

## Forbidden

- Do not send Mode 04 or any command that clears DTCs/errors.
- Do not send UDS session, security, write, routine-control, or output-control commands, including 10, 27, 2E, 31, and 3B.
- Do not write ECU parameters, coding, adaptations, calibration data, or stored settings.
- Do not run actuator tests, active tests, output-control routines, or service procedures.
- Do not add manufacturer-specific commands by guesswork.

## Decision Rule

If a command is not clearly read-only, treat it as forbidden. Stop and ask before adding, changing, or testing it.

# OBD Architecture

This section defines the required architecture for vehicle communication and telemetry. It is normative for contributors and AI agents. Changes may extend the architecture, but must not bypass its boundaries.

## Goals

- Support standard OBD-II vehicles while keeping the core independent of every vehicle profile, including brand, model, engine, and vehicle-specific configuration.
- Keep every vehicle interaction read-only.
- Isolate transport details from ELM protocol handling and feature code.
- Correlate every response with its request and source ECU.
- Recover from unreliable adapters without hiding fatal protocol or developer errors.
- Make new telemetry metrics declarative and straightforward to test.

## Dependency Direction

Dependencies flow in one direction:

```text
Feature / UI
    -> Telemetry catalog and polling
        -> Session lifecycle and capabilities
            -> OBD / ELM client, correlation, parsing
                -> ElmTransport
```

Lower layers must not import feature or UI types. Feature code must not bypass a layer to access raw sockets, `ElmTransport`, or raw ELM commands.

## Layer Responsibilities

### Transport

Relevant types: `ElmTransport`, `WifiElmTransport`, and transport exceptions.

The transport layer owns:

- opening and closing the physical connection;
- sending one serialized command and reading its raw response through the terminating ELM prompt;
- transport timeouts and partial raw data;
- mapping platform/network failures to transport-level exceptions.

The transport layer must not:

- interpret OBD PIDs or ECU payloads;
- decide which commands to poll;
- own reconnect policy;
- contain feature logic or logic tied to a vehicle profile, brand, model, engine, or vehicle-specific configuration.

New connection types such as Bluetooth must implement `ElmTransport`. They must not create parallel OBD clients or duplicate parsing and recovery logic.

### OBD and ELM Client

Relevant types: `OBDClient`, `ElmCommand`, `OBDRequest`, `OBDResponseCorrelator`, `OBDDataMapper`, and OBD exceptions.

This layer owns:

- serializing ELM setup commands and typed OBD requests;
- ensuring exchanges are sequential;
- parsing raw adapter responses;
- correlating responses by service, PID, and ECU;
- rejecting stale or mismatched responses;
- bounded command-level recovery where explicitly designed;
- invalidating a transport that can no longer be trusted.

Raw command strings must not be scattered through services or features. Standard services and PIDs belong in typed request/catalog models. Do not duplicate a PID as both a command string and an independent numeric field when it can be derived from one source of truth.

A preferred ECU is a routing preference, not permission to discard a valid matching response from another ECU. Capability masks and responses from different ECUs must never be merged without retaining their ECU identity.

### Session Lifecycle

Relevant types: `OBDSessionManager`, `OBDSessionState`, and `VehicleCapabilities`.

This layer owns:

- initial connection and ELM initialization;
- the authoritative session state;
- classification of reconnectable and fatal failures;
- reconnect scheduling and backoff;
- supported-PID discovery and its cache lifetime;
- invalidating cached capabilities after repeated failed recovery.

There must be one connection/reconnect policy. Android services, polling code, and UI code must not implement independent retry loops.

Supported-PID discovery is performed for the active diagnostic session and stored per responding ECU. A short transport reconnect should restore adapter state and reuse cached capabilities. Discovery may be repeated after a new app/session start, vehicle or adapter change, protocol change, explicit refresh, or repeated recovery without valid data.

Fatal protocol/developer errors must remain visible and must not be retried forever. Recoverable transport and known adapter-interruption failures may reconnect according to session policy.

### Telemetry

Relevant types: `TelemetryMetric`, `PollingTarget`, `StandardPidCatalog`, and `TelemetryPollingEngine`.

This layer owns:

- declaring a metric, its typed request, and polling period;
- scheduling multiple targets through one serialized polling flow;
- skipping unsupported PIDs using current capabilities;
- selecting the preferred ECU reported for a PID;
- emitting a result that retains its target/metric identity.

To add a standard Mode 01 metric:

1. Add a named PID to `StandardPid`.
2. Add a typed `OBDRequest` derived from its service and PID.
3. Add a `TelemetryMetric` identity.
4. Add a `PollingTarget` to the appropriate catalog.
5. Add feature-layer decoding/mapping for the returned payload.
6. Add tests for request encoding, correlation/parsing, capability handling, and value decoding.

Do not add a dedicated polling loop or flow for every metric. Polling targets are data; `TelemetryPollingEngine` is the scheduler.

### Feature and UI

Relevant types include `ObdMonitorService` and `TelemetryResponseMapper`.

This layer owns:

- selecting the telemetry catalog required by the product;
- wiring transport, client, session manager, and polling engine;
- converting generic telemetry results into feature/domain values;
- updating trips, notifications, widgets, overlays, and UI state.

Feature code must not parse raw CAN frames, run supported-PID discovery, classify transport failures, or send commands directly.

## Response and Error Rules

- A request and response must be correlated before feature mapping.
- A stale response may be discarded and retried only in a bounded way.
- Repeated mismatch must surface as desynchronization and invalidate the session/transport.
- `NO DATA`, unsupported PID, negative response, CAN error, prompt timeout, interrupted command, malformed response, and connection loss are distinct outcomes.
- Do not turn all protocol exceptions into reconnectable errors. A narrow exception taxonomy keeps permanent bugs from becoming infinite retry loops.
- Cancellation must propagate and must not be converted into a reconnect request.

## Tests

Tests should live at the lowest layer responsible for the behavior:

- transport tests: framing, prompt handling, timeout, and platform error mapping;
- client/parser tests: encoding, parsing, correlation, stale responses, and bounded retry;
- session tests: initial retry, fatal-error stop, reconnect, discovery caching, and cache invalidation;
- telemetry tests: scheduling, unsupported-PID skipping, ECU selection, and metric identity;
- feature tests: conversion from typed responses to domain/UI values.

# OBD Architecture Quality Gate

Before completing an OBD-related change, verify all of the following:

- Transport-specific behavior remains behind `ElmTransport`.
- Command serialization and response correlation remain in `OBDClient` and its parser/correlator collaborators.
- Connection lifecycle, retry policy, and capability-cache lifetime remain in `OBDSessionManager`.
- Poll scheduling remains in `TelemetryPollingEngine` and targets remain declarative.
- Feature and UI code consume typed telemetry results and do not send raw ELM/OBD commands.
- Optional PIDs are checked through supported-PID discovery before polling.
- Capability data remains associated with the ECU that reported it.
- Reconnect paths do not repeat capability discovery after every short transport interruption.
- New behavior has tests at the lowest responsible layer, plus integration-style tests where layer interaction changes.
- No forbidden diagnostic command or guessed manufacturer-specific behavior was introduced.
