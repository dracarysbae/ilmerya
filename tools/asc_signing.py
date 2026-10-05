"""App Store Connect API helper for Ilmerya signing. Never prints keys, passwords or certificates.

Usage:
  python tools/asc_signing.py status                 # bundle ID, certificates, profiles (names only)
  python tools/asc_signing.py setup                  # bundle ID + Game Center, distribution cert, App Store profile,
                                                     # then GitHub environment secrets for dracarysbae/ilmerya
Signing material is kept in %USERPROFILE%\\.ilmerya-signing (outside the repository).
"""
import base64, json, os, secrets, subprocess, sys, time
from pathlib import Path
import jwt, requests
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

KEY_ID = "HCVRZ6XS27"
ISSUER = "387a070f-0bbc-42ec-9760-7cca8d590ee7"
KEY_FILE = Path(os.environ["USERPROFILE"]) / "Downloads" / f"AuthKey_{KEY_ID}.p8"
BUNDLE = "com.ozgames.ilmerya"
REPO, ENV = "dracarysbae/ilmerya", "apple-distribution"
STORE = Path(os.environ["USERPROFILE"]) / ".ilmerya-signing"
API = "https://api.appstoreconnect.apple.com/v1"


def token():
    now = int(time.time())
    return jwt.encode({"iss": ISSUER, "iat": now, "exp": now + 1100, "aud": "appstoreconnect-v1"},
                      KEY_FILE.read_text(), algorithm="ES256", headers={"kid": KEY_ID, "typ": "JWT"})


def call(method, path, body=None, params=None):
    r = requests.request(method, API + path, headers={"Authorization": f"Bearer {token()}"}, json=body, params=params, timeout=60)
    if r.status_code >= 400:
        errors = [e.get("detail") or e.get("title") for e in r.json().get("errors", [])] if r.content else []
        raise SystemExit(f"{method} {path} -> HTTP {r.status_code}: {errors}")
    return r.json() if r.content else {}


def bundle_id():
    found = call("GET", "/bundleIds", params={"filter[identifier]": BUNDLE})["data"]
    found = [b for b in found if b["attributes"]["identifier"] == BUNDLE]
    return found[0] if found else None


def status():
    b = bundle_id()
    print("bundle:", b and (b["id"], b["attributes"]["name"], b["attributes"]["platform"]))
    if b:
        caps = call("GET", f"/bundleIds/{b['id']}/bundleIdCapabilities")["data"]
        print("capabilities:", sorted(c["attributes"]["capabilityType"] for c in caps))
    certs = call("GET", "/certificates", params={"limit": 50})["data"]
    print("certificates:", [(c["attributes"]["certificateType"], c["attributes"]["name"], c["attributes"]["expirationDate"][:10]) for c in certs])
    profiles = call("GET", "/profiles", params={"limit": 50})["data"]
    print("profiles:", [(p["attributes"]["name"], p["attributes"]["profileType"], p["attributes"]["profileState"]) for p in profiles])


def secret(name, value):
    r = subprocess.run(["gh", "secret", "set", name, "--repo", REPO, "--env", ENV], input=value, text=True, capture_output=True)
    if r.returncode != 0:
        raise SystemExit(f"could not set {name}: {r.stderr.strip()}")
    print("secret set:", name)


