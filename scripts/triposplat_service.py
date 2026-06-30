#!/usr/bin/env python3
"""FastAPI adapter for VAST-AI-Research/TripoSplat.

The Spring Boot app calls this service as a local GPU worker. It mirrors the
official Gradio flow: preprocess -> encode -> sample -> decode -> save PLY/SPLAT.
When the repo or checkpoints are not configured, health and generation fail
explicitly instead of returning fake assets.
"""

from __future__ import annotations

import json
import os
import sys
import time
from functools import lru_cache
from pathlib import Path
from typing import Any

try:
    from fastapi import FastAPI, File, Form, HTTPException, UploadFile
    from fastapi.responses import FileResponse
except ImportError as exc:  # pragma: no cover - import guard for local setup clarity
    raise SystemExit("fastapi is required: pip install fastapi uvicorn python-multipart") from exc


TRIPOSPLAT_REPO_DIR = os.getenv("TRIPOSPLAT_REPO_DIR", "").strip()
if TRIPOSPLAT_REPO_DIR:
    sys.path.insert(0, TRIPOSPLAT_REPO_DIR)

DEFAULT_CKPT_ROOT = Path(os.getenv("TRIPOSPLAT_CKPT_ROOT", "ckpts"))
DEFAULT_DEVICE = os.getenv("TRIPOSPLAT_DEVICE", "cuda")
OUT_ROOT = Path(os.getenv("TRIPOSPLAT_OUTPUT_ROOT", "output/triposplat-jobs")).resolve()
OUT_ROOT.mkdir(parents=True, exist_ok=True)

app = FastAPI(title="Spring AI TripoSplat Adapter", version="0.1.0")


def checkpoint_paths() -> dict[str, Path]:
    return {
        "ckpt_path": Path(os.getenv(
            "TRIPOSPLAT_DIFFUSION_CKPT",
            DEFAULT_CKPT_ROOT / "diffusion_models" / "triposplat_fp16.safetensors",
        )),
        "decoder_path": Path(os.getenv(
            "TRIPOSPLAT_DECODER_CKPT",
            DEFAULT_CKPT_ROOT / "vae" / "triposplat_vae_decoder_fp16.safetensors",
        )),
        "dinov3_path": Path(os.getenv(
            "TRIPOSPLAT_DINOV3_CKPT",
            DEFAULT_CKPT_ROOT / "clip_vision" / "dino_v3_vit_h.safetensors",
        )),
        "flux2_vae_encoder_path": Path(os.getenv(
            "TRIPOSPLAT_FLUX2_VAE_CKPT",
            DEFAULT_CKPT_ROOT / "vae" / "flux2-vae.safetensors",
        )),
        "rmbg_path": Path(os.getenv(
            "TRIPOSPLAT_RMBG_CKPT",
            DEFAULT_CKPT_ROOT / "background_removal" / "birefnet.safetensors",
        )),
    }


def missing_requirements() -> list[str]:
    missing: list[str] = []
    try:
        import triposplat  # noqa: F401
    except Exception:
        missing.append("triposplat.py is not importable; set TRIPOSPLAT_REPO_DIR or PYTHONPATH")

    for key, path in checkpoint_paths().items():
        if not path.exists():
            missing.append(f"{key} not found: {path}")
    return missing


@lru_cache(maxsize=1)
def get_pipeline():
    missing = missing_requirements()
    if missing:
        return None, missing

    from triposplat import TripoSplatPipeline

    paths = checkpoint_paths()
    pipe = TripoSplatPipeline(
        ckpt_path=str(paths["ckpt_path"]),
        decoder_path=str(paths["decoder_path"]),
        dinov3_path=str(paths["dinov3_path"]),
        flux2_vae_encoder_path=str(paths["flux2_vae_encoder_path"]),
        rmbg_path=str(paths["rmbg_path"]),
        device=DEFAULT_DEVICE,
    )
    return pipe, []


