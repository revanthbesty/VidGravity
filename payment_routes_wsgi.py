"""Minimal WSGI route adapter example for P142.

Production must put this behind HTTPS, authentication, request-size limits, CSRF/origin checks,
structured audit logging, and a reverse proxy. Webhook endpoint is provider-authenticated by HMAC.
"""
from __future__ import annotations
import json
from payment_license_service import PaymentLicenseError

def make_payment_app(service, authenticate):
    def app(environ, start_response):
        path = environ.get("PATH_INFO", "")
        method = environ.get("REQUEST_METHOD", "GET").upper()
        try:
            if path == "/api/payment/create-order" and method == "POST":
                account_id = authenticate(environ)
                if not account_id:
                    return _respond(start_response, "401 Unauthorized", {"error":"authentication_required"})
                body = _read_json(environ)
                if body not in ({}, None):
                    return _respond(start_response, "400 Bad Request", {"error":"unexpected_fields"})
                return _respond(start_response, "201 Created", service.create_order(account_id))
            if path == "/api/payment/verify" and method == "POST":
                account_id = authenticate(environ)
                if not account_id:
                    return _respond(start_response, "401 Unauthorized", {"error":"authentication_required"})
                body = _read_json(environ)
                if not isinstance(body, dict) or set(body) != {"order_id","payment_id","signature"}:
                    return _respond(start_response, "400 Bad Request", {"error":"invalid_request"})
                return _respond(start_response, "200 OK", service.verify_checkout(account_id, body["order_id"], body["payment_id"], body["signature"]))
            if path == "/api/payment/webhook" and method == "POST":
                raw = _read_raw(environ)
                signature = environ.get("HTTP_X_RAZORPAY_SIGNATURE", "")
                event_id = environ.get("HTTP_X_RAZORPAY_EVENT_ID", "")
                return _respond(start_response, "200 OK", service.process_webhook(raw, signature, event_id))
            return _respond(start_response, "404 Not Found", {"error":"not_found"})
        except PaymentLicenseError as exc:
            # Keep response generic; do not leak provider internals.
            return _respond(start_response, "400 Bad Request", {"error":"payment_verification_failed"})
        except (ValueError, json.JSONDecodeError):
            return _respond(start_response, "400 Bad Request", {"error":"invalid_request"})
        except Exception:
            return _respond(start_response, "503 Service Unavailable", {"error":"payment_service_unavailable"})
    return app

def _read_raw(environ):
    try:
        length = int(environ.get("CONTENT_LENGTH") or "0")
    except ValueError:
        raise ValueError("invalid content length")
    if length < 0 or length > 1024 * 1024:
        raise ValueError("request body too large")
    return environ["wsgi.input"].read(length)

def _read_json(environ):
    raw = _read_raw(environ)
    if not raw:
        return {}
    return json.loads(raw.decode("utf-8"))

def _respond(start_response, status, payload):
    body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    start_response(status, [("Content-Type","application/json; charset=utf-8"),
                            ("Content-Length", str(len(body))),
                            ("Cache-Control", "no-store")])
    return [body]
