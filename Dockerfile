FROM python:3.10-slim

RUN apt-get update && apt-get install -y ffmpeg git

RUN pip install --no-cache-dir openai-whisper fastapi uvicorn

WORKDIR /app
COPY app.py .

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
