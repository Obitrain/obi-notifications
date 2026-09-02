# /// script
# requires-python = ">=3.12"
# dependencies = ["google-auth>=2.30", "requests>=2.32"]
# ///
# DESCRIPTION
#   Send one FCM message through the HTTP v1 API using a Firebase service account.
#   The device token is passed explicitly or read from the example app's screen on an emulator.
# USAGE
#   uv run scripts/send-fcm.py --credentials <service-account.json> (--token <fcm-token> | --emulator <adb-serial>) [--notification] [key=value ...]
# EXAMPLES
#   uv run scripts/send-fcm.py --credentials ~/Projects/Obitrain/devops/static/fcm-credentials.json --emulator emulator-5560 kind=verify id=1
#   uv run scripts/send-fcm.py --credentials creds.json --token eEnF... --notification title="Hello" body="From the CLI"
import argparse
import json
import subprocess
import sys
import xml.etree.ElementTree as ET

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

SCOPE = "https://www.googleapis.com/auth/firebase.messaging"


def token_from_emulator(serial: str) -> str:
    """Reads the token text of the example app's `token` testID through uiautomator."""
    subprocess.run(["adb", "-s", serial, "shell", "uiautomator", "dump", "/sdcard/ui.xml"], check=True, capture_output=True)
    xml = subprocess.run(["adb", "-s", serial, "shell", "cat", "/sdcard/ui.xml"], check=True, capture_output=True, text=True).stdout
    for node in ET.fromstring(xml).iter("node"):
        if node.get("resource-id") == "token" and node.get("text", "(none)") != "(none)":
            return node.get("text", "")
    raise SystemExit("no FCM token on screen: tap 'Register for notifications' in the example app first")


def main() -> None:
    parser = argparse.ArgumentParser(description="Send an FCM v1 message.")
    parser.add_argument("--credentials", required=True, help="Firebase service-account JSON")
    parser.add_argument("--token", help="FCM device token")
    parser.add_argument("--emulator", help="adb serial to read the token from the example app screen")
    parser.add_argument("--notification", action="store_true", help="add a notification block (title/body from data) so FCM renders it itself in background")
    parser.add_argument("data", nargs="*", help="key=value pairs sent as the data payload")
    args = parser.parse_args()

    if not args.token and not args.emulator:
        parser.error("--token or --emulator is required")
    data = dict(item.split("=", 1) for item in args.data)
    data.setdefault("title", "CLI push")
    data.setdefault("body", "sent by scripts/send-fcm.py")

    creds = service_account.Credentials.from_service_account_file(args.credentials, scopes=[SCOPE])
    creds.refresh(Request())
    device_token = args.token or token_from_emulator(args.emulator)

    message: dict = {"token": device_token, "data": data, "android": {"priority": "high"}}
    if args.notification:
        message["notification"] = {"title": data["title"], "body": data["body"]}
    response = requests.post(
        f"https://fcm.googleapis.com/v1/projects/{creds.project_id}/messages:send",
        headers={"Authorization": f"Bearer {creds.token}"},
        json={"message": message},
        timeout=30,
    )
    print(json.dumps(response.json(), indent=2))
    if not response.ok:
        sys.exit(1)


if __name__ == "__main__":
    main()
