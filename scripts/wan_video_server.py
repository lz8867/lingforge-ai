#!/usr/bin/env python3
"""Small HTTP bridge for Wan2.1 text-to-video generation.

The Spring app calls this service at:
  POST /api/text-to-video
  GET  /api/text-to-video/status/{task_id}
  GET  /api/text-to-video/health

It wraps the official Wan generate command instead of faking output:
  python generate.py --task t2v-1.3B --size 832*480 --ckpt_dir ... --prompt ... --save_file ...
"""

from __future__ import annotations

import json
import mimetypes
import os
import subprocess
import sys
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlparse


MODEL_NAME = "Wan2.1-T2V-1.3B"
WAN_TASK_NAME = "t2v-1.3B"
WAN_SIZE = "832*480"
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 7861
ROOT_DIR = Path(__file__).resolve().parents[1]
TASKS: dict[str, dict[str, object]] = {}
TASK_LOCK = threading.Lock()


def repo_dir() -> Path:
    return Path(os.environ.get("WAN_REPO_DIR", ROOT_DIR / "Wan2.1")).expanduser().resolve()


def python_bin() -> str:
    return os.environ.get("WAN_PYTHON", sys.executable)


def checkpoint_dir() -> Path:
    configured = os.environ.get("WAN_CHECKPOINT_DIR") or os.environ.get("WAN_CKPT_DIR")
    if configured:
        return Path(configured).expanduser().resolve()
    return repo_dir() / MODEL_NAME


def output_dir() -> Path:
    configured = os.environ.get("WAN_OUTPUT_DIR")
    path = Path(configured).expanduser() if configured else ROOT_DIR / "images" / "wan-video"
    return path.resolve()


def service_host() -> str:
    return os.environ.get("WAN_VIDEO_HOST") or os.environ.get("WAN_HOST", DEFAULT_HOST)


def service_port() -> int:
    return int(os.environ.get("WAN_VIDEO_PORT") or os.environ.get("WAN_PORT", str(DEFAULT_PORT)))


def public_video_url(file_path: Path) -> str:
    public_base = os.environ.get("WAN_PUBLIC_BASE_URL", f"http://{service_host()}:{service_port()}/videos").rstrip("/")
    return f"{public_base}/{file_path.name}"


def service_configured() -> tuple[bool, str]:
    generate_script = repo_dir() / "generate.py"
    if not generate_script.exists():
        return False, f"generate.py not found under WAN_REPO_DIR={repo_dir()}"
    if not checkpoint_dir().exists():
        return False, f"checkpoint directory not found: {checkpoint_dir()}"
    return True, "Wan2.1 command is configured"


def build_wan_command(prompt: str, save_file: Path) -> list[str]:
    return [
        python_bin(),
        "generate.py",
        "--task",
        WAN_TASK_NAME,
        "--size",
        WAN_SIZE,
        "--ckpt_dir",
        str(checkpoint_dir()),
        "--prompt",
        prompt,
        "--save_file",
        str(save_file),
    ]


def update_task(task_id: str, **changes: object) -> None:
    with TASK_LOCK:
        task = TASKS.setdefault(task_id, {})
        task.update(changes)
        task["updatedAt"] = time.time()


def task_snapshot(task_id: str) -> dict[str, object] | None:
    with TASK_LOCK:
        task = TASKS.get(task_id)
        return dict(task) if task is not None else None


def run_wan_task(task_id: str, prompt: str) -> None:
    save_file = output_dir() / f"{task_id}.mp4"
    try:
        configured, message = service_configured()
        if not configured:
            raise RuntimeError(message)

        output_dir().mkdir(parents=True, exist_ok=True)
        command = build_wan_command(prompt, save_file)
        timeout = int(os.environ.get("WAN_GENERATE_TIMEOUT_SECONDS", "7200"))
        update_task(task_id, status="PROCESSING", command=" ".join(command), saveFile=str(save_file))

        completed = subprocess.run(
            command,
            cwd=repo_dir(),
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=timeout,
            check=False,
        )
        if completed.returncode != 0:
            raise RuntimeError(completed.stdout.strip() or f"Wan command exited with {completed.returncode}")
        if not save_file.exists():
            raise RuntimeError(f"Wan command completed but did not create {save_file}")

        video = public_video_url(save_file)
        update_task(
            task_id,
            success=True,
            status="SUCCESS",
            message="Wan2.1 video generated",
            video=video,
            videoUrl=video,
            url=video,
            output=completed.stdout[-4000:],
        )
    except Exception as exc:  # noqa: BLE001 - boundary handler should return the concrete failure.
        update_task(
            task_id,
            success=False,
            status="FAILED",
            message="Wan2.1 video generation failed",
            error=str(exc),
        )


