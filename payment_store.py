"""Minimal SQLite payment-event idempotency store for the P140 integration."""
from __future__ import annotations
import sqlite3
from pathlib import Path

SCHEMA = """
CREATE TABLE IF NOT EXISTS payment_events (
    event_id TEXT PRIMARY KEY,
    event_type TEXT NOT NULL,
    provider_payment_id TEXT,
    provider_order_id TEXT,
    status TEXT NOT NULL,
    received_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS verified_payments (
    provider_payment_id TEXT PRIMARY KEY,
    provider_order_id TEXT NOT NULL,
    amount_paise INTEGER NOT NULL,
    currency TEXT NOT NULL,
    status TEXT NOT NULL,
    verified_by TEXT NOT NULL,
    license_issued INTEGER NOT NULL DEFAULT 0,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
"""

class PaymentStore:
    def __init__(self, path: str | Path):
        self.path = str(path)
        with self.connect() as db:
            db.executescript(SCHEMA)

    def connect(self):
        db = sqlite3.connect(self.path, timeout=10)
        db.row_factory = sqlite3.Row
        return db

    def record_event_once(self, event_id: str, event_type: str, payment_id: str | None,
                          order_id: str | None, status: str = "verified") -> bool:
        if not event_id:
            raise ValueError("Provider event ID is required for idempotency")
        with self.connect() as db:
            cur = db.execute(
                "INSERT OR IGNORE INTO payment_events(event_id,event_type,provider_payment_id,provider_order_id,status) VALUES(?,?,?,?,?)",
                (event_id, event_type, payment_id, order_id, status))
            return cur.rowcount == 1

    def record_captured_payment(self, payment_id: str, order_id: str, amount_paise: int,
                                currency: str = "INR", verified_by: str = "webhook") -> None:
        if not payment_id or not order_id or amount_paise <= 0:
            raise ValueError("Valid payment ID, order ID, and positive amount are required")
        with self.connect() as db:
            db.execute("""INSERT INTO verified_payments(provider_payment_id,provider_order_id,amount_paise,currency,status,verified_by)
                          VALUES(?,?,?,?, 'captured', ?)
                          ON CONFLICT(provider_payment_id) DO UPDATE SET
                          provider_order_id=excluded.provider_order_id, amount_paise=excluded.amount_paise,
                          currency=excluded.currency, status='captured', verified_by=excluded.verified_by,
                          updated_at=CURRENT_TIMESTAMP""",
                       (payment_id, order_id, amount_paise, currency, verified_by))

    def mark_license_issued_once(self, payment_id: str) -> bool:
        with self.connect() as db:
            cur = db.execute("""UPDATE verified_payments SET license_issued=1, updated_at=CURRENT_TIMESTAMP
                                WHERE provider_payment_id=? AND status='captured' AND license_issued=0""", (payment_id,))
            return cur.rowcount == 1


# P142 order mapping schema and methods are added below for backward-compatible upgrades.
ORDER_SCHEMA = """
CREATE TABLE IF NOT EXISTS payment_orders (
    provider_order_id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    amount_paise INTEGER NOT NULL,
    currency TEXT NOT NULL,
    receipt TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'created',
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
"""
_original_init = PaymentStore.__init__
def _p142_init(self, path):
    _original_init(self, path)
    with self.connect() as db:
        db.executescript(ORDER_SCHEMA)
PaymentStore.__init__ = _p142_init

def _p142_save_order(self, order_id, account_id, amount_paise, currency, receipt):
    if not all([order_id, account_id, currency, receipt]) or amount_paise <= 0:
        raise ValueError("Valid order mapping values are required")
    with self.connect() as db:
        db.execute("""INSERT INTO payment_orders(provider_order_id,account_id,amount_paise,currency,receipt)
                      VALUES(?,?,?,?,?)""", (order_id, account_id, amount_paise, currency, receipt))

def _p142_get_order(self, order_id):
    with self.connect() as db:
        row = db.execute("SELECT * FROM payment_orders WHERE provider_order_id=?", (order_id,)).fetchone()
        return dict(row) if row else None

