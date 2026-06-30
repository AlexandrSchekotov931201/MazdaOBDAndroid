# ELM Drive Lab

Read-only ELM327-style simulator for developing the Android application without a vehicle.

## Required Android build

Use the `simDebug` build variant with this simulator:

```powershell
.\gradlew.bat assembleSimDebug
```

The `sim` flavor connects to the simulator at `192.168.10.3:35000`. Do not use
`prodDebug` for simulator testing because the production flavor targets the
real Wi-Fi ELM adapter address.

## Run

```powershell
python -m pip install Flask
python elm_simulator/elm_sim_ui.py
```

Open `http://127.0.0.1:8080/`. The simulated Wi-Fi ELM transport listens on TCP port `35000`.

## Tests

```powershell
python -m unittest discover -s elm_simulator/tests -p "test_*.py"
```

The simulator implements standard read-only Mode 01 telemetry and fault injection only. It must not be extended with diagnostic write, reset, actuator-control, coding, or security-access commands.