def setup():
    STORE.mkdir(exist_ok=True)
    b = bundle_id()
    if not b:
        b = call("POST", "/bundleIds", {"data": {"type": "bundleIds", "attributes": {
            "identifier": BUNDLE, "name": "Ilmerya", "platform": "IOS"}}})["data"]
        print("bundle registered:", b["id"])
    caps = {c["attributes"]["capabilityType"] for c in call("GET", f"/bundleIds/{b['id']}/bundleIdCapabilities")["data"]}
    if "GAME_CENTER" not in caps:
        call("POST", "/bundleIdCapabilities", {"data": {"type": "bundleIdCapabilities", "attributes": {"capabilityType": "GAME_CENTER"},
            "relationships": {"bundleId": {"data": {"type": "bundleIds", "id": b["id"]}}}}})
        print("Game Center enabled")

    key_path, cert_path, pw_path = STORE / "ilmerya-distribution.key.pem", STORE / "ilmerya-distribution.cer", STORE / "ilmerya-distribution.p12.password"
    if cert_path.exists():
        cert_der = cert_path.read_bytes()
        key = serialization.load_pem_private_key(key_path.read_bytes(), None)
        cert_id = json.loads((STORE / "ilmerya-distribution.json").read_text())["id"]
    else:
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        key_path.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        csr = x509.CertificateSigningRequestBuilder().subject_name(x509.Name([
            x509.NameAttribute(NameOID.COMMON_NAME, "OzGAMES Ilmerya Distribution"),
            x509.NameAttribute(NameOID.EMAIL_ADDRESS, "ozgames.dev@gmail.com"),
            x509.NameAttribute(NameOID.COUNTRY_NAME, "TR")])).sign(key, hashes.SHA256())
        created = call("POST", "/certificates", {"data": {"type": "certificates", "attributes": {
            "certificateType": "DISTRIBUTION", "csrContent": csr.public_bytes(serialization.Encoding.PEM).decode()}}})["data"]
        cert_der = base64.b64decode(created["attributes"]["certificateContent"])
        cert_path.write_bytes(cert_der)
        cert_id = created["id"]
        (STORE / "ilmerya-distribution.json").write_text(json.dumps({"id": cert_id, "name": created["attributes"]["name"]}))
        print("distribution certificate created:", created["attributes"]["name"], created["attributes"]["expirationDate"][:10])
    cert = x509.load_der_x509_certificate(cert_der)
    password = pw_path.read_text() if pw_path.exists() else secrets.token_urlsafe(24)
    pw_path.write_text(password)
    p12 = pkcs12.serialize_key_and_certificates(b"Ilmerya Distribution", key, cert, None,
        serialization.BestAvailableEncryption(password.encode()))
    (STORE / "ilmerya-distribution.p12").write_bytes(p12)

    name = "Ilmerya App Store"
    for p in call("GET", "/profiles", params={"filter[name]": name})["data"]:
        call("DELETE", f"/profiles/{p['id']}")
    profile = call("POST", "/profiles", {"data": {"type": "profiles", "attributes": {"name": name, "profileType": "IOS_APP_STORE"},
        "relationships": {"bundleId": {"data": {"type": "bundleIds", "id": b["id"]}},
                          "certificates": {"data": [{"type": "certificates", "id": cert_id}]}}}})["data"]
    content = profile["attributes"]["profileContent"]
    (STORE / "ilmerya-appstore.mobileprovision").write_bytes(base64.b64decode(content))
    print("profile created:", profile["attributes"]["name"], profile["attributes"]["expirationDate"][:10])

    secret("APPLE_DISTRIBUTION_P12_B64", base64.b64encode(p12).decode())
    secret("APPLE_DISTRIBUTION_P12_PASSWORD", password)
    secret("ILMERYA_APPSTORE_PROFILE_B64", content)
    secret("ASC_KEY_ID", KEY_ID)
    secret("ASC_ISSUER_ID", ISSUER)
    secret("ASC_PRIVATE_KEY_B64", base64.b64encode(KEY_FILE.read_bytes()).decode())


def profile():
    """App Store profile for the existing team distribution certificate; saved outside the repository."""
    STORE.mkdir(exist_ok=True)
    b = bundle_id() or sys.exit("register the bundle ID first")
    certs = [c for c in call("GET", "/certificates", params={"limit": 50})["data"] if c["attributes"]["certificateType"] == "DISTRIBUTION"]
    cert = max(certs, key=lambda c: c["attributes"]["expirationDate"])
    name = "Ilmerya App Store"
    for p in call("GET", "/profiles", params={"filter[name]": name})["data"]:
        call("DELETE", f"/profiles/{p['id']}")
    created = call("POST", "/profiles", {"data": {"type": "profiles", "attributes": {"name": name, "profileType": "IOS_APP_STORE"},
        "relationships": {"bundleId": {"data": {"type": "bundleIds", "id": b["id"]}},
                          "certificates": {"data": [{"type": "certificates", "id": cert["id"]}]}}}})["data"]
    content = created["attributes"]["profileContent"]
    (STORE / "ilmerya-appstore.mobileprovision").write_bytes(base64.b64decode(content))
    print("profile created:", created["attributes"]["name"], created["attributes"]["expirationDate"][:10], "cert:", cert["attributes"]["name"])
    secret("ILMERYA_APPSTORE_PROFILE_B64", content)
    secret("ASC_KEY_ID", KEY_ID)
    secret("ASC_ISSUER_ID", ISSUER)
    secret("ASC_PRIVATE_KEY_B64", base64.b64encode(KEY_FILE.read_bytes()).decode())


def profile_secrets():
    """Reads the web-created "Ilmerya App Store" profile (read access suffices) and stores CI secrets."""
    STORE.mkdir(exist_ok=True)
    found = [p for p in call("GET", "/profiles", params={"filter[name]": "Ilmerya App Store"})["data"]
             if p["attributes"]["profileState"] == "ACTIVE"]
    if not found:
        sys.exit("profile not found")
    content = found[0]["attributes"]["profileContent"]
    (STORE / "ilmerya-appstore.mobileprovision").write_bytes(base64.b64decode(content))
    print("profile:", found[0]["attributes"]["name"], found[0]["attributes"]["profileType"], found[0]["attributes"]["expirationDate"][:10])
    secret("ILMERYA_APPSTORE_PROFILE_B64", content)
    secret("ASC_KEY_ID", KEY_ID)
    secret("ASC_ISSUER_ID", ISSUER)
    secret("ASC_PRIVATE_KEY_B64", base64.b64encode(KEY_FILE.read_bytes()).decode())


if __name__ == "__main__":
    {"status": status, "setup": setup, "profile": profile, "profile-secrets": profile_secrets}[sys.argv[1] if len(sys.argv) > 1 else "status"]()