PaymentStore.save_order = _p142_save_order
PaymentStore.get_order = _p142_get_order


# P143 recovery state: durable order/payment/license workflow.
P143_RECOVERY_SCHEMA = """
CREATE TABLE IF NOT EXISTS payment_recovery (
    provider_payment_id TEXT PRIMARY KEY,
    provider_order_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    amount_paise INTEGER NOT NULL,
    currency TEXT NOT NULL,
    payment_status TEXT NOT NULL DEFAULT 'captured',
    issuance_status TEXT NOT NULL DEFAULT 'pending',
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error_code TEXT,
    next_retry_at TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS payment_reconciliation_runs (
    run_id INTEGER PRIMARY KEY AUTOINCREMENT,
    started_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TEXT,
    inspected INTEGER NOT NULL DEFAULT 0,
    repaired INTEGER NOT NULL DEFAULT 0,
    failed INTEGER NOT NULL DEFAULT 0,
    notes TEXT
);
"""
_p143_original_init = PaymentStore.__init__
def _p143_init(self, path):
    _p143_original_init(self, path)
    with self.connect() as db:
        db.executescript(P143_RECOVERY_SCHEMA)
PaymentStore.__init__ = _p143_init

def _p143_upsert_recovery(self, payment_id, order_id, account_id, amount_paise, currency="INR"):
    if not all([payment_id, order_id, account_id, currency]) or amount_paise <= 0:
        raise ValueError("Valid payment recovery details are required")
    with self.connect() as db:
        db.execute("""INSERT INTO payment_recovery(provider_payment_id,provider_order_id,account_id,amount_paise,currency)
                      VALUES(?,?,?,?,?)
                      ON CONFLICT(provider_payment_id) DO UPDATE SET
                      provider_order_id=excluded.provider_order_id, account_id=excluded.account_id,
                      amount_paise=excluded.amount_paise, currency=excluded.currency,
                      payment_status='captured', updated_at=CURRENT_TIMESTAMP""",
                   (payment_id, order_id, account_id, amount_paise, currency))

def _p143_get_recovery(self, payment_id):
    with self.connect() as db:
        row = db.execute("SELECT * FROM payment_recovery WHERE provider_payment_id=?", (payment_id,)).fetchone()
        return dict(row) if row else None

def _p143_pending_recoveries(self, limit=100):
    limit = max(1, min(int(limit), 500))
    with self.connect() as db:
        rows = db.execute("""SELECT * FROM payment_recovery
                            WHERE payment_status='captured' AND issuance_status IN ('pending','retry')
                            ORDER BY created_at LIMIT ?""", (limit,)).fetchall()
        return [dict(row) for row in rows]

def _p143_claim_recovery(self, payment_id):
    with self.connect() as db:
        cur = db.execute("""UPDATE payment_recovery SET issuance_status='issuing', attempts=attempts+1,
                            updated_at=CURRENT_TIMESTAMP
                            WHERE provider_payment_id=? AND payment_status='captured'
                            AND issuance_status IN ('pending','retry')""", (payment_id,))
        return cur.rowcount == 1

def _p143_finish_recovery(self, payment_id):
    with self.connect() as db:
        cur = db.execute("""UPDATE payment_recovery SET issuance_status='issued', last_error_code=NULL,
                            next_retry_at=NULL, updated_at=CURRENT_TIMESTAMP
                            WHERE provider_payment_id=? AND issuance_status='issuing'""", (payment_id,))
        return cur.rowcount == 1

def _p143_fail_recovery(self, payment_id, error_code="issuer_unavailable"):
    safe_code = str(error_code)[:80]
    with self.connect() as db:
        cur = db.execute("""UPDATE payment_recovery SET issuance_status='retry', last_error_code=?,
                            next_retry_at=datetime('now','+5 minutes'), updated_at=CURRENT_TIMESTAMP
                            WHERE provider_payment_id=? AND issuance_status='issuing'""", (safe_code, payment_id))
        return cur.rowcount == 1

