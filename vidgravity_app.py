"""Unified WSGI runtime for VidGravity payment, recovery and admin components.

Configure the existing account authentication and license issuer through a small integration
module, referenced by VIDGRAVITY_INTEGRATION_MODULE. This runtime fails closed when that module
or required secrets are absent; it never creates a fake license or accepts client success flags.
"""
from __future__ import annotations
import importlib
import json
import os
from wsgiref.simple_server import make_server
from payment_store import PaymentStore
from payment_recovery import PaymentRecoveryService
from payment_admin import PaymentAdminService
from payment_license_service import PaymentLicenseService
from payment_routes_wsgi import make_payment_app
from payment_admin_wsgi import make_admin_app
from razorpay_adapter import RazorpayAdapter, RazorpayConfigurationError

class RuntimeConfigurationError(RuntimeError):
    pass

def load_integration():
    module_name = os.environ.get("VIDGRAVITY_INTEGRATION_MODULE", "")
    if not module_name:
        raise RuntimeConfigurationError("Set VIDGRAVITY_INTEGRATION_MODULE to the real account/license integration module")
    try:
        mod = importlib.import_module(module_name)
    except ImportError:
        raise RuntimeConfigurationError("Configured integration module could not be imported") from None
    authenticate = getattr(mod, "authenticate", None)
    issue_license = getattr(mod, "issue_license", None)
    if not callable(authenticate) or not callable(issue_license):
        raise RuntimeConfigurationError("Integration module must expose callable authenticate(environ) and issue_license(**kwargs)")
    return authenticate, issue_license

def combine_apps(payment_app, admin_app):
    def app(environ, start_response):
        path = environ.get("PATH_INFO", "")
        if path.startswith("/api/admin/payments/"):
            return admin_app(environ, start_response)
        if path.startswith("/api/payment/"):
            return payment_app(environ, start_response)
        if path == "/health":
            body = json.dumps({"status":"ok","component":"vidgravity-payment-runtime"}).encode()
            start_response("200 OK", [("Content-Type","application/json"),("Content-Length",str(len(body))),("Cache-Control","no-store")])
            return [body]
        body = json.dumps({"service":"VidGravity payment runtime","routes":["/health","/api/payment/*","/api/admin/payments/*"]}).encode()
        start_response("200 OK", [("Content-Type","application/json"),("Content-Length",str(len(body))),("Cache-Control","no-store")])
        return [body]
    return app

def build_app():
    db_path = os.environ.get("VIDGRAVITY_PAYMENT_DB", "vidgravity_payments.sqlite3")
    admin_token = os.environ.get("VIDGRAVITY_PAYMENT_ADMIN_TOKEN", "")
    if not admin_token:
        raise RuntimeConfigurationError("VIDGRAVITY_PAYMENT_ADMIN_TOKEN must be set")
    authenticate, issue_license = load_integration()
    try:
        razorpay = RazorpayAdapter()
    except RazorpayConfigurationError as exc:
        raise RuntimeConfigurationError(str(exc)) from None
    store = PaymentStore(db_path)
    recovery = PaymentRecoveryService(store, issue_license)
    payment_service = PaymentLicenseService(store, razorpay, issue_license)
    admin_service = PaymentAdminService(store, recovery)
    # The same admin token is checked by payment_admin; deployment must restrict access.
    return combine_apps(make_payment_app(payment_service, authenticate), make_admin_app(admin_service))

def main():
    host = os.environ.get("VIDGRAVITY_HOST", "127.0.0.1")
    port = int(os.environ.get("VIDGRAVITY_PORT", "8765"))
    app = build_app()
    with make_server(host, port, app) as server:
        print(f"VidGravity payment runtime listening on http://{host}:{port}")
        server.serve_forever()

if __name__ == "__main__":
    main()
