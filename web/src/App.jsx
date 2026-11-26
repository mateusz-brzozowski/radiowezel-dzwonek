import { useEffect, useMemo, useRef, useState } from "react";
import {
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut,
} from "firebase/auth";
import { onValue, push, ref, set } from "firebase/database";
import {
  auth,
  clearInsecureBrowserSecrets,
  db,
  hardenAuthSession,
  isFirebaseConfigured,
} from "./firebase.js";

const IDLE_MS = 12 * 60 * 1000;

const ALARMS = [
  { id: "test", label: "Test systemu" },
  { id: "evac_fire", label: "Pożar / ewakuacja" },
  { id: "bomb", label: "Alarm bombowy" },
  { id: "lockdown", label: "Blokada szkoły" },
  { id: "shelter", label: "Schronienie w budynku" },
  { id: "air_raid", label: "Alarm powietrzny" },
  { id: "all_clear", label: "Odwołanie alarmu" },
];

const TABS = [
  { id: "status", label: "Status" },
  { id: "schedule", label: "Plan" },
  { id: "tracks", label: "Utwory" },
  { id: "holidays", label: "Święta" },
  { id: "alarms", label: "Alarmy" },
];

function uid() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function minutesToLabel(m) {
  const h = Math.floor(m / 60);
  const min = m % 60;
  return `${String(h).padStart(2, "0")}:${String(min).padStart(2, "0")}`;
}

function labelToMinutes(label) {
  const [h, m] = label.split(":").map((x) => parseInt(x, 10));
  if (Number.isNaN(h) || Number.isNaN(m)) return null;
  return h * 60 + m;
}

function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = String(reader.result || "");
      const idx = result.indexOf(",");
      resolve(idx >= 0 ? result.slice(idx + 1) : result);
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

