import whisper
import time
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()
model = whisper.load_model("base")

class AudioReq(BaseModel):
    path: str
    translate: bool = True

@app.post("/transcribe")
def transcribe(req: AudioReq):
    print(f"Processing audio file: {req.path}")

    start_time = time.time()

    result = model.transcribe(
        req.path,
        task="translate" if req.translate else "transcribe"
    )

    end_time = time.time()
    elapsed = end_time - start_time

    print(f"Transcription took {elapsed:.2f} seconds")

    result["processing_time_seconds"] = elapsed

    return {"text": result["text"], "processing_time": result["processing_time_seconds"]}
