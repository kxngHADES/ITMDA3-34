#!/usr/bin/env python3
"""Contract mock of the SwiftQ API for emulator testing of the patient join flow.

NOT the real API. It exists because the dev API (swiftq-api-dev.onrender.com) has no GET /v1/clinics,
no patient token route and no /v1/sync/*. It copies the behaviour of api/routes/v1/tickets.py and
api/services/queue_service.py:join_queue, so the app sees the same status codes, headers and error envelope.

Run:   python3 docs/tools/mock_api.py [port]        (default 8787; emulator reaches it at http://10.0.2.2:8787)
Admin: POST /__admin/queue {"queue_id": "q_general", "status": "paused|open|closed"}
       POST /__admin/hold  {"seconds": 20}      hold the next join response (to kill the app mid-request)
       GET  /__admin/state                      tickets + request log
       POST /__admin/reset
"""
import hashlib, json, sys, threading, time, uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOCK = threading.Lock()
NOW = lambda: time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())

CLINICS = [
    {"id": "clin_olive", "name": "Olievenhoutbosch Clinic", "address": "Olievenhoutbosch, Centurion", "is_open": True, "is_active": True},
    {"id": "clin_laudium", "name": "Laudium Community Health Centre", "address": "Laudium, Pretoria", "is_open": True, "is_active": True},
    {"id": "clin_erasmia", "name": "Erasmia Clinic", "address": "Erasmia, Pretoria", "is_open": False, "is_active": True},
]
SERVICE_POINTS = {
    "clin_olive": [
        {"id": "sp_general", "clinic_id": "clin_olive", "name": "General consultation", "icon": "stethoscope", "is_active": True, "queue_id": "q_general", "concurrent_servers": 1},
        {"id": "sp_chronic", "clinic_id": "clin_olive", "name": "Chronic medication", "icon": "pill", "is_active": True, "queue_id": None, "concurrent_servers": 1},
    ],
    "clin_laudium": [
        {"id": "sp_lau_general", "clinic_id": "clin_laudium", "name": "General consultation", "icon": "stethoscope", "is_active": True, "queue_id": "q_lau", "concurrent_servers": 1},
    ],
    "clin_erasmia": [],
}
QUEUES = {"q_general": {"clinic": "clin_olive", "sp": "sp_general", "status": "open", "seq": 0},
          "q_lau": {"clinic": "clin_laudium", "sp": "sp_lau_general", "status": "open", "seq": 0}}
TICKETS, IDEM, LOG = {}, {}, []
HOLD = {"seconds": 0}

def err(code, status, retryable, message, retry_after=None):
    body = {"error": {"code": code, "message": message, "retryable": retryable, "request_id": "req_" + uuid.uuid4().hex[:10]}}
    if retry_after: body["error"]["retry_after_seconds"] = retry_after
    return status, body, ({"Retry-After": str(retry_after)} if retry_after else {})

def ticket_json(t, q):
    ahead = sum(1 for x in TICKETS.values() if x["queue_id"] == t["queue_id"] and x["status"] == "waiting" and x["sequence_no"] < t["sequence_no"])
    return {**t, "position": ahead + 1, "people_ahead": ahead,
            "estimated_wait": {"min_minutes": 8 * (ahead + 1), "max_minutes": 12 * (ahead + 1), "cold_start": True}}

