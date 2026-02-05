# app.py
import os
import time
from typing import Optional, Dict

import whisper
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI()

# Prefer "small" over "base" for better language robustness (still lightweight).
model = whisper.load_model("small")


class AudioReq(BaseModel):
    path: str
    # same-language output always (kept for compatibility with your Java payload)
    translate: bool = False
    # Optional override: "hi" or "en". If not provided, we auto-pick between hi/en only.
    language: Optional[str] = None


def detect_probs(path: str) -> Dict[str, float]:
    audio = whisper.load_audio(path)
    audio = whisper.pad_or_trim(audio)
    mel = whisper.log_mel_spectrogram(audio).to(model.device)
    _, probs = model.detect_language(mel)
    return probs


def pick_hi_en_only(probs: Dict[str, float]) -> str:
    hi_p = float(probs.get("hi", 0.0))
    en_p = float(probs.get("en", 0.0))
    return "hi" if hi_p >= en_p else "en"


@app.post("/transcribe")
def transcribe(req: AudioReq):
    if not os.path.exists(req.path):
        raise HTTPException(status_code=400, detail=f"Audio file not found: {req.path}")

    start = time.time()

    probs = detect_probs(req.path)
    lang = (req.language or "").strip().lower() or pick_hi_en_only(probs)

    # Hinting helps keep Hindi in Devanagari when lang="hi"
    prompt = "Hindi should be written in Devanagari script." if lang == "hi" else None

    result = model.transcribe(
        req.path,
        task="transcribe",              # same-language output
        language=lang,                  # force ONLY hi/en decision
        temperature=0.0,
        beam_size=5,
        best_of=5,
        condition_on_previous_text=False,
        initial_prompt=prompt,
    )

    elapsed = time.time() - start

    return {
        "text": (result.get("text") or "").strip(),
        "language": lang,  # "hi" or "en"
        "language_probs_top": sorted(probs.items(), key=lambda x: x[1], reverse=True)[:5],
        "processing_time_seconds": round(elapsed, 3),
    }
