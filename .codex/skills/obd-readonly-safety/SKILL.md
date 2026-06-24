---
name: obd-readonly-safety
description: Project-local safety policy for OBD, ELM327, CAN, ECU diagnostic, PID, trip debug, and adapter communication work. Use whenever editing or reviewing code that sends vehicle diagnostic commands, changes OBD polling, adds supported-PID discovery, handles raw ECU responses, or proposes any OBD/CAN command behavior.
---

# OBD Read-only Safety

Diagnostic code must remain read-only with respect to any vehicle.

## Allowed

- Send standard read-only telemetry and discovery requests, such as Mode 01 RPM, temperature, and supported PID discovery.
- Configure the ELM adapter for read-only communication, such as echo, line feeds, headers, and protocol selection.
- Parse and log raw responses, negative responses, unsupported PIDs, timeouts, and transport errors.
- Reduce polling frequency or skip unsupported PIDs when that makes communication safer, quieter, or more reliable.

## Forbidden

- Do not send Mode 04 or any command that clears DTCs/errors.
- Do not send UDS session, security, write, routine-control, or output-control commands, including 10, 27, 2E, 31, and 3B.
- Do not write ECU parameters, coding, adaptations, calibration data, or stored settings.
- Do not run actuator tests, active tests, output-control routines, or service procedures.
- Do not add manufacturer-specific commands by guesswork.

## Required Behavior

- If a command is not clearly read-only, treat it as forbidden. Stop and ask before adding, changing, or testing it.
- Prefer supported-PID checks before polling optional PIDs.
- Prefer reducing or disabling noisy unsupported polling over retrying aggressively.
- Keep diagnostics clear enough to distinguish unsupported PID, negative ECU response, adapter timeout, and transport failure.
