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

        String applicationId = request.get("applicationId").toString();
        String encodedAudioPath = request.get("encodedAudioPath").toString();

        String transcript = audioService.process(encodedAudioPath);

        Map<String, Object> response = new HashMap<>();
        response.put("applicationId", applicationId);
        response.put("status", "SUCCESS");
        response.put("transcript", transcript);

        return response;
    }
}