class WanVideoHandler(BaseHTTPRequestHandler):
    server_version = "WanVideoBridge/1.0"

    def do_OPTIONS(self) -> None:  # noqa: N802 - stdlib handler API.
        self.send_response(204)
        self._send_common_headers()
        self.end_headers()

    def do_GET(self) -> None:  # noqa: N802 - stdlib handler API.
        parsed = urlparse(self.path)
        if parsed.path == "/api/text-to-video/health":
            configured, message = service_configured()
            self._send_json(200, {
                "success": True,
                "configured": configured,
                "canGenerate": configured,
                "ready": configured,
                "model": MODEL_NAME,
                "size": WAN_SIZE,
                "message": message,
            })
            return

        prefix = "/api/text-to-video/status/"
        if parsed.path.startswith(prefix):
            task_id = unquote(parsed.path[len(prefix):]).strip()
            task = task_snapshot(task_id)
            if task is None:
                self._send_json(404, {
                    "success": False,
                    "status": "NOT_FOUND",
                    "message": "Video task not found",
                    "taskId": task_id,
                    "model": MODEL_NAME,
                })
                return
            self._send_json(200, task)
            return

        if parsed.path.startswith("/videos/"):
            self._send_video(parsed.path.removeprefix("/videos/"))
            return

        self._send_json(404, {"success": False, "message": "Unknown endpoint"})

    def do_POST(self) -> None:  # noqa: N802 - stdlib handler API.
        parsed = urlparse(self.path)
        if parsed.path != "/api/text-to-video":
            self._send_json(404, {"success": False, "message": "Unknown endpoint"})
            return

        payload = self._read_json()
        prompt = str(payload.get("prompt") or "").strip()
        if not prompt:
            self._send_json(400, {
                "success": False,
                "status": "FAILED",
                "message": "Prompt is required",
                "model": MODEL_NAME,
            })
            return

        task_id = f"wan-{uuid.uuid4().hex[:12]}"
        update_task(
            task_id,
            success=True,
            taskId=task_id,
            status="PROCESSING",
            model=MODEL_NAME,
            size=WAN_SIZE,
            message="Wan2.1 task accepted",
            createdAt=time.time(),
        )
        thread = threading.Thread(target=run_wan_task, args=(task_id, prompt), daemon=True)
        thread.start()
        self._send_json(202, {
            "success": True,
            "taskId": task_id,
            "status": "PROCESSING",
            "model": MODEL_NAME,
            "size": WAN_SIZE,
            "message": "Wan2.1 task accepted",
        })

    def log_message(self, format: str, *args: object) -> None:
        sys.stderr.write("%s - - [%s] %s\n" % (
            self.address_string(),
            self.log_date_time_string(),
            format % args,
        ))

    def _read_json(self) -> dict[str, object]:
        content_length = int(self.headers.get("Content-Length", "0"))
        if content_length <= 0:
            return {}
        raw_body = self.rfile.read(content_length).decode("utf-8")
        try:
            body = json.loads(raw_body)
        except json.JSONDecodeError as exc:
            raise ValueError(f"Invalid JSON body: {exc}") from exc
        return body if isinstance(body, dict) else {}

    def _send_json(self, status: int, body: dict[str, object]) -> None:
        encoded = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self._send_common_headers()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def _send_common_headers(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")

    def _send_video(self, raw_file_name: str) -> None:
        root = output_dir()
        file_path = (root / unquote(raw_file_name)).resolve()
        try:
            file_path.relative_to(root)
        except ValueError:
            self._send_json(403, {"success": False, "message": "Invalid video path"})
            return
        if not file_path.is_file():
            self._send_json(404, {"success": False, "message": "Video file not found"})
            return

        content_type = mimetypes.guess_type(str(file_path))[0] or "video/mp4"
        self.send_response(200)
        self._send_common_headers()
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(file_path.stat().st_size))
        self.end_headers()
        with file_path.open("rb") as video_file:
            self.wfile.write(video_file.read())


def main() -> None:
    host = service_host()
    port = service_port()
    server = ThreadingHTTPServer((host, port), WanVideoHandler)
    print(f"Wan video bridge listening on http://{host}:{port}/api/text-to-video", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
