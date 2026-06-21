import socket
import threading
import time
import random
import json
import queue
from flask import Flask, request, jsonify, Response

TCP_HOST = "0.0.0.0"
TCP_PORT = 35000

WEB_HOST = "0.0.0.0"
WEB_PORT = 8080

# Shared state for UI + TCP server behavior
state = {
    "ignition": True,
    "rpm": 850,
    "coolant_temp": 40,

    # Connection/testing controls:
    "mute_responses": False,      # if True -> do not send any replies (simulate "adapter stopped responding")
    "drop_connections": False,    # if True -> close socket on next received command (simulate sudden disconnect)
    "response_delay_ms": 0,       # artificial delay before sending response (0..5000 ms)

    # Bad/garbage response simulation:
    "response_mode": "normal",    # normal | garbage | wrong_can | malformed | random
    "garbage_every_n": 0,         # 0 = never, 1 = always, 5 = every 5th request
    "garbage_only_010c": True,    # apply garbage only to 010C
    "request_counter": 0,         # internal counter

    # RPM test
    "rpm_test_running": False,
}
state_lock = threading.Lock()

# Track active TCP connections so we can drop them from UI
connections = set()
connections_lock = threading.Lock()

app = Flask(__name__)

# --- SSE subscribers (no polling UI updates) ---
subscribers_lock = threading.Lock()
subscribers = set()  # set[queue.Queue]

def notify_state():
    """Push current state snapshot to all SSE subscribers."""
    with state_lock:
        snap = dict(state)
    with connections_lock:
        snap["active_clients"] = len(connections)

    payload = json.dumps(snap, ensure_ascii=False)
    dead = []

    with subscribers_lock:
        for q in list(subscribers):
            try:
                # drop if queue is full / client too slow
                q.put_nowait(payload)
            except Exception:
                dead.append(q)
        for q in dead:
            subscribers.discard(q)

