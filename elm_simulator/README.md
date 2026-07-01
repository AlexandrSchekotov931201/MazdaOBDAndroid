# ELM Drive Lab

Read-only ELM327-style simulator for developing the Android application without a vehicle.

## Run

```powershell
python -m pip install Flask
python elm_simulator/elm_sim_ui.py
```

Open `http://127.0.0.1:8080/`. The simulated Wi-Fi ELM transport listens on TCP port `35000`.

## Connect the Android app

The simulator can be used to test any application build. In the app, enter the
IP address of the computer running the simulator and TCP port `35000`. Port
`8080` belongs only to the simulator web interface and is not an ELM endpoint.
Connection details can be changed later in the app settings.

## Tests

```powershell
python -m unittest discover -s elm_simulator/tests -p "test_*.py"
```

The simulator implements standard read-only Mode 01 telemetry and fault injection only. It must not be extended with diagnostic write, reset, actuator-control, coding, or security-access commands.
