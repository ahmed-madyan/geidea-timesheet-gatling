#!/usr/bin/env python3
"""Sign in to the live timesheet and rewrite the lookup CSVs from the form."""

import base64
import csv
import html.parser
import http.cookiejar
import io
import json
import os
import re
import socket
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

# The runners have an IPv6 address that does not reach this host. Browsers fall
# back to IPv4; Python and Java wait on IPv6 until the connect timeout.
_getaddrinfo = socket.getaddrinfo


def _ipv4_only(host, port, family=0, type=0, proto=0, flags=0):
    return _getaddrinfo(host, port, socket.AF_INET, type, proto, flags)


socket.getaddrinfo = _ipv4_only

BASE = os.environ.get("TIMESHEET_BASE_URL", "https://timesheet.geidea.net").rstrip("/")
ROOT = Path(__file__).resolve().parents[1]
TARGETS = {
    "ddlDepartment": ("departments.csv", "Department"),
    "ddlCountry": ("countries.csv", "Country"),
    "ddlProject": ("projects.csv", "Project"),
}


class SelectParser(html.parser.HTMLParser):
    def __init__(self):
        super().__init__()
        self._select = None
        self._value = None
        self._text = []
        self.options = {key: [] for key in TARGETS}

    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        if tag == "select" and attributes.get("id") in self.options:
            self._select = attributes["id"]
        elif tag == "option" and self._select:
            self._value = attributes.get("value", "")
            self._text = []

    def handle_data(self, data):
        if self._value is not None:
            self._text.append(data)

    def handle_endtag(self, tag):
        if tag == "option" and self._select and self._value is not None:
            label = "".join(self._text).strip()
            if self._value != "":
                self.options[self._select].append((self._value, label))
            self._value = None
        elif tag == "select":
            self._select = None


def token_from(page):
    marker = 'name="__RequestVerificationToken"'
    start = page.find(marker)
    if start < 0:
        raise SystemExit("Login page did not include a verification token.")
    value = page.find('value="', start)
    end = page.find('"', value + 7)
    return page[value + 7 : end]


def fetch(opener, url, data=None):
    request = urllib.request.Request(url, data=data)
    attempts = 3
    for attempt in range(1, attempts + 1):
        try:
            with opener.open(request, timeout=60) as response:
                return response.read().decode("utf-8", "replace"), response.geturl()
        except (TimeoutError, urllib.error.URLError) as error:
            reason = getattr(error, "reason", error)
            if attempt == attempts:
                raise SystemExit(f"Could not reach {url} after {attempts} attempts: {reason}") from error
            print(f"Attempt {attempt} failed for {url}: {reason}. Retrying.")
            time.sleep(5)


def csv_text(header, rows):
    buffer = io.StringIO()
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerow(["id", header])
    writer.writerows(rows)
    return buffer.getvalue()


def write_csv(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


DRIVE_SITE = "https://mydrive.geidea.net/personal/ahmed_madyan"
DRIVE_FOLDER = "/personal/ahmed_madyan/Documents/Shared with Everyone/Start Time Sheet"


class OneDriveError(Exception):
    pass


def curl_ntlm(username, password, args):
    completed = subprocess.run(
        ["curl", "--silent", "--show-error", "--connect-timeout", "20", "--max-time", "60",
         "--ntlm", "-u", f"{username}:{password}", *args],
        check=False,
        capture_output=True,
    )
    if completed.returncode != 0:
        detail = completed.stderr.decode("utf-8", "replace").strip()
        raise OneDriveError(detail or f"curl exited {completed.returncode}")
    return completed.stdout


def upload_page():
    username = os.environ.get("ONEDRIVE_USERNAME", "")
    password = os.environ.get("ONEDRIVE_PASSWORD", "")
    if not username or not password:
        print(
            "OneDrive upload skipped. Set ONEDRIVE_USERNAME and ONEDRIVE_PASSWORD "
            "to replace start-timesheet.html in Start Time Sheet."
        )
        return
    try:
        folder = urllib.parse.quote(DRIVE_FOLDER)
        digest_raw = curl_ntlm(username, password, [
            "-X", "POST",
            "-H", "Accept: application/json;odata=verbose",
            "-H", "Content-Length: 0",
            f"{DRIVE_SITE}/_api/contextinfo",
        ])
        digest = json.loads(digest_raw)["d"]["GetContextWebInformation"]["FormDigestValue"]
        page = ROOT / "start-timesheet/start-timesheet.html"
        uploaded = curl_ntlm(username, password, [
            "-X", "POST",
            "-H", "Accept: application/json;odata=verbose",
            "-H", f"X-RequestDigest: {digest}",
            "-H", "Content-Type: application/octet-stream",
            "--data-binary", f"@{page}",
            f"{DRIVE_SITE}/_api/web/GetFolderByServerRelativeUrl('{folder}')/Files/add(url='start-timesheet.html',overwrite=true)",
        ])
        name = json.loads(uploaded)["d"]["Name"]
    except (OneDriveError, KeyError, json.JSONDecodeError) as error:
        print(f"OneDrive upload skipped: {error}")
        return
    print(f"Uploaded {name} to {DRIVE_FOLDER}")


def write_page_lists(lists):
    page = ROOT / "start-timesheet/start-timesheet.html"
    source = page.read_text(encoding="utf-8")
    encoded = {
        name: base64.b64encode(text.encode("utf-8")).decode("ascii")
        for name, text in lists.items()
    }
    statement = "const lookupCsv = " + json.dumps(encoded) + ";"
    updated, count = re.subn(r"const lookupCsv = \{.*?\};", statement, source, count=1, flags=re.S)
    if count != 1:
        raise SystemExit("start-timesheet/start-timesheet.html is missing the lookup lists.")
    page.write_text(updated, encoding="utf-8")


def main():
    username = os.environ.get("TIMESHEET_USERNAME", "")
    password = os.environ.get("TIMESHEET_PASSWORD", "")
    if not username or not password:
        raise SystemExit("TIMESHEET_USERNAME and TIMESHEET_PASSWORD are required.")

    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    login_page, _ = fetch(opener, BASE + "/")
    body = urllib.parse.urlencode({
        "Username": username,
        "Password": password,
        "__RequestVerificationToken": token_from(login_page),
    }).encode()
    _, landed = fetch(opener, BASE + "/", body)
    if "login" in landed.lower() and "/Timesheet" not in landed:
        raise SystemExit("Sign-in did not open the timesheet.")

    entry, _ = fetch(opener, BASE + "/TimesheetEntry")
    parser = SelectParser()
    parser.feed(entry)

    lists = {}
    for select_id, (filename, header) in TARGETS.items():
        rows = parser.options[select_id]
        if not rows:
            raise SystemExit(f"{select_id} was empty on the time entry form.")
        text = csv_text(header, rows)
        write_csv(ROOT / "src/test/resources/data/timesheet" / filename, text)
        lists[filename] = text
        print(f"{filename}: {len(rows)} rows")
    write_page_lists(lists)
    upload_page()


if __name__ == "__main__":
    main()