class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def log_message(self, *a): pass

    def _send(self, status, body, headers=None):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(data)))
        for k, v in (headers or {}).items(): self.send_header(k, v)
        self.end_headers(); self.wfile.write(data)

    def _uid(self):
        a = self.headers.get("Authorization", "")
        return a.removeprefix("Bearer ").removeprefix("mock-id-token-") if a.startswith("Bearer mock-id-token-") else None

    def do_GET(self): self._route("GET")
    def do_POST(self): self._route("POST")

    def _route(self, method):
        n = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(n) if n else b""
        path = self.path.split("?")[0]
        with LOCK:
            LOG.append({"t": NOW(), "m": method, "p": path, "idem": self.headers.get("Idempotency-Key"), "body": raw.decode() or None})
        # Firebase Identity Toolkit stand-in (anonymous sign-up and token refresh)
        if path.endswith("/accounts:signUp"):
            uid = "anon_" + uuid.uuid4().hex[:8]
            return self._send(200, {"idToken": "mock-id-token-" + uid, "refreshToken": "mock-refresh-" + uid, "expiresIn": "3600", "localId": uid})
        if path.endswith("/v1/token") and not path.startswith("/v1/"):
            return self._send(200, {"id_token": "mock-id-token-anon_refreshed", "refresh_token": "mock-refresh", "expires_in": "3600"})
        if path.startswith("/__admin/"): return self._admin(path, raw)
        if path == "/health": return self._send(200, {"status": "ok"})
        uid = self._uid()
        if uid is None:
            return self._send(*err("UNAUTHENTICATED", 401, True, "Please sign in again.")[:2])
        if method == "GET" and path == "/v1/clinics":
            return self._send(200, {"items": CLINICS, "has_more": False})
        if method == "GET" and path.startswith("/v1/clinics/") and path.endswith("/service-points"):
            cid = path.split("/")[3]
            if cid not in SERVICE_POINTS: return self._send(*err("NOT_FOUND", 404, False, "We couldn't find that. It may have been closed or moved.")[:2])
            return self._send(200, {"items": [s for s in SERVICE_POINTS[cid] if s["is_active"]]})
        if method == "POST" and path.startswith("/v1/queues/") and path.endswith("/tickets"):
            return self._join(path.split("/")[3], uid, raw)
        if method == "GET" and path.startswith("/v1/queues/") and path.count("/") == 3:
            # Harness only: pretends other patients are in the queue so the Live queue screen has something to show.
            q = QUEUES.get(path.split("/")[3])
            if not q: return self._send(*err("NOT_FOUND", 404, False, "We couldn't find that. It may have been closed or moved.")[:2])
            mine = sorted((t for t in TICKETS.values() if t["queue_id"] == path.split("/")[3] and t["status"] == "waiting"), key=lambda t: t["sequence_no"])
            fake_before = [{"ticket_id": f"x{n}", "display_number": f"A{n:03d}", "status": "waiting"} for n in (39, 40, 41)]
            fake_after = [{"ticket_id": f"y{n}", "display_number": f"A{n:03d}", "status": "waiting"} for n in range(50, 57)]
            rows = fake_before + [{"ticket_id": t["id"], "display_number": t["display_number"], "status": "waiting"} for t in mine] + fake_after
            entries = [{**r, "position": i + 1, "is_priority": False, "late_flag": False} for i, r in enumerate(rows)]
            return self._send(200, {"id": path.split("/")[3], "service_point_id": q["sp"], "clinic_id": q["clinic"], "service_date": "2026-10-13",
                                    "display_prefix": "A", "status": q["status"], "now_serving": "A038", "waiting_count": len(entries),
                                    "entries": entries, "updated_at": "2026-10-13T12:00:00Z"})
        if method == "POST" and path.startswith("/v1/tickets/") and path.endswith("/cancel"):
            t = TICKETS.get(path.split("/")[3])
            if not t: return self._send(*err("NOT_FOUND", 404, False, "We couldn't find that. It may have been closed or moved.")[:2])
            t["status"] = "cancelled"
            return self._send(200, ticket_json(t, QUEUES[t["queue_id"]]))
        if method == "GET" and path.startswith("/v1/tickets/"):
            t = TICKETS.get(path.split("/")[3])
            if not t: return self._send(*err("NOT_FOUND", 404, False, "We couldn't find that. It may have been closed or moved.")[:2])
            return self._send(200, ticket_json(t, QUEUES[t["queue_id"]]))
        return self._send(404, {"detail": "Not Found"})

    def _join(self, qid, uid, raw):
        key = self.headers.get("Idempotency-Key")
        try: uuid.UUID(key or "", version=4)
        except ValueError: return self._send(*err("VALIDATION_FAILED", 422, False, "Some details are missing or not in the right format. Please check and try again.")[:2])
        try: body = json.loads(raw)
        except ValueError: return self._send(422, err("VALIDATION_FAILED", 422, False, "Some details are missing or not in the right format. Please check and try again.")[1])
        allowed = {"display_name", "phone", "language", "notification_channel", "priority_request", "client_created_at"}
        if set(body) - allowed or "language" not in body:   # extra = "forbid"
            return self._send(*err("VALIDATION_FAILED", 422, False, "Some details are missing or not in the right format. Please check and try again.")[:2])
        if body.get("notification_channel") in ("sms", "whatsapp") and not body.get("phone"):
            return self._send(*err("VALIDATION_FAILED", 422, False, "Add a phone number to get SMS or WhatsApp alerts.")[:2])
        # the real server hashes ("join", queue_id, parsed model dump), not the raw bytes
        req_hash = hashlib.sha256(json.dumps(["join", qid, body], sort_keys=True).encode()).hexdigest()
        with LOCK:
            rec = IDEM.get((uid, key))
            if rec:
                if rec[0] != req_hash: return self._send(*err("IDEMPOTENCY_KEY_REUSED", 400, False, "Something went wrong with this request. Please try again.")[:2])
                return self._send(200, rec[1])
            q = QUEUES.get(qid)
            if not q: return self._send(*err("NOT_FOUND", 404, False, "We couldn't find that. It may have been closed or moved.")[:2])
            clinic = next(c for c in CLINICS if c["id"] == q["clinic"])
            if not clinic["is_open"]: return self._send(*err("CLINIC_CLOSED", 400, False, "This clinic is closed right now. Please check the opening hours.")[:2])
            existing = next((t for t in TICKETS.values() if t["queue_id"] == qid and t["owner"] == uid and t["status"] in ("waiting", "called")), None)
            if existing:
                resp = ticket_json(existing, q); IDEM[(uid, key)] = (req_hash, resp)
                return self._send(200, resp)
            if q["status"] == "paused": return self._send(*err("QUEUE_PAUSED", 400, True, "This queue is paused for a short while. Please try again in a few minutes.")[:2])
            if q["status"] == "closed": return self._send(*err("QUEUE_CLOSED", 400, False, "This queue is closed for today. Please choose another service.")[:2])
            q["seq"] += 1
            tid = "tkt_" + uuid.uuid4().hex[:6]
            t = {"id": tid, "owner": uid, "display_number": f"A{q['seq']:03d}", "sequence_no": q["seq"], "queue_id": qid,
                 "service_point_id": q["sp"], "clinic_id": q["clinic"], "status": "waiting", "channel": "app",
                 "priority": {"requested_category": body.get("priority_request") or "none", "staff_confirmed": False},
                 "late_flag": False, "display_name": body.get("display_name"),
                 "phone_last4": (body.get("phone") or "")[-4:] or None, "notification_channel": body.get("notification_channel"),
                 "language": body["language"], "created_at": NOW(), "updated_at": NOW()}
            TICKETS[tid] = t
            resp = ticket_json(t, q); IDEM[(uid, key)] = (req_hash, resp)
            hold = HOLD["seconds"]; HOLD["seconds"] = 0
        if hold: time.sleep(hold)      # the ticket exists on the server, but the response is slow to arrive
        resp.pop("owner", None)
        self._send(201, resp, {"Location": f"/v1/tickets/{tid}"})

    def _admin(self, path, raw):
        body = json.loads(raw or b"{}")
        with LOCK:
            if path == "/__admin/queue": QUEUES[body["queue_id"]]["status"] = body["status"]
            elif path == "/__admin/hold": HOLD["seconds"] = body["seconds"]
            elif path == "/__admin/clinic": next(c for c in CLINICS if c["id"] == body["id"])["is_open"] = body["is_open"]
            elif path == "/__admin/reset":
                TICKETS.clear(); IDEM.clear(); LOG.clear()
                for q in QUEUES.values(): q["seq"] = 0; q["status"] = "open"
            return self._send(200, {"tickets": [{k: v for k, v in t.items() if k != "owner"} for t in TICKETS.values()], "log": LOG[-60:], "queues": QUEUES})

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8787
    ThreadingHTTPServer(("0.0.0.0", port), H).serve_forever()
