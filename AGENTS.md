# Agent Workflow

Before starting any task:

- Check the available project and system skills first.
- If any skill matches the request, even partially, use it immediately.
- Read all documentation and local instruction files related to the task, including referenced documents.
- Follow the relevant documentation and skill instructions precisely.
- If no skill matches, inspect the relevant code and local docs before writing or changing code.
- Do not skip safety, validation, or quality gates described by matching skills or project docs.

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
