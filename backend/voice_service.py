"""Optional loopback Parakeet/Kokoro service. Never invoked or installed by the mod.
Run: python -m uvicorn voice_service:app --host 127.0.0.1 --port 8090
Model weights download on first use; set HF_HOME to choose a cache location.
"""
import io
import os
import secrets
import tempfile
import threading
from contextlib import contextmanager

from fastapi import FastAPI, HTTPException, Request
from fastapi.concurrency import run_in_threadpool
from pydantic import BaseModel, Field

app = FastAPI(title="Echo local voice", docs_url=None, redoc_url=None)
_gate = threading.BoundedSemaphore(1)
_asr = None
_tts = None


@app.middleware("http")
async def auth(request: Request, call_next):
    key = os.getenv("ECHO_VOICE_KEY", "")
    if key and not secrets.compare_digest(request.headers.get("authorization", "").encode(), ("Bearer " + key).encode()):
        from fastapi.responses import JSONResponse
        return JSONResponse({"detail": "Unauthorized"}, status_code=401)
    return await call_next(request)


@contextmanager
def inference_slot():
    if not _gate.acquire(blocking=False):
        raise HTTPException(503, "Voice model busy")
    try:
        yield
    finally:
        _gate.release()


def transcribe_wav(data: bytes) -> dict:
    global _asr
    import numpy as np
    import soundfile as sf
    with inference_slot():
        try:
            info = sf.info(io.BytesIO(data))
            if info.format not in {"WAV", "WAVEX"} or info.subtype != "PCM_16" or info.samplerate != 16000 or info.channels != 1 or not 1600 <= info.frames <= 240000:
                raise HTTPException(400, "Expected bounded PCM WAV")
            audio, rate = sf.read(io.BytesIO(data), dtype="float32")
        except Exception:
            raise HTTPException(400, "Invalid WAV") from None
        if rate != 16000 or audio.ndim != 1 or not 1600 <= len(audio) <= 240000 or not np.isfinite(audio).all():
            raise HTTPException(400, "Expected 0.1–15 seconds of 16 kHz mono WAV")
        if _asr is None:
            import nemo.collections.asr as nemo_asr
            _asr = nemo_asr.models.ASRModel.from_pretrained(os.getenv("ECHO_PARAKEET_MODEL", "nvidia/parakeet-tdt-0.6b-v2"))
            _asr.eval()
        # NeMo's audio loader accepts paths. The path is generated locally and never comes from the caller.
        path = None
        try:
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as wav:
                path = wav.name
                sf.write(wav, audio, rate, format="WAV", subtype="PCM_16")
            output = _asr.transcribe([path])
            first = output[0]
            return {"text": str(getattr(first, "text", first))[:1000]}
        finally:
            if path:
                os.unlink(path)


@app.post("/transcribe")
async def transcribe(request: Request):
    data = bytearray()
    async for chunk in request.stream():
        data.extend(chunk)
        if len(data) > 500000:
            raise HTTPException(413, "Recording too large")
    return await run_in_threadpool(transcribe_wav, bytes(data))


class Speech(BaseModel):
    text: str = Field(min_length=1, max_length=600)


def speak_text(text: str) -> bytes:
    global _tts
    import numpy as np
    import soundfile as sf
    with inference_slot():
        if _tts is None:
            from kokoro import KPipeline
            _tts = KPipeline(lang_code="a")
        chunks = [np.asarray(audio) for _, _, audio in _tts(text, voice=os.getenv("ECHO_KOKORO_VOICE", "af_heart"))]
        if not chunks:
            raise HTTPException(503, "No speech generated")
        audio = np.concatenate(chunks)
        if len(audio) > 1_900_000:
            raise HTTPException(413, "Speech too long")
        buffer = io.BytesIO()
        sf.write(buffer, audio, 24000, format="WAV", subtype="PCM_16")
        return buffer.getvalue()


@app.post("/speech")
async def speech(request: Request):
    # Bound the JSON body before Pydantic parses it.
    data = bytearray()
    async for chunk in request.stream():
        data.extend(chunk)
        if len(data) > 8192:
            raise HTTPException(413, "Speech request too large")
    try:
        body = Speech.model_validate_json(data)
    except ValueError:
        raise HTTPException(400, "Invalid speech request") from None
    from fastapi.responses import Response
    return Response(await run_in_threadpool(speak_text, body.text), media_type="audio/wav")


@app.get("/health")
def health():
    return {"status": "ready", "parakeet_loaded": _asr is not None, "kokoro_loaded": _tts is not None}
