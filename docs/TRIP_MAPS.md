# Trip maps

Trip maps are intentionally independent from the OBD transport. The recorder only consumes the
existing trip state, RPM, and coolant temperature snapshots. It must not add requests or change OBD
polling intervals.

## Local setup

Set `MAPS_API_KEY` in the user Gradle properties (`~/.gradle/gradle.properties`) or as an environment
variable. Never commit a key. Without a key, route recording and statistics continue to work, while
the map screen shows a configuration placeholder instead of Google map tiles.

## First iteration behavior

- Recording is opt-in and starts only after foreground location permission is granted.
- A missing or revoked permission disables route recording without affecting the OBD trip.
- Points with accuracy worse than 100 meters are ignored.
- Every recorder restart creates a new segment so the UI never draws a fabricated line across a gap.
- Data stays in the local `trip_routes.db` database and can be deleted per trip.
- No background-location permission is requested. Recording can continue in the location foreground
  service after it is enabled from the visible app, but automatic location startup after reboot is not
  part of this iteration.