export default function App() {
  const [user, setUser] = useState(null);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [deviceId, setDeviceId] = useState("");
  const [remoteToken, setRemoteToken] = useState("");
  const [pin, setPin] = useState("");
  const [alarmPin, setAlarmPin] = useState("");
  const [drill, setDrill] = useState(true);
  const [presence, setPresence] = useState(null);
  const [state, setState] = useState(null);
  const [ack, setAck] = useState(null);
  const [error, setError] = useState("");
  const [info, setInfo] = useState("");
  const [busy, setBusy] = useState(false);
  const [tab, setTab] = useState("status");
  const idleTimer = useRef(null);

  const [eventTime, setEventTime] = useState("08:15");
  const [eventType, setEventType] = useState("BREAK_START");
  const [eventTrackId, setEventTrackId] = useState("");
  const [eventDuration, setEventDuration] = useState(60);
  const [editingEventId, setEditingEventId] = useState(null);

  const [holidayDate, setHolidayDate] = useState("");
  const [holidayName, setHolidayName] = useState("");

  const [trackName, setTrackName] = useState("");
  const [trackUrl, setTrackUrl] = useState("");
  const [trackFile, setTrackFile] = useState(null);

  useEffect(() => {
    clearInsecureBrowserSecrets();
    hardenAuthSession().catch(() => {});
    if (!auth) return undefined;
    return onAuthStateChanged(auth, setUser);
  }, []);

  useEffect(() => {
    if (!user) return undefined;
    const bump = () => {
      if (idleTimer.current) clearTimeout(idleTimer.current);
      idleTimer.current = setTimeout(() => {
        signOut(auth).catch(() => {});
        setInfo("Wylogowano po bezczynności (12 min).");
      }, IDLE_MS);
    };
    const events = ["mousemove", "keydown", "click", "touchstart", "scroll"];
    events.forEach((ev) => window.addEventListener(ev, bump, { passive: true }));
    bump();
    return () => {
      events.forEach((ev) => window.removeEventListener(ev, bump));
      if (idleTimer.current) clearTimeout(idleTimer.current);
    };
  }, [user]);

  useEffect(() => {
    if (!db || !user || !deviceId.trim()) {
      setPresence(null);
      setState(null);
      setAck(null);
      return undefined;
    }
    const base = `devices/${deviceId.trim()}`;
    const unsubs = [
      onValue(ref(db, `${base}/presence`), (snap) => setPresence(snap.val())),
      onValue(ref(db, `${base}/state`), (snap) => setState(snap.val())),
      onValue(ref(db, `${base}/ack`), (snap) => setAck(snap.val())),
    ];
    return () => unsubs.forEach((u) => u());
  }, [user, deviceId]);

  const online = Boolean(presence?.online);
  const nextBell = presence?.nextBell || "—";
  const events = Array.isArray(state?.events) ? state.events : [];
  const tracks = Array.isArray(state?.tracks) ? state.tracks : [];
  const holidays = Array.isArray(state?.holidays) ? state.holidays : [];

  const statusLabel = useMemo(() => {
    if (!deviceId.trim()) return "Podaj Device ID tabletu";
    if (!presence) return "Brak danych presence — włącz Firebase na tablecie";
    return online ? "Tablet online" : "Tablet offline";
  }, [deviceId, presence, online]);

  async function login(e) {
    e.preventDefault();
    setError("");
    if (!auth) {
      setError("Brak konfiguracji Firebase (.env)");
      return;
    }
    setBusy(true);
    try {
      await hardenAuthSession();
      await signInWithEmailAndPassword(auth, email.trim(), password);
      setPassword("");
      setEmail("");
    } catch (err) {
      setPassword("");
      setError("Logowanie nieudane. Sprawdź e-mail i hasło.");
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    setRemoteToken("");
    setAlarmPin("");
    setPin("");
    setDeviceId("");
    if (auth) await signOut(auth);
  }

  async function sendCommand(payload, { requirePin = true } = {}) {
    setError("");
    setInfo("");
    if (!db || !user) {
      setError("Zaloguj się");
      return;
    }
    if (!deviceId.trim()) {
      setError("Uzupełnij Device ID");
      return;
    }
    if (remoteToken.trim().length < 16) {
      setError("Wklej kod parowania z tabletu (Ustawienia)");
      return;
    }
    if (requirePin) {
      if (alarmPin.length < 4 || pin !== alarmPin) {
        setError("Podaj poprawny PIN alarmowy (potwierdzenie akcji)");
        return;
      }
    }
    const commandId = uid();
    const body = {
      id: commandId,
      issuedAt: Date.now(),
      issuedBy: user.email || "unknown",
      remoteToken: remoteToken.trim(),
      ...payload,
    };
    setBusy(true);
    try {
      await set(ref(db, `devices/${deviceId.trim()}/command`), body);
      const { remoteToken: _t, base64: _b, url: _u, ...auditSafe } = body;
      await push(ref(db, "audit"), {
        ...auditSafe,
        deviceId: deviceId.trim(),
        at: Date.now(),
        source: "web",
      });
      setInfo(`Wysłano: ${payload.type}`);
      if (requirePin) setPin("");
    } catch (err) {
      setError(err.message || "Nie udało się wysłać komendy");
    } finally {
      setBusy(false);
    }
  }

  async function saveEvent(e) {
    e.preventDefault();
    const minutes = labelToMinutes(eventTime);
    if (minutes == null) {
      setError("Zła godzina (HH:MM)");
      return;
    }
    await sendCommand({
      type: "upsert_event",
      eventId: editingEventId || 0,
      timeMinutes: minutes,
      bellType: eventType,
      trackId: eventTrackId ? Number(eventTrackId) : null,
      durationSeconds: Number(eventDuration) || 60,
      enabled: true,
    });
    setEditingEventId(null);
  }

  function editEvent(ev) {
    setEditingEventId(ev.id);
    setEventTime(minutesToLabel(ev.timeMinutes));
    setEventType(ev.type || "BREAK_START");
    setEventTrackId(ev.trackId ? String(ev.trackId) : "");
    setEventDuration(ev.durationSeconds || 60);
    setTab("schedule");
  }

  async function saveHoliday(e) {
    e.preventDefault();
    if (!holidayDate) {
      setError("Podaj datę");
      return;
    }
    await sendCommand({
      type: "upsert_holiday",
      holidayId: 0,
      date: holidayDate,
      name: holidayName || "Dzień wolny",
    });
    setHolidayDate("");
    setHolidayName("");
  }

  async function importTrack(e) {
    e.preventDefault();
    const name = trackName.trim() || trackFile?.name || "utwor.mp3";
    if (trackFile) {
      if (trackFile.size > 900_000) {
        setError("Plik za duży (max ~900 KB przez panel). Użyj mniejszego MP3 albo URL.");
        return;
      }
      if (!deviceId.trim() || remoteToken.trim().length < 16) {
        setError("Device ID i kod parowania są wymagane");
        return;
      }
      setBusy(true);
      try {
        const base64 = await fileToBase64(trackFile);
        const uploadId = uid();
        await set(ref(db, `uploads/${deviceId.trim()}/${uploadId}`), {
          name,
          base64,
          at: Date.now(),
        });
        await sendCommand({ type: "import_track_upload", uploadId, name });
        setTrackFile(null);
        setTrackName("");
      } catch (err) {
        setError(err.message || "Upload nieudany");
      } finally {
        setBusy(false);
      }
      return;
    }
    if (!trackUrl.trim()) {
      setError("Wybierz plik MP3 albo podaj URL");
      return;
    }
    await sendCommand({ type: "import_track", name, url: trackUrl.trim() });
    setTrackUrl("");
    setTrackName("");
  }

  if (!isFirebaseConfigured) {
    return (
      <div className="app">
        <h1 className="brand">Radiowęzeł</h1>
        <p className="lead">Brak konfiguracji Firebase (.env).</p>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="app">
        <h1 className="brand">Radiowęzeł</h1>
        <p className="lead">Panel zdalny: dzwonki, utwory, święta i alarmy.</p>
        <form className="panel" onSubmit={login} autoComplete="off">
          <div className="label">Logowanie</div>
          <input
            className="field"
            type="email"
            name="panel-email"
            placeholder="E-mail"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="off"
            required
          />
          <input
            className="field"
            type="password"
            name="panel-password"
            placeholder="Hasło"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            required
          />
          <button className="btn" disabled={busy} type="submit">
            Zaloguj
          </button>
          {error ? <div className="err">{error}</div> : null}
        </form>
      </div>
    );
  }

  return (
    <div className="app">
      <h1 className="brand">Radiowęzeł</h1>
      <p className="lead">
        Pełne zdalne sterowanie tabletem: harmonogram, utwory, dni wolne i alarmy.
      </p>

      <div className="panel">
        <div className="label">Tablet</div>
        <div className="row" style={{ marginBottom: 12 }}>
          <span className={`status-dot ${online ? "on" : "off"}`} />
          <strong>{statusLabel}</strong>
          <span className="mono" style={{ color: "var(--muted)" }}>
            v{presence?.appVersion || "?"} · następny: {nextBell}
          </span>
        </div>
        <input
          className="field"
          placeholder="Device ID (z Ustawień tabletu)"
          value={deviceId}
          onChange={(e) => setDeviceId(e.target.value.trim())}
          autoComplete="off"
        />
        <input
          className="field"
          type="password"
          placeholder="Kod parowania (z Ustawień tabletu)"
          value={remoteToken}
          onChange={(e) => setRemoteToken(e.target.value.trim())}
          autoComplete="off"
        />
        <input
          className="field"
          type="password"
          inputMode="numeric"
          placeholder="PIN alarmowy tabletu (sesja — nie zapisywany)"
          value={alarmPin}
          onChange={(e) => setAlarmPin(e.target.value.replace(/\D/g, "").slice(0, 6))}
          autoComplete="off"
        />
        <input
          className="field"
          type="password"
          inputMode="numeric"
          placeholder="Potwierdzenie PIN (przy każdej zmianie)"
          value={pin}
          onChange={(e) => setPin(e.target.value.replace(/\D/g, "").slice(0, 6))}
          autoComplete="off"
        />
        <div className="row">
          <button className="btn ghost" type="button" onClick={logout}>
            Wyloguj
          </button>
          <button
            className="btn ghost"
            type="button"
            disabled={busy}
            onClick={() => sendCommand({ type: "refresh_state" }, { requirePin: false })}
          >
            Odśwież stan
          </button>
        </div>
        <div className="muted" style={{ marginTop: 8 }}>
          Sesja tylko w tej karcie przeglądarki. Bezczynność 12 min = wylogowanie. PIN i kod
          parowania nie są zapamiętywane na dysku.
        </div>
      </div>

      <div className="tabs">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            className={`tab ${tab === t.id ? "active" : ""}`}
            onClick={() => setTab(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === "status" ? (
        <div className="panel">
          <div className="label">Status</div>
          <p>
            Utwory: <strong>{tracks.length}</strong> · Dzwonki:{" "}
            <strong>{events.length}</strong> · Święta: <strong>{holidays.length}</strong>
          </p>
          <div className="row">
            <button
              className="btn"
              type="button"
              disabled={busy}
              onClick={() => sendCommand({ type: "ring_now", durationSeconds: 15 })}
            >
              Dzwoń teraz (test)
            </button>
            <button
              className="btn ghost"
              type="button"
              disabled={busy}
              onClick={() => sendCommand({ type: "apply_school_schedule" })}
            >
              Wczytaj plan lekcji szkoły
            </button>
          </div>
        </div>
      ) : null}

      {tab === "schedule" ? (
        <div className="panel">
          <div className="label">Plan dzwonków</div>
          <form onSubmit={saveEvent}>
            <div className="row">
              <input
                className="field"
                style={{ flex: "1 1 120px", marginBottom: 0 }}
                value={eventTime}
                onChange={(e) => setEventTime(e.target.value)}
                placeholder="HH:MM"
                required
              />
              <select
                className="field"
                style={{ flex: "1 1 160px", marginBottom: 0 }}
                value={eventType}
                onChange={(e) => setEventType(e.target.value)}
              >
                <option value="BREAK_START">Start przerwy</option>
                <option value="BREAK_END">Koniec przerwy</option>
              </select>
              <select
                className="field"
                style={{ flex: "1 1 160px", marginBottom: 0 }}
                value={eventTrackId}
                onChange={(e) => setEventTrackId(e.target.value)}
              >
                <option value="">Utwór (opcjonalnie)</option>
                {tracks.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
              <input
                className="field"
                style={{ flex: "0 0 90px", marginBottom: 0 }}
                type="number"
                min={5}
                max={180}
                value={eventDuration}
                onChange={(e) => setEventDuration(e.target.value)}
              />
              <button className="btn" type="submit" disabled={busy}>
                {editingEventId ? "Zapisz" : "Dodaj"}
              </button>
            </div>
          </form>
          <div className="list">
            {events.length === 0 ? (
              <div className="muted">Brak dzwonków — tablet offline albo pusty plan.</div>
            ) : (
              events.map((ev) => {
                const track = tracks.find((t) => t.id === ev.trackId);
                return (
                  <div className="list-row" key={ev.id}>
                    <div>
                      <strong>{minutesToLabel(ev.timeMinutes)}</strong>{" "}
                      {ev.type === "BREAK_END" ? "koniec przerwy" : "start przerwy"}
                      <div className="muted">
                        {track?.name || "bez utworu"} · {ev.durationSeconds || 60}s
                      </div>
                    </div>
                    <div className="row">
                      <button className="btn ghost" type="button" onClick={() => editEvent(ev)}>
                        Edytuj
                      </button>
                      <button
                        className="btn ghost"
                        type="button"
                        disabled={busy}
                        onClick={() => sendCommand({ type: "delete_event", eventId: ev.id })}
                      >
                        Usuń
                      </button>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      ) : null}

      {tab === "tracks" ? (
        <div className="panel">
          <div className="label">Utwory MP3</div>
          <form onSubmit={importTrack}>
            <input
              className="field"
              placeholder="Nazwa (np. wiosna.mp3)"
              value={trackName}
              onChange={(e) => setTrackName(e.target.value)}
            />
            <input
              className="field"
              type="file"
              accept="audio/mpeg,audio/mp3,.mp3"
              onChange={(e) => setTrackFile(e.target.files?.[0] || null)}
            />
            <div className="muted" style={{ marginBottom: 8 }}>
              Albo wklej bezpośredni URL do MP3 (HTTPS):
            </div>
            <input
              className="field"
              placeholder="https://…/utwor.mp3"
              value={trackUrl}
              onChange={(e) => setTrackUrl(e.target.value)}
            />
            <button className="btn" type="submit" disabled={busy}>
              Dodaj utwór na tablet
            </button>
          </form>
          <div className="list">
            {tracks.map((t) => (
              <div className="list-row" key={t.id}>
                <strong>{t.name}</strong>
                <div className="row">
                  <button
                    className="btn ghost"
                    type="button"
                    disabled={busy}
                    onClick={() =>
                      sendCommand({ type: "ring_now", trackId: t.id, durationSeconds: 15 })
                    }
                  >
                    Test
                  </button>
                  <button
                    className="btn ghost"
                    type="button"
                    disabled={busy}
                    onClick={() => sendCommand({ type: "delete_track", trackId: t.id })}
                  >
                    Usuń
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      ) : null}

      {tab === "holidays" ? (
        <div className="panel">
          <div className="label">Dni wolne</div>
          <form onSubmit={saveHoliday}>
            <div className="row">
              <input
                className="field"
                style={{ flex: "1 1 160px", marginBottom: 0 }}
                type="date"
                value={holidayDate}
                onChange={(e) => setHolidayDate(e.target.value)}
                required
              />
              <input
                className="field"
                style={{ flex: "2 1 200px", marginBottom: 0 }}
                placeholder="Nazwa"
                value={holidayName}
                onChange={(e) => setHolidayName(e.target.value)}
              />
              <button className="btn" type="submit" disabled={busy}>
                Dodaj
              </button>
            </div>
          </form>
          <div className="list">
            {holidays.map((h) => (
              <div className="list-row" key={h.id}>
                <div>
                  <strong>{h.date}</strong> {h.name}
                  {h.builtIn ? <span className="muted"> · wbudowane</span> : null}
                </div>
                <button
                  className="btn ghost"
                  type="button"
                  disabled={busy}
                  onClick={() => sendCommand({ type: "delete_holiday", holidayId: h.id })}
                >
                  Usuń
                </button>
              </div>
            ))}
          </div>
        </div>
      ) : null}

      {tab === "alarms" ? (
        <div className="panel">
          <div className="label">Alarmy</div>
          <div className="toggle">
            <input
              id="drill"
              type="checkbox"
              checked={drill}
              onChange={(e) => setDrill(e.target.checked)}
            />
            <label htmlFor="drill">
              {drill ? "Tryb: ĆWICZENIE" : "Tryb: PRAWDZIWY ALARM"}
            </label>
          </div>
          <div className="muted" style={{ marginBottom: 10 }}>
            Użyj pól PIN z sekcji Tablet powyżej.
          </div>
          <div className="row" style={{ marginBottom: 12 }}>
            <button
              className="btn danger"
              type="button"
              disabled={busy}
              onClick={() => sendCommand({ type: "stop" })}
            >
              STOP
            </button>
          </div>
          <div className="row">
            {ALARMS.map((a) => (
              <button
                key={a.id}
                className="btn alarm"
                type="button"
                disabled={busy}
                onClick={() => sendCommand({ type: "start", alarmType: a.id, drill })}
              >
                {a.label}
              </button>
            ))}
          </div>
        </div>
      ) : null}

      {error ? <div className="err">{error}</div> : null}
      {info ? <div className="ok">{info}</div> : null}
      {ack ? (
        <div className="mono" style={{ marginTop: 12, color: "var(--muted)" }}>
          Ostatnie ACK: {ack.status}
          {ack.message ? ` — ${ack.message}` : ""} · {ack.commandId}
        </div>
      ) : null}
    </div>
  );
}
