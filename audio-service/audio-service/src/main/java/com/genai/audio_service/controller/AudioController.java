package com.genai.audio_service.controller;

import com.genai.audio_service.service.AudioService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/audio")
public class AudioController {

    private final AudioService audioService;

    public AudioController(AudioService audioService) {
        this.audioService = audioService;
    }

    @PostMapping("/process")
    public Map<String, Object> processAudio(@RequestBody Map<String, Object> request) {

        String applicationId = String.valueOf(request.get("applicationId"));
        String encodedAudioPath = String.valueOf(request.get("encodedAudioPath"));

        // NEW: language input (en|hi|auto). Default to auto if not provided.
        String language = "auto";
        if (request.get("language") != null) {
            language = String.valueOf(request.get("language")).trim().toLowerCase();
        }

        String transcript = audioService.process(encodedAudioPath, language);

        Map<String, Object> response = new HashMap<>();
        response.put("applicationId", applicationId);
        response.put("status", "SUCCESS");
        response.put("transcript", transcript);

        return response;
    }
}
