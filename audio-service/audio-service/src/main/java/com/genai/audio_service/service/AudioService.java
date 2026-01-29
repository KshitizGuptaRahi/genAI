package com.genai.audio_service.service;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class AudioService {

        private final WebClient webClient = WebClient.builder()
            .baseUrl("http://0.0.0.0:8000") // Python Whisper service
            .build();

        public String process(String encodedAudioPath) {

                // 1️⃣ Decode Base64 → original Windows path
                String decodedPath = new String(
                        Base64.getDecoder().decode(encodedAudioPath),
                        StandardCharsets.UTF_8
                );

                // 2️⃣ Extract only filename (IMPORTANT)
                String fileName = new File(decodedPath).getName();

                // 3️⃣ Build Docker-visible path
                String audioPath = "/audio/" + fileName;

                System.out.println("FINAL AUDIO PATH SENT TO WHISPER = " + audioPath);

                // 4️⃣ Call Whisper
                Map<String, Object> body = Map.of(
                        "path", audioPath,
                        "translate", true
                );

                long start = System.currentTimeMillis();

                Map<String, Object> whisperResponse = webClient.post()
                        .uri("/transcribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();

                long end = System.currentTimeMillis();

                System.out.println("Total API call time = " + (end - start) + " ms");

                return whisperResponse.get("text").toString();
       }
}