HTML = """
<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <title>ELM Simulator</title>
  <style>
    body { font-family: sans-serif; margin: 24px; }
    .row { margin: 12px 0; }
    button { padding: 10px 14px; margin-right: 8px; }
    input[type=range] { width: 360px; }
    .box { padding: 12px; border: 1px solid #ddd; border-radius: 10px; display: inline-block; }
    code { background: #f6f6f6; padding: 2px 6px; border-radius: 6px; }
    .muted { color: #b00; font-weight: bold; }
    select { padding: 6px 8px; }
    .hint { color: #666; font-size: 12px; }
    .ok { color: #0a7; font-weight: bold; }
    .warn { color: #b00; font-weight: bold; }
  </style>
</head>
<body>
  <h2>ELM Simulator UI</h2>

  <div class="box">
    <div class="row">
      Ignition: <b id="ign">?</b>
    </div>

    <div class="row">
      RPM: <b id="rpm">?</b>
    </div>

    <div class="row">
      Coolant temp: <b id="coolantTemp">?</b> C
    </div>

    <div class="row">
      <button onclick="setIgn(true)">Ignition ON</button>
      <button onclick="setIgn(false)">Ignition OFF</button>
      <button onclick="syncState()" style="margin-left:8px;">Sync</button>
    </div>

    <div class="row">
      <input id="rpmRange" type="range" min="0" max="8000" step="50"
             oninput="setRpmLocal(this.value)"
             onchange="commitRpm(this.value)">
      <span id="rpmVal"></span>
    </div>

    <div class="row">
      <input id="coolantTempRange" type="range" min="-20" max="130" step="1"
             oninput="setCoolantTempLocal(this.value)"
             onchange="commitCoolantTemp(this.value)">
      <span id="coolantTempVal"></span>
    </div>
    <div class="row hint">Слайдер обновляет UI сразу. На сервер RPM отправляется при отпускании (onchange).</div>

    <hr/>

    <div class="row">
      <b>RPM Test:</b>
      <button onclick="startRpmTest()">Start</button>
      <button onclick="stopRpmTest()">Stop</button>
      <span id="rpmTestStatus" class="hint"></span>
    </div>
    <div class="row hint">Тест: плавный набор → резкий сброс (как переключение передачи) → повтор + газовки.</div>

    <hr/>

    <div class="row">
      Active TCP clients: <b id="clients">0</b>
      <span class="hint">(обновится автоматически через SSE или при Sync)</span>
    </div>

    <div class="row">
      <button onclick="dropNow()">Drop connection NOW</button>
      <label style="margin-left:8px;">
        <input id="dropModeChk" type="checkbox" onchange="setDropMode(this.checked)">
        Drop on next command
      </label>
    </div>

    <div class="row">
      <label>
        <input id="muteChk" type="checkbox" onchange="setMute(this.checked)">
        <span id="muteLabel">Mute responses (no reply)</span>
      </label>
    </div>

    <div class="row">
      Delay: <b id="delay">0</b> ms
    </div>
    <div class="row">
      <input id="delayRange" type="range" min="0" max="5000" step="100"
             oninput="setDelayLocal(this.value)"
             onchange="commitDelay(this.value)">
      <span id="delayVal"></span>
    </div>
    <div class="row hint">Delay применяется к ответам TCP. На сервер отправляется при отпускании (onchange).</div>

    <hr/>

    <div class="row">
      Response mode:
      <select id="modeSel" onchange="setMode(this.value)">
        <option value="normal">normal</option>
        <option value="garbage">garbage text</option>
        <option value="wrong_can">wrong CAN id</option>
        <option value="malformed">malformed line</option>
        <option value="random">random mix</option>
      </select>

      <button onclick="resetCounter()" style="margin-left:8px;">Reset counter</button>
    </div>

    <div class="row">
      Garbage every N requests: <b id="everyN">0</b>
    </div>
    <div class="row">
      <input id="everyNRange" type="range" min="0" max="20" step="1"
             oninput="setEveryNLocal(this.value)"
             onchange="commitEveryN(this.value)">
      <span id="everyNVal"></span>
    </div>

    <div class="row">
      <label>
        <input id="only010cChk" type="checkbox" onchange="setOnly010c(this.checked)">
        Apply only to 010C
      </label>
    </div>

    <div class="row">
      <small>
        Android клиент получает ответы на <code>010C</code> как от ELM (CAN 7E8) когда ignition ON.<br/>
        <span class="muted">Mute</span> = не отвечаем (симулируем "адаптер перестал отдавать ответы").<br/>
        <span class="muted">Drop</span> = разрыв TCP (симулируем "соединение оборвалось").<br/>
        Bad responses = левые ответы для теста парсинга/логики (Unrecognized/ProtocolException/etc).<br/>
        UI обновляется без polling через <span class="ok">SSE</span> (/api/events).
      </small>
    </div>
  </div>

<script>
let localState = null;
let es = null;

function applyStateToUi(s) {
  document.getElementById('ign').textContent = s.ignition ? 'ON' : 'OFF';
  document.getElementById('rpm').textContent = s.rpm;
  document.getElementById('coolantTemp').textContent = s.coolant_temp;

  const slider = document.getElementById('rpmRange');
  slider.value = s.rpm;
  document.getElementById('rpmVal').textContent = s.rpm;

  const tempSlider = document.getElementById('coolantTempRange');
  tempSlider.value = s.coolant_temp;
  document.getElementById('coolantTempVal').textContent = s.coolant_temp + " C";

  document.getElementById('muteChk').checked = !!s.mute_responses;
  document.getElementById('dropModeChk').checked = !!s.drop_connections;

  document.getElementById('delay').textContent = s.response_delay_ms;
  const d = document.getElementById('delayRange');
  d.value = s.response_delay_ms;
  document.getElementById('delayVal').textContent = s.response_delay_ms;

  document.getElementById('modeSel').value = s.response_mode || "normal";

  document.getElementById('everyN').textContent = s.garbage_every_n || 0;
  const en = document.getElementById('everyNRange');
  en.value = s.garbage_every_n || 0;
  document.getElementById('everyNVal').textContent = s.garbage_every_n || 0;

  document.getElementById('only010cChk').checked = !!s.garbage_only_010c;
  document.getElementById('clients').textContent = s.active_clients || 0;

  const muteLabel = document.getElementById('muteLabel');
  if (s.mute_responses) muteLabel.classList.add('muted');
  else muteLabel.classList.remove('muted');

  const st = document.getElementById('rpmTestStatus');
  if (s.rpm_test_running) {
    st.textContent = "running";
    st.className = "ok";
  } else {
    st.textContent = "stopped";
    st.className = "hint";
  }
}

async function syncState() {
  const r = await fetch('/api/state');
  localState = await r.json();
  applyStateToUi(localState);
}

// -------- SSE: receive server updates (no polling) --------
function connectEvents() {
  if (es) { try { es.close(); } catch(e) {} }
  es = new EventSource('/api/events');

  es.onmessage = (ev) => {
    try {
      const s = JSON.parse(ev.data);
      localState = s;
      applyStateToUi(s);
    } catch (e) {
      console.log("SSE parse error", e);
    }
  };

  es.onerror = () => {
    // Browser will auto-reconnect. We can show minimal hint in console.
    console.log("SSE error / reconnecting...");
  };
}

// -------- actions (optimistic UI for manual controls) --------

async function setIgn(v) {
  if (!localState) await syncState();
  localState.ignition = v;
  if (!v) localState.rpm = 0;
  applyStateToUi(localState);

  await fetch('/api/ignition', {
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body: JSON.stringify({ignition:v})
  });
}

function setRpmLocal(v) {
  const rpm = parseInt(v);
  localState.rpm = rpm;
  if (rpm > 0) localState.ignition = true;
  applyStateToUi(localState);
}

async function commitRpm(v) {
  await fetch('/api/rpm', {
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body: JSON.stringify({rpm: parseInt(v)})
  });
}

function setCoolantTempLocal(v) {
  const temp = parseInt(v);
  localState.coolant_temp = temp;
  applyStateToUi(localState);
}

async function commitCoolantTemp(v) {
  await fetch('/api/coolant_temp', {
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body: JSON.stringify({coolant_temp: parseInt(v)})
  });
}

async function setMute(v) {
  localState.mute_responses = v;
  applyStateToUi(localState);

  await fetch('/api/mute', {
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body: JSON.stringify({mute:v})
  });
}

async function setDropMode(v) {
  localState.drop_connections = v;
  applyStateToUi(localState);

  await fetch('/api/drop_mode', {
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body: JSON.stringify({drop:v})
  });
}

function setDelayLocal(v) {
  localState.response_delay_ms = parseInt(v);
  applyStateToUi(localState);
}

async function commitDelay(v) {
  await fetch('/api/delay', {
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body: JSON.stringify({ms: parseInt(v)})
  });
}

async function dropNow() {
  await fetch('/api/drop', {method:'POST'});
  await syncState();
}

async function setMode(mode) {
  localState.response_mode = mode;
  applyStateToUi(localState);

  await fetch('/api/response_mode', {
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body: JSON.stringify({mode})
  });
}

function setEveryNLocal(v) {
  localState.garbage_every_n = parseInt(v);
  applyStateToUi(localState);
}

async function commitEveryN(v) {
  await fetch('/api/garbage_rate', {
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body: JSON.stringify({every_n: parseInt(v)})
  });
}

async function setOnly010c(v) {
  localState.garbage_only_010c = v;
  applyStateToUi(localState);

  await fetch('/api/garbage_only_010c', {
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body: JSON.stringify({only_010c: v})
  });
}

async function resetCounter() {
  await fetch('/api/reset_counter', {method:'POST'});
  localState.request_counter = 0;
  applyStateToUi(localState);
}

// RPM test buttons
async function startRpmTest() {
  await fetch('/api/rpm_test/start', {method:'POST'});
}

async function stopRpmTest() {
  await fetch('/api/rpm_test/stop', {method:'POST'});
}

// Initial load
syncState();
connectEvents();
</script>
</body>
</html>
"""