@app.get("/health")
def health() -> dict[str, Any]:
    missing = missing_requirements()
    return {
        "success": len(missing) == 0,
        "configured": len(missing) == 0,
        "message": "TripoSplat adapter is ready" if not missing else "TripoSplat adapter is not configured",
        "missing": missing,
        "device": DEFAULT_DEVICE,
        "outputRoot": str(OUT_ROOT),
    }


@app.post("/generate")
async def generate(
    image: UploadFile = File(...),
    job_id: str = Form(...),
    output_dir: str = Form(""),
    prompt: str = Form(""),
    kind: str = Form("character"),
    quality: str = Form("balanced"),
    seed: int = Form(42),
    steps: int = Form(20),
    guidance_scale: float = Form(3.0),
    num_gaussians: int = Form(32768),
) -> dict[str, Any]:
    pipe, missing = get_pipeline()
    if pipe is None:
        raise HTTPException(
            status_code=503,
            detail={
                "success": False,
                "message": "TripoSplat adapter is not ready",
                "missing": missing,
            },
        )

    safe_job_id = "".join(ch for ch in job_id if ch.isalnum() or ch in ("-", "_"))[:80] or "job"
    job_dir = Path(output_dir).resolve() if output_dir else OUT_ROOT / safe_job_id
    job_dir.mkdir(parents=True, exist_ok=True)
    input_path = job_dir / "input-upload"
    input_path.write_bytes(await image.read())

    started = time.time()
    import torch

    # This follows the official run_gradio.py flow explicitly so downstream code
    # can attach progress events later without changing the contract.
    prepared = pipe.preprocess_image(input_path)
    generator = torch.Generator(device=pipe._device).manual_seed(int(seed))
    cond = pipe.encode_image(prepared, generator=generator)
    out = pipe.sample_latent(
        cond,
        steps=int(steps),
        guidance_scale=float(guidance_scale),
        generator=generator,
        show_progress=False,
    )
    gaussian = pipe.decode_latent(out["latent"], num_gaussians=int(num_gaussians))

    preprocessed_path = job_dir / "preprocessed.webp"
    ply_path = job_dir / "splat.ply"
    splat_path = job_dir / "splat.splat"
    manifest_path = job_dir / "manifest.json"

    prepared.save(preprocessed_path)
    gaussian.save_ply(str(ply_path))
    gaussian.save_splat(str(splat_path))

    metrics = {
        "gaussians": int(gaussian.get_xyz.shape[0]),
        "generationSeconds": round(time.time() - started, 2),
        "seed": int(seed),
        "steps": int(steps),
        "guidanceScale": float(guidance_scale),
    }
    outputs = [
        artifact(preprocessed_path, "image/webp", "preprocessed"),
        artifact(ply_path, "application/octet-stream", "ply"),
        artifact(splat_path, "application/octet-stream", "splat"),
    ]
    manifest = {
        "generator": "spring-ai-triposplat-adapter",
        "jobId": safe_job_id,
        "prompt": prompt,
        "kind": kind,
        "quality": quality,
        "metrics": metrics,
        "outputs": outputs,
    }
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    outputs.append(artifact(manifest_path, "application/json", "manifest"))

    return {
        "success": True,
        "message": "TripoSplat generation completed",
        "jobId": safe_job_id,
        "outputs": outputs,
        "metrics": metrics,
    }


@app.get("/files/{job_id}/{file_name}")
def file(job_id: str, file_name: str):
    if "/" in file_name or "\\" in file_name or ".." in file_name:
        raise HTTPException(status_code=400, detail="invalid file name")
    path = (OUT_ROOT / job_id / file_name).resolve()
    if not path.exists() or not str(path).startswith(str(OUT_ROOT)):
        raise HTTPException(status_code=404, detail="file not found")
    return FileResponse(path)


def artifact(path: Path, content_type: str, role: str) -> dict[str, Any]:
    return {
        "name": path.name,
        "type": content_type,
        "role": role,
        "bytes": path.stat().st_size,
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host=os.getenv("TRIPOSPLAT_HOST", "127.0.0.1"), port=int(os.getenv("TRIPOSPLAT_PORT", "7862")))
