# 🎙️ genAI Audio Transcription System

A microservice-based audio transcription system built using:

- ☕ **Spring Boot (Java)** – API & orchestration layer  
- 🐍 **FastAPI + OpenAI Whisper (Python)** – Speech-to-text inference engine  
- 🐳 **Docker** – Containerized deployment & environment isolation  

This project demonstrates service-to-service communication, containerization, and ML model integration in a production-style architecture.

---

# 🏗️ Architecture Overview

```
Client
   ↓
Spring Boot (Java API Layer)
   ↓ HTTP (REST)
Whisper Service (Python + FastAPI)
   ↓
OpenAI Whisper Model
```

## 🔄 Request Flow

1. Client sends request to Spring Boot.
2. Spring decodes Base64 path and extracts the filename.
3. Spring calls Python `/transcribe` endpoint.
4. Python loads Whisper model and processes the audio file.
5. Transcribed text is returned to Spring.
6. Spring returns final structured response to client.

---

# 📂 Project Structure

```
genAI/
│
├── app.py                  # FastAPI Whisper service
├── Dockerfile              # Python service container definition
├── audio/                  # Place audio files here
├── audio-service/          # Spring Boot project
├── docker-compose.yml      # (Optional) Run both services together
└── README.md
```

---

# ⚙️ Prerequisites

- Java 17+
- Maven
- Docker Desktop (running)

---

# 🚀 How To Run

---

## ✅ Option 1: Run Python (Docker) + Java (Local)

### 1️⃣ Place audio file

Add your audio file inside:

```
genAI/audio/sample.wav
```

---

### 2️⃣ Build Whisper Docker Image

From project root:

```bash
docker build -t whisper-svc .
```

---

### 3️⃣ Run Whisper Container

PowerShell (Windows):

```powershell
docker run --rm -p 8000:8000 -v "${PWD}\audio:/audio" whisper-svc
```

Whisper will run at:

```
http://localhost:8000
```

---

### 4️⃣ Run Spring Boot

Inside `audio-service` directory:

```bash
mvn spring-boot:run
```

Spring Boot runs at:

```
http://localhost:8080
```

---

### 5️⃣ Call API

Generate Base64 string for file path (PowerShell):

```powershell
$raw = "C:\temp\sample.wav"
[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($raw))
```

Request Body:

```json
{
  "applicationId": "123",
  "encodedAudioPath": "<BASE64_STRING>"
}
```

Send to:

```
POST http://localhost:8080/audio/process
```

Response:

```json
{
  "applicationId": "123",
  "status": "SUCCESS",
  "transcript": "Transcribed text here"
}
```

---

## ✅ Option 2 (Recommended): Run Everything Using Docker Compose

### 1️⃣ Ensure Spring baseUrl is:

```java
.baseUrl("http://whisper:8000")
```

---

### 2️⃣ docker-compose.yml (Root Folder)

```yaml
services:
  whisper:
    build: .
    ports:
      - "8000:8000"
    volumes:
      - ./audio:/audio

  audio-service:
    build: ./audio-service
    ports:
      - "8080:8080"
    depends_on:
      - whisper
    volumes:
      - ./audio:/audio
```

---

### 3️⃣ Run Both Services

```bash
docker compose up --build
```

Access:

- Spring Boot → http://localhost:8080
- Whisper → http://localhost:8000

---

# 📡 API Endpoints

## Spring Boot

### POST `/audio/process`

Request:

```json
{
  "applicationId": "123",
  "encodedAudioPath": "Base64EncodedPath"
}
```

Response:

```json
{
  "applicationId": "123",
  "status": "SUCCESS",
  "transcript": "Transcribed text"
}
```

---

## Python Whisper (Internal Service)

### POST `/transcribe`

Request:

```json
{
  "path": "/audio/sample.wav",
  "translate": true
}
```

Returns Whisper transcription output.

---

# ⚠️ Common Issues

### File Not Found
Ensure:
- File exists in `audio/`
- Volume is mounted correctly

---

### Docker Networking Error
Use:

```
http://whisper:8000
```

NOT:

```
http://0.0.0.0:8000
```

---

### FP16 Warning

```
FP16 is not supported on CPU; using FP32 instead
```

This is normal. It means the model is running on CPU instead of GPU.

---

# 🔥 Future Improvements

- Send audio via multipart upload instead of file path
- Implement structured DTOs instead of raw Map
- Add logging & exception handling
- Add authentication layer
- Add GPU acceleration
- Add async processing & queue support

---

# 👨‍💻 Author

Kshitiz Gupta  
Software Developer | Backend & Systems Engineering