@app.get("/")
def index():
    return HTML

@app.get("/api/events")
def sse_events():
    q = queue.Queue(maxsize=100)
    with subscribers_lock:
        subscribers.add(q)

    # Push current state immediately
    notify_state()

    def gen():
        try:
            # initial comment for SSE
            yield ": connected\n\n"
            while True:
                try:
                    msg = q.get(timeout=15)
                    yield f"data: {msg}\n\n"
                except queue.Empty:
                    # keep-alive ping
                    yield ": ping\n\n"
        finally:
            with subscribers_lock:
                subscribers.discard(q)

    return Response(gen(), mimetype="text/event-stream", headers={
        "Cache-Control": "no-cache",
        "X-Accel-Buffering": "no",
    })

@app.get("/api/state")
def get_state():
    with state_lock:
        snap = dict(state)
    with connections_lock:
        snap["active_clients"] = len(connections)
    return jsonify(snap)

@app.post("/api/ignition")
def set_ignition():
    data = request.get_json(force=True)
    with state_lock:
        state["ignition"] = bool(data.get("ignition", False))
        if not state["ignition"]:
            state["rpm"] = 0
    notify_state()
    return jsonify(ok=True)

@app.post("/api/rpm")
def set_rpm():
    data = request.get_json(force=True)
    rpm = int(data.get("rpm", 0))
    rpm = max(0, min(8000, rpm))
    with state_lock:
        state["rpm"] = rpm
        if rpm > 0:
            state["ignition"] = True
    notify_state()
    return jsonify(ok=True)