def _p143_start_reconciliation(self):
    with self.connect() as db:
        cur = db.execute("INSERT INTO payment_reconciliation_runs DEFAULT VALUES")
        return cur.lastrowid

def _p143_finish_reconciliation(self, run_id, inspected, repaired, failed, notes=""):
    with self.connect() as db:
        db.execute("""UPDATE payment_reconciliation_runs SET finished_at=CURRENT_TIMESTAMP,
                      inspected=?, repaired=?, failed=?, notes=? WHERE run_id=?""",
                   (inspected, repaired, failed, str(notes)[:500], run_id))

PaymentStore.upsert_recovery = _p143_upsert_recovery
PaymentStore.get_recovery = _p143_get_recovery
PaymentStore.pending_recoveries = _p143_pending_recoveries
PaymentStore.claim_recovery = _p143_claim_recovery
PaymentStore.finish_recovery = _p143_finish_recovery
PaymentStore.fail_recovery = _p143_fail_recovery
PaymentStore.start_reconciliation = _p143_start_reconciliation
PaymentStore.finish_reconciliation = _p143_finish_reconciliation


P144_ADMIN_SCHEMA = """
CREATE TABLE IF NOT EXISTS payment_audit_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type TEXT NOT NULL,
    payment_id TEXT,
    order_id TEXT,
    status TEXT NOT NULL,
    detail_code TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_recovery_status_updated ON payment_recovery(issuance_status, updated_at);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON payment_audit_events(created_at);
"""
_p144_prev_init = PaymentStore.__init__
def _p144_init(self, path):
    _p144_prev_init(self, path)
    with self.connect() as db:
        db.executescript(P144_ADMIN_SCHEMA)
PaymentStore.__init__ = _p144_init

def _p144_audit(self, event_type, payment_id=None, order_id=None, status="info", detail_code=None):
    safe_type = str(event_type)[:80]
    safe_status = str(status)[:40]
    safe_code = str(detail_code)[:80] if detail_code else None
    with self.connect() as db:
        db.execute("INSERT INTO payment_audit_events(event_type,payment_id,order_id,status,detail_code) VALUES(?,?,?,?,?)",
                   (safe_type, payment_id, order_id, safe_status, safe_code))

def _p144_dashboard(self):
    with self.connect() as db:
        counts = {}
        for row in db.execute("SELECT issuance_status, COUNT(*) AS n FROM payment_recovery GROUP BY issuance_status"):
            counts[row["issuance_status"]] = row["n"]
        recent = db.execute("""SELECT event_type,status,detail_code,created_at
                              FROM payment_audit_events ORDER BY id DESC LIMIT 20""").fetchall()
        runs = db.execute("""SELECT run_id,started_at,finished_at,inspected,repaired,failed
                             FROM payment_reconciliation_runs ORDER BY run_id DESC LIMIT 10""").fetchall()
        return {"license_issuance_counts": counts,
                "recent_events": [dict(r) for r in recent],
                "reconciliation_runs": [dict(r) for r in runs]}

def _p144_stuck(self, min_age_minutes=10, limit=100):
    min_age_minutes = max(1, min(int(min_age_minutes), 10080))
    limit = max(1, min(int(limit), 500))
    with self.connect() as db:
        rows = db.execute("""SELECT provider_payment_id,provider_order_id,issuance_status,attempts,
                            last_error_code,created_at,updated_at
                            FROM payment_recovery
                            WHERE issuance_status IN ('pending','retry','issuing')
                            AND (julianday('now') - julianday(updated_at))*1440 >= ?
                            ORDER BY updated_at LIMIT ?""", (min_age_minutes, limit)).fetchall()
        return [dict(r) for r in rows]

def _p144_record_run(self, run_id):
    with self.connect() as db:
        row = db.execute("SELECT * FROM payment_reconciliation_runs WHERE run_id=?", (run_id,)).fetchone()
        return dict(row) if row else None

PaymentStore.audit_event = _p144_audit
PaymentStore.admin_dashboard = _p144_dashboard
PaymentStore.stuck_payments = _p144_stuck
PaymentStore.reconciliation_run = _p144_record_run
