#!/usr/bin/env python3
"""
Checks the compose setup end to end, from outside, with nothing but Python: both instances ready and connected on
their mesh, a sign-in through nginx, a session ended on one instance refused by the other, nginx's limit on /api/auth,
and Prometheus scraping both instances. Run it after `docker compose up -d`.
"""
import base64, hashlib, hmac, json, sys, time, urllib.error, urllib.request

NGINX, STORE_A, STORE_B, PROMETHEUS = "http://localhost:8080", "http://localhost:8081", "http://localhost:8082", "http://localhost:9090"
failures = []


def call(base, path, body=None, token=None, method=None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(base + path, data=data, method=method or ("POST" if data else "GET"),
                                     headers={"Content-Type": "application/json"})
    if token:
        request.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


def check(what, ok):
    print(("ok   " if ok else "FAIL ") + what)
    if not ok:
        failures.append(what)


def wait_for(what, condition, seconds=120):
    until = time.time() + seconds
    while time.time() < until:
        try:
            if condition():
                return True
        except OSError:
            pass
        time.sleep(2)
    return False


def base36(n):
    digits = "0123456789abcdefghijklmnopqrstuvwxyz"
    text = ""
    while n:
        n, r = divmod(n, 36)
        text = digits[r] + text
    return text or "0"


def sign_in(base, user, password):
    """SCRAM-SHA-256 as the store's clients do it (the password's digest is the PBKDF2 input)."""
    _, body = call(base, "/api/auth/challenge", {"userName": user})
    challenge = json.loads(body)
    digest = base36(int.from_bytes(hashlib.md5(password.encode()).digest(), "big")).encode()
    salted = hashlib.pbkdf2_hmac("sha256", digest, base64.b64decode(challenge["salt"]), challenge["iterations"], 32)
    client_key = hmac.new(salted, b"Client Key", "sha256").digest()
    message = f"n={user},r={challenge['nonce']}".encode()
    signature = hmac.new(hashlib.sha256(client_key).digest(), message, "sha256").digest()
    proof = base64.b64encode(bytes(a ^ b for a, b in zip(client_key, signature))).decode()
    status, body = call(base, "/api/auth/login", {"userName": user, "proof": proof, "nonce": challenge["nonce"]})
    if status != 200:
        raise SystemExit(f"sign-in failed: {status} {body[:200]}")
    return json.loads(body)


def ready(base):
    status, body = call(base, "/health/ready")
    return json.loads(body) if status == 200 else None


check("nginx answers, both instances behind it ready", wait_for("ready", lambda: ready(NGINX) is not None))
check("each instance sees the other on the mesh",
      wait_for("mesh", lambda: all((ready(b) or {}).get("cluster", {}).get("peers") == 1 for b in (STORE_A, STORE_B)), 90))

session = sign_in(NGINX, "admin", "admin")
token = session["accessToken"]
check("signed in through nginx", bool(token))
check("the token works on instance A", call(STORE_A, "/api/repo/product/count", {}, token)[0] == 200)
check("and on instance B", call(STORE_B, "/api/repo/product/count", {}, token)[0] == 200)
call(STORE_A, "/api/auth/logout", {"refreshToken": session["refreshToken"]})
time.sleep(1)
check("signed out on A, the token is refused by B (the mesh told it)", call(STORE_B, "/api/repo/product/count", {}, token)[0] == 401)

statuses = [call(NGINX, "/api/auth/challenge", {"userName": "nobody"})[0] for _ in range(100)]
check(f"nginx limits /api/auth per address ({statuses.count(429)} of 100 answered 429)", 429 in statuses)


def targets_up():
    _, body = call(PROMETHEUS, "/api/v1/targets")
    targets = json.loads(body)["data"]["activeTargets"]
    return len(targets) == 2 and all(t["health"] == "up" for t in targets)


check("Prometheus scrapes both instances", wait_for("prometheus", targets_up, 60))

print("all checks passed" if not failures else f"{len(failures)} check(s) failed")
sys.exit(1 if failures else 0)