@app.post("/api/coolant_temp")
def set_coolant_temp():
    data = request.get_json(force=True)
    temp = int(data.get("coolant_temp", 0))
    temp = max(-20, min(130, temp))
    with state_lock:
        state["coolant_temp"] = temp
    notify_state()
    return jsonify(ok=True)

@app.post("/api/mute")
def set_mute():
    data = request.get_json(force=True)
    with state_lock:
        state["mute_responses"] = bool(data.get("mute", False))
    notify_state()
    return jsonify(ok=True)

@app.post("/api/delay")
def set_delay():
    data = request.get_json(force=True)
    ms = int(data.get("ms", 0))
    ms = max(0, min(5000, ms))
    with state_lock:
        state["response_delay_ms"] = ms
    notify_state()
    return jsonify(ok=True)

@app.post("/api/drop_mode")
def set_drop_mode():
    data = request.get_json(force=True)
    with state_lock:
        state["drop_connections"] = bool(data.get("drop", False))
    notify_state()
    return jsonify(ok=True)

@app.post("/api/drop")
def drop_now():
    # Close all active connections immediately (one-shot drop)
    with connections_lock:
        conns = list(connections)
        connections.clear()

    dropped = 0
    for c in conns:
        try:
            c.shutdown(socket.SHUT_RDWR)
        except Exception:
            pass
        try:
            c.close()
            dropped += 1
        except Exception:
            pass

    # Reset drop mode (safe default)
    with state_lock:
        state["drop_connections"] = False

    notify_state()
    return jsonify(ok=True, dropped=dropped)

@app.post("/api/response_mode")
def set_response_mode():
    data = request.get_json(force=True)
    mode = str(data.get("mode", "normal"))
    allowed = {"normal", "garbage", "wrong_can", "malformed", "random"}
    if mode not in allowed:
        mode = "normal"
    with state_lock:
        state["response_mode"] = mode
    notify_state()
    return jsonify(ok=True)

@app.post("/api/garbage_rate")
def set_garbage_rate():
    data = request.get_json(force=True)
    n = int(data.get("every_n", 0))
    n = max(0, min(50, n))
    with state_lock:
        state["garbage_every_n"] = n
    notify_state()
    return jsonify(ok=True)

@app.post("/api/garbage_only_010c")
def set_garbage_only_010c():
    data = request.get_json(force=True)
    with state_lock:
        state["garbage_only_010c"] = bool(data.get("only_010c", True))
    notify_state()
    return jsonify(ok=True)

