# OBD/ELM Safety Rules

Code in this package talks directly to the OBD adapter. Apply the project-level vehicle diagnostic safety rules from the repository root.

This package must stay read-only with respect to the vehicle.

Allowed here:
- Standard read-only telemetry and supported-PID requests.
- ELM adapter setup required for read-only communication.
- Logging, parsing, timeout handling, and reconnect behavior.

Forbidden here:
- DTC clearing, ECU writes, coding, adaptations, actuator tests, active tests, security/session changes, and manufacturer-specific commands added by guesswork.

If a command is not clearly read-only, do not add it.
