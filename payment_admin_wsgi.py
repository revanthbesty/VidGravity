"""Minimal admin WSGI routes; requires a strong bearer token and HTTPS at the edge."""
from __future__ import annotations
import json

def make_admin_app(service):
    def app(environ, start_response):
        if environ.get("REQUEST_METHOD", "GET").upper() != "GET" and environ.get("PATH_INFO") != "/api/admin/payments/reconcile":
            return _respond(start_response, "405 Method Not Allowed", {"error":"method_not_allowed"})
        auth = environ.get("HTTP_AUTHORIZATION", "")
        token = auth[7:] if auth.startswith("Bearer ") else ""
        path = environ.get("PATH_INFO", "")
        try:
            if path == "/api/admin/payments/health" and environ.get("REQUEST_METHOD","GET").upper() == "GET":
                # Authenticated diagnostic endpoint; do not expose provider credentials.
                from payment_admin import require_admin
                require_admin(token)
                return _respond(start_response, "200 OK", {"status":"ok","component":"payment_admin"})
            if path == "/api/admin/payments/dashboard" and environ.get("REQUEST_METHOD","GET").upper() == "GET":
                return _respond(start_response, "200 OK", service.dashboard(token))
            if path == "/api/admin/payments/reconcile" and environ.get("REQUEST_METHOD","GET").upper() == "POST":
                result = service.retry_reconciliation(token)
                return _respond(start_response, "200 OK", result)
            if path.startswith("/api/admin/payments/") and path.endswith("/status") and environ.get("REQUEST_METHOD","GET").upper() == "GET":
                payment_id = path[len("/api/admin/payments/"):-len("/status")].strip("/")
                if not payment_id or "/" in payment_id:
                    return _respond(start_response, "400 Bad Request", {"error":"invalid_payment_id"})
                result = service.payment_status(token, payment_id)
                return _respond(start_response, "200 OK" if result else "404 Not Found", result or {"error":"not_found"})
            return _respond(start_response, "404 Not Found", {"error":"not_found"})
        except PermissionError:
            return _respond(start_response, "401 Unauthorized", {"error":"unauthorized"})
        except Exception:
            return _respond(start_response, "503 Service Unavailable", {"error":"admin_service_unavailable"})
    return app

def _respond(start_response, status, payload):
    body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    start_response(status, [("Content-Type","application/json; charset=utf-8"),
                            ("Content-Length",str(len(body))),
                            ("Cache-Control","no-store")])
    return [body]