@app.post("/api/reset_counter")
def reset_counter():
    with state_lock:
        state["request_counter"] = 0
    notify_state()
    return jsonify(ok=True)

# --- RPM test runner ---
rpm_test_stop = threading.Event()
rpm_test_thread = None
rpm_test_thread_lock = threading.Lock()

def set_rpm_internal(rpm: int, ignition: bool = True):
    rpm = max(0, min(8000, int(rpm)))
    with state_lock:
        state["rpm"] = rpm
        state["ignition"] = ignition if rpm > 0 else False
    notify_state()

def ramp_rpm(from_rpm: int, to_rpm: int, duration_s: float, step_ms: int = 60):
    steps = max(1, int(duration_s * 1000 / step_ms))
    for i in range(steps + 1):
        if rpm_test_stop.is_set():
            return
        t = i / steps
        cur = int(from_rpm + (to_rpm - from_rpm) * t)
        set_rpm_internal(cur, ignition=True)
        time.sleep(step_ms / 1000.0)

def rpm_test_loop():
    try:
        with state_lock:
            state["rpm_test_running"] = True
            state["ignition"] = True
            if state["rpm"] <= 0:
                state["rpm"] = 850
        notify_state()

        base_idle = 850

        while not rpm_test_stop.is_set():
            # 1) smooth accelerate: idle -> ~3500
            ramp_rpm(base_idle, 3500, duration_s=2.2)

            # hold a bit
            for _ in range(10):
                if rpm_test_stop.is_set():
                    break
                time.sleep(0.08)

            # 2) gear shift drop: 3500 -> 2200 fast
            ramp_rpm(3500, 2200, duration_s=0.35, step_ms=35)

            # 3) accelerate again: 2200 -> 4800
            ramp_rpm(2200, 4800, duration_s=1.6)

            # 4) another shift drop: 4800 -> 2800
            ramp_rpm(4800, 2800, duration_s=0.40, step_ms=35)

            # 5) a couple of throttle blips
            for _ in range(2):
                if rpm_test_stop.is_set():
                    break
                blip_top = random.randint(3500, 5500)
                ramp_rpm(2800, blip_top, duration_s=0.5, step_ms=40)
                ramp_rpm(blip_top, 2400, duration_s=0.45, step_ms=40)

            # back to idle-ish
            ramp_rpm(2400, base_idle, duration_s=1.2)

            # small idle wobble
            for _ in range(25):
                if rpm_test_stop.is_set():
                    break
                wobble = base_idle + random.randint(-40, 80)
                set_rpm_internal(wobble, ignition=True)
                time.sleep(0.08)

    finally:
        with state_lock:
            state["rpm_test_running"] = False
        notify_state()
        rpm_test_stop.clear()

@app.post("/api/rpm_test/start")
def start_rpm_test():
    global rpm_test_thread
    with rpm_test_thread_lock:
        if rpm_test_thread and rpm_test_thread.is_alive():
            return jsonify(ok=True, already_running=True)

        rpm_test_stop.clear()
        rpm_test_thread = threading.Thread(target=rpm_test_loop, daemon=True)
        rpm_test_thread.start()

    return jsonify(ok=True)

@app.post("/api/rpm_test/stop")
def stop_rpm_test():
    rpm_test_stop.set()
    # not joining here (daemon thread), it will exit quickly
    return jsonify(ok=True)

def elm_prompt(conn, payload: str):
    # Must end with '>' because Android client reads until '>'
    conn.sendall((payload + "\r\r>").encode("ascii", errors="ignore"))

def garbage_response(mode: str) -> str:
    # NOTE: elm_prompt will add \r\r> at the end
    if mode == "garbage":
        return "THIS IS GARBAGE!!! ### ???"
    if mode == "wrong_can":
        # Looks similar, but CAN id is not 7E8
        return "7E9 04 41 0C 00 10"
    if mode == "malformed":
        # Broken tokens / non-hex
        return "7E8 ZZ 41 0C GG HH"
    if mode == "random":
        return garbage_response(random.choice(["garbage", "wrong_can", "malformed"]))
    return "NO DATA"

