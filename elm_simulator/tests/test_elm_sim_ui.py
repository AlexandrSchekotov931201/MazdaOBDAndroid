import time
import unittest

from elm_simulator import elm_sim_ui as simulator


class ElmSimulatorTest(unittest.TestCase):
    def setUp(self):
        self.client = simulator.app.test_client()
        simulator.scenario_stop.clear()
        with simulator.state_lock:
            simulator.state["ignition"] = True
            simulator.state["rpm"] = 850
            simulator.state["coolant_temp"] = 40
            simulator.state["one_shot_response_mode"] = "normal"

    def tearDown(self):
        simulator.scenario_stop.set()
        time.sleep(0.15)

    def test_catalog_exposes_guided_drive_scenarios(self):
        scenarios = self.client.get("/api/scenarios").get_json()

        self.assertEqual(6, len(scenarios))
        self.assertIn("cold_high_rpm", {scenario["id"] for scenario in scenarios})
        self.assertTrue(all(scenario["duration_s"] > 0 for scenario in scenarios))

    def test_scenario_can_start_pause_resume_and_stop(self):
        self.assertEqual(200, self.client.post("/api/scenarios/cold_high_rpm/start").status_code)
        time.sleep(0.25)

        running = self.client.get("/api/state").get_json()
        self.assertTrue(running["scenario_running"])
        self.assertEqual("cold_high_rpm", running["scenario_id"])

        self.client.post("/api/scenarios/pause")
        self.assertTrue(self.client.get("/api/state").get_json()["scenario_paused"])
        self.client.post("/api/scenarios/resume")
        self.assertFalse(self.client.get("/api/state").get_json()["scenario_paused"])

        self.client.post("/api/scenarios/stop")
        time.sleep(0.2)
        self.assertFalse(self.client.get("/api/state").get_json()["scenario_running"])

    def test_read_only_obd_responses_remain_available(self):
        self.assertEqual("7E8 06 41 00 08 10 00 00", simulator.handle_command("0100"))
        self.assertTrue(simulator.handle_command("010C").startswith("7E8 04 41 0C"))
        self.assertTrue(simulator.handle_command("0105").startswith("7E8 03 41 05"))

    def test_unknown_scenario_is_rejected(self):
        self.assertEqual(404, self.client.post("/api/scenarios/not-a-scenario/start").status_code)

    def test_vehicle_can_be_prepared_in_engine_off_state(self):
        self.client.post(
            "/api/warmup_preset",
            json={"ignition": False, "rpm": 0, "coolant_temp": 20},
        )

        parked = self.client.get("/api/state").get_json()
        self.assertFalse(parked["ignition"])
        self.assertEqual(0, parked["rpm"])
        self.assertEqual(20, parked["coolant_temp"])

    def test_completed_scenario_reports_zero_rpm_then_turns_ignition_off(self):
        scenario_id = "test_trip_completion"
        original_finish_delay = simulator.TRIP_FINISH_SIGNAL_SECONDS
        simulator.SCENARIOS[scenario_id] = {
            "name": "Test completion",
            "description": "Test only",
            "accent": "green",
            "steps": [
                {"label": "Driving", "duration": 0.01, "ignition": True, "rpm": 1500, "coolant": 80},
            ],
        }
        simulator.TRIP_FINISH_SIGNAL_SECONDS = 0.01
        simulator.scenario_stop.clear()
        with simulator.state_lock:
            simulator.state["scenario_paused"] = False
            simulator.state["scenario_speed"] = 5.0

        try:
            simulator.scenario_loop(scenario_id)
        finally:
            simulator.TRIP_FINISH_SIGNAL_SECONDS = original_finish_delay
            simulator.SCENARIOS.pop(scenario_id)

        final_state = self.client.get("/api/state").get_json()
        self.assertFalse(final_state["ignition"])
        self.assertEqual(0, final_state["rpm"])
        self.assertEqual("Trip completed · engine off", final_state["scenario_stage"])

    def test_obd_fault_does_not_corrupt_elm_initialization(self):
        self.client.post("/api/debug_fault", json={"mode": "no_data", "only_010c": False})

        self.assertEqual("ELM327 v1.5", simulator.handle_command("ATZ"))
        self.assertEqual("NO DATA", simulator.handle_command("010C"))


if __name__ == "__main__":
    unittest.main()
