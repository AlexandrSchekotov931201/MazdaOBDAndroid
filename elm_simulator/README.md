# ELM Drive Lab

Read-only ELM327-style simulator for developing the Android application without a vehicle.

## Required Android build

Use the standard `debug` build with this simulator:

```powershell
.\gradlew.bat assembleDebug
```

On the first app start, enter the simulator host address and port shown by the
simulator. The same values can be changed later in the app settings.

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