def should_inject_garbage(normalized_cmd: str) -> bool:
    with state_lock:
        mode = state["response_mode"]
        every_n = state["garbage_every_n"]
        only_010c = state["garbage_only_010c"]
        state["request_counter"] += 1
        cnt = state["request_counter"]

    if mode == "normal" or every_n <= 0:
        return False
    if cnt % every_n != 0:
        return False
    if only_010c and normalized_cmd != "010C":
        return False
    return True

def handle_command(cmd: str) -> str:
    c = cmd.strip().upper().replace(" ", "")
    if not c:
        return ""

    # Inject garbage responses (controlled from UI)
    if should_inject_garbage(c):
        with state_lock:
            mode = state["response_mode"]
        return garbage_response(mode)

    # ELM init sequence (expand as needed)
    if c in ("ATZ", "ATE0", "ATL0", "ATS0", "ATH1", "ATSP0"):
        return "OK"

    # RPM request (01 0C)
    if c == "010C":
        with state_lock:
            ign = state["ignition"]
            rpm = state["rpm"]

        if not ign:
            return "NO DATA"

        value = int(rpm) * 4
        A = (value >> 8) & 0xFF
        B = value & 0xFF
        return f"7E8 04 41 0C {A:02X} {B:02X}"

    # Coolant temperature request (01 05), A - 40
    if c == "0105":
        with state_lock:
            ign = state["ignition"]
            coolant_temp = state["coolant_temp"]

        if not ign:
            return "NO DATA"

        A = max(0, min(255, int(coolant_temp) + 40))
        return f"7E8 03 41 05 {A:02X}"

    return "NO DATA"

def tcp_client_thread(conn, addr):
    print("TCP client connected:", addr)

    with connections_lock:
        connections.add(conn)
    notify_state()

    buf = ""
    try:
        while True:
            data = conn.recv(1024)
            if not data:
                break

            buf += data.decode("ascii", errors="ignore")
            while "\r" in buf:
                cmd, buf = buf.split("\r", 1)
                if cmd.strip():
                    print("CMD:", repr(cmd))

                # Snapshot flags atomically
                with state_lock:
                    mute = state["mute_responses"]
                    drop_mode = state["drop_connections"]
                    delay_ms = state["response_delay_ms"]

                # Drop on next command (simulate sudden disconnect)
                if drop_mode:
                    print("DROP_MODE: closing connection")
                    try:
                        conn.shutdown(socket.SHUT_RDWR)
                    except Exception:
                        pass
                    conn.close()
                    return

                # Mute: do not reply at all (simulate adapter stopped responding)
                if mute:
                    print("MUTE: ignoring command (no response)")
                    continue

                resp = handle_command(cmd)

                if delay_ms > 0:
                    print(f"DELAY: {delay_ms} ms")
                    time.sleep(delay_ms / 1000.0)

                if resp != "":
                    print("RESP:", resp)
                    elm_prompt(conn, resp)
                else:
                    elm_prompt(conn, "")

    except Exception as e:
        print("TCP client error:", e)
    finally:
        with connections_lock:
            connections.discard(conn)
        notify_state()
        try:
            conn.close()
        except Exception:
            pass
        print("TCP client disconnected:", addr)

def run_tcp_server():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind((TCP_HOST, TCP_PORT))
        s.listen(5)
        print(f"ELM TCP listening on {TCP_HOST}:{TCP_PORT}")
        while True:
            conn, addr = s.accept()
            threading.Thread(target=tcp_client_thread, args=(conn, addr), daemon=True).start()

def run_web():
    print(f"Web UI: http://{WEB_HOST}:{WEB_PORT}")
    app.run(host=WEB_HOST, port=WEB_PORT, debug=False, threaded=True)

if __name__ == "__main__":
    threading.Thread(target=run_tcp_server, daemon=True).start()
    run_web()
