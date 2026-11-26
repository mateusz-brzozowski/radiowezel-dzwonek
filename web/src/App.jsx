import { useEffect, useMemo, useState } from "react";
import {
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut,
} from "firebase/auth";
import { onValue, push, ref, set } from "firebase/database";
import { auth, db, isFirebaseConfigured } from "./firebase.js";

const ALARMS = [
  { id: "test", label: "Test systemu" },
  { id: "evac_fire", label: "Pożar / ewakuacja" },
  { id: "bomb", label: "Alarm bombowy" },
  { id: "lockdown", label: "Blokada szkoły" },
  { id: "shelter", label: "Schronienie w budynku" },
  { id: "air_raid", label: "Alarm powietrzny" },
  { id: "all_clear", label: "Odwołanie alarmu" },
];

function uid() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export default function App() {
  const [user, setUser] = useState(null);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [deviceId, setDeviceId] = useState(
    import.meta.env.VITE_DEFAULT_DEVICE_ID || localStorage.getItem("deviceId") || ""
  );
  const [pin, setPin] = useState("");
  const [alarmPin, setAlarmPin] = useState(localStorage.getItem("alarmPin") || "1234");
  const [drill, setDrill] = useState(true);
  const [presence, setPresence] = useState(null);
  const [ack, setAck] = useState(null);
  const [error, setError] = useState("");
  const [info, setInfo] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!auth) return undefined;
    return onAuthStateChanged(auth, setUser);
  }, []);

  useEffect(() => {
    localStorage.setItem("deviceId", deviceId.trim());
  }, [deviceId]);

  useEffect(() => {
    localStorage.setItem("alarmPin", alarmPin);
  }, [alarmPin]);

  useEffect(() => {
    if (!db || !user || !deviceId.trim()) {
      setPresence(null);
      setAck(null);
      return undefined;
    }
    const base = `devices/${deviceId.trim()}`;
    const unsubPresence = onValue(ref(db, `${base}/presence`), (snap) => {
      setPresence(snap.val());
    });
    const unsubAck = onValue(ref(db, `${base}/ack`), (snap) => {
      setAck(snap.val());
    });
    return () => {
      unsubPresence();
      unsubAck();
    };
  }, [user, deviceId]);

  const online = Boolean(presence?.online);
  const nextBell = presence?.nextBell || "—";

  const statusLabel = useMemo(() => {
    if (!deviceId.trim()) return "Podaj Device ID tabletu";
    if (!presence) return "Brak danych presence";
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
      await signInWithEmailAndPassword(auth, email.trim(), password);
    } catch (err) {
      setError(err.message || "Logowanie nieudane");
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    if (auth) await signOut(auth);
  }

  async function sendCommand(type, alarmType) {
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
    if (pin !== alarmPin) {
      setError("Błędny PIN alarmowy");
      return;
    }
    const commandId = uid();
    const payload = {
      id: commandId,
      type,
      alarmType: alarmType || null,
      drill,
      issuedAt: Date.now(),
      issuedBy: user.email || "unknown",
    };
    setBusy(true);
    try {
      await set(ref(db, `devices/${deviceId.trim()}/command`), payload);
      await push(ref(db, "audit"), {
        ...payload,
        deviceId: deviceId.trim(),
        at: Date.now(),
        source: "web",
      });
      setInfo(
        type === "stop"
          ? `Wysłano STOP (${commandId})`
          : `Wysłano ${alarmType} (${commandId})`
      );
      setPin("");
    } catch (err) {
      setError(err.message || "Nie udało się wysłać komendy");
    } finally {
      setBusy(false);
    }
  }

  if (!isFirebaseConfigured) {
    return (
      <div className="app">
        <h1 className="brand">Radiowęzeł</h1>
        <p className="lead">
          Skopiuj <code>web/.env.example</code> → <code>web/.env</code> i uzupełnij
          klucze Firebase (ten sam projekt co tablet w{" "}
          <code>firebase_config.json</code>).
        </p>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="app">
        <h1 className="brand">Radiowęzeł</h1>
        <p className="lead">Panel zdalnych alarmów szkolnych — tylko dla uprawnionych.</p>
        <form className="panel" onSubmit={login}>
          <div className="label">Logowanie</div>
          <input
            className="field"
            type="email"
            placeholder="E-mail"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
          <input
            className="field"
            type="password"
            placeholder="Hasło"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
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
        Zdalne sterowanie alarmami. Komendy trafiają do tabletu przez Firebase —
        tablet sam łączy się wychodząco.
      </p>

      <div className="panel">
        <div className="label">Tablet</div>
        <div className="row" style={{ marginBottom: 12 }}>
          <span className={`status-dot ${online ? "on" : "off"}`} />
          <strong>{statusLabel}</strong>
          <span className="mono" style={{ color: "var(--muted)" }}>
            v{presence?.appVersion || "?"} · następny dzwonek: {nextBell}
          </span>
        </div>
        <input
          className="field"
          placeholder="Device ID (z Ustawień tabletu)"
          value={deviceId}
          onChange={(e) => setDeviceId(e.target.value)}
        />
        <input
          className="field"
          type="password"
          placeholder="Zapamiętany PIN alarmowy (oczekiwany)"
          value={alarmPin}
          onChange={(e) => setAlarmPin(e.target.value.replace(/\D/g, "").slice(0, 6))}
        />
        <div className="row">
          <button className="btn ghost" type="button" onClick={logout}>
            Wyloguj ({user.email})
          </button>
        </div>
      </div>

      <div className="panel">
        <div className="label">Alarm</div>
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
        <input
          className="field"
          type="password"
          inputMode="numeric"
          placeholder="PIN alarmowy (potwierdzenie)"
          value={pin}
          onChange={(e) => setPin(e.target.value.replace(/\D/g, "").slice(0, 6))}
        />
        <div className="row" style={{ marginBottom: 12 }}>
          <button
            className="btn danger"
            type="button"
            disabled={busy}
            onClick={() => sendCommand("stop")}
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
              onClick={() => sendCommand("start", a.id)}
            >
              {a.label}
            </button>
          ))}
        </div>
        {error ? <div className="err">{error}</div> : null}
        {info ? <div className="ok">{info}</div> : null}
        {ack ? (
          <div className="mono" style={{ marginTop: 12, color: "var(--muted)" }}>
            Ostatnie ACK: {ack.status} · {ack.commandId}
          </div>
        ) : null}
      </div>
    </div>
  );
}
