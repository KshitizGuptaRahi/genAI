package com.genai.audio_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AudioService {

    @Value("${stt.model.hi:}")
    private String hindiModelPath;

    @Value("${stt.model.en:}")
    private String englishModelPath;

    @Value("${stt.ffmpegPath:ffmpeg}")
    private String ffmpegPath;

    private final Map<String, Model> modelCache = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @param encodedAudioPath base64-encoded original path (your existing design)
     * @param language "en" | "hi" | "auto"
     */
    public String process(String encodedAudioPath, String language) {
        // 1) Decode Base64 -> original path
        String decodedPath = new String(Base64.getDecoder().decode(encodedAudioPath), StandardCharsets.UTF_8);
        Path input = Path.of(decodedPath);

        if (!Files.exists(input)) {
            throw new IllegalArgumentException("Audio file not found: " + input);
        }

        // 2) Validate configuration at runtime
        if (hindiModelPath == null || hindiModelPath.isBlank()) {
            throw new IllegalStateException("Missing config: stt.model.hi (set it in src/main/resources/application.yml)");
        }
        if (englishModelPath == null || englishModelPath.isBlank()) {
            throw new IllegalStateException("Missing config: stt.model.en (set it in src/main/resources/application.yml)");
        }

        String lang = (language == null ? "auto" : language.trim().toLowerCase());
        if (!lang.equals("hi") && !lang.equals("en") && !lang.equals("auto")) {
            throw new IllegalArgumentException("language must be 'hi', 'en', or 'auto'");
        }

        Path wav16k = null;
        try {
            // 3) Convert to 16kHz mono PCM WAV
            wav16k = ensure16kMonoWav(input);

            // 4) Choose model
            if (lang.equals("en") || lang.equals("hi")) {
                return runVoskText(wav16k, lang);
            }

            // AUTO: do a short probe + script heuristic, then run only chosen model on full audio
            String chosen = detectHiOrEnByProbe(wav16k);
            return runVoskText(wav16k, chosen);

        } catch (Exception e) {
            throw new RuntimeException("STT failed: " + e.getMessage(), e);
        } finally {
            if (wav16k != null && wav16k.toString().contains(System.getProperty("java.io.tmpdir"))) {
                try { Files.deleteIfExists(wav16k); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Auto-detect only between hi/en using a short audio probe.
     * Heuristic: if English transcript contains enough Latin letters/words, pick EN, else HI.
     */
    private String detectHiOrEnByProbe(Path wav16kMono) throws Exception {
        byte[] probeBytes = readFirstNSeconds(wav16kMono, 2.5);

        SttText en = runVoskOnBytes(probeBytes, "en");
        SttText hi = runVoskOnBytes(probeBytes, "hi");

        int enLatin = countLatinLetters(en.text);
        int hiDeva = countDevanagariLetters(hi.text);

        int enWords = wordCount(en.text);
        int hiWords = wordCount(hi.text);

        // Rule 1: If English output has meaningful Latin content, choose EN.
        // Thresholds are conservative to avoid false EN picks.
        if (enWords >= 2 && enLatin >= 6) return "en";
        if (enWords >= 4) return "en";

        // Rule 2: Otherwise, if Hindi output has Devanagari, choose HI.
        if (hiWords >= 1 && hiDeva >= 2) return "hi";

        // Rule 3: fallback to whichever has more words
        return (enWords >= hiWords) ? "en" : "hi";
    }

    private String runVoskText(Path wav16kMono, String lang) throws Exception {
        SttText out;
        try (InputStream is = new BufferedInputStream(Files.newInputStream(wav16kMono))) {
            out = runVoskOnStream(is, lang);
        }
        return out.text;
    }

    private SttText runVoskOnBytes(byte[] audioBytes, String lang) throws Exception {
        try (InputStream is = new ByteArrayInputStream(audioBytes)) {
            return runVoskOnStream(is, lang);
        }
    }

    private SttText runVoskOnStream(InputStream is, String lang) throws Exception {
        Model model = getModel(lang);

        try (Recognizer recognizer = new Recognizer(model, 16000.0f)) {
            // Optional: better diagnostics/alternatives if you want later
            // recognizer.setWords(true);
            // recognizer.setMaxAlternatives(2);

            byte[] buffer = new byte[4096];
            int n;
            while ((n = is.read(buffer)) >= 0) {
                recognizer.acceptWaveForm(buffer, n);
            }

            String finalJson = recognizer.getFinalResult();
            JsonNode node = mapper.readTree(finalJson);
            String text = node.has("text") ? node.get("text").asText() : "";
            return new SttText(lang, text == null ? "" : text.trim());
        }
    }

    private Model getModel(String lang) {
        return modelCache.computeIfAbsent(lang, l -> {
            try {
                String path = l.equals("hi") ? hindiModelPath : englishModelPath;
                if (path == null || path.isBlank()) throw new IllegalStateException("Model path is blank for: " + l);
                return new Model(path);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
    }

    /**
     * Reads first N seconds of 16kHz mono PCM WAV.
     * WAV header is 44 bytes; PCM16 mono 16k: 16000 samples/sec * 2 bytes = 32000 bytes/sec.
     */
    private byte[] readFirstNSeconds(Path wav16kMono, double seconds) throws IOException {
        byte[] all = Files.readAllBytes(wav16kMono);
        int header = Math.min(44, all.length);
        int bytesPerSecond = 16000 * 2; // mono PCM16
        int payloadBytes = (int) Math.min(all.length - header, Math.max(0, (long)(bytesPerSecond * seconds)));

        byte[] out = new byte[header + payloadBytes];
        System.arraycopy(all, 0, out, 0, header + payloadBytes);
        return out;
    }

    private int countLatinLetters(String s) {
        if (s == null) return 0;
        int c = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) c++;
        }
        return c;
    }

    private int countDevanagariLetters(String s) {
        if (s == null) return 0;
        int c = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            // Devanagari Unicode block: U+0900..U+097F
            if (ch >= 0x0900 && ch <= 0x097F) c++;
        }
        return c;
    }

    private int wordCount(String s) {
        if (s == null) return 0;
        String t = s.trim();
        if (t.isEmpty()) return 0;
        return t.split("\\s+").length;
    }

    private Path ensure16kMonoWav(Path input) throws IOException, InterruptedException {
        Path out = Files.createTempFile("stt-", "-16k-mono.wav");

        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-y",
                "-i", input.toAbsolutePath().toString(),
                "-ac", "1",
                "-ar", "16000",
                "-c:a", "pcm_s16le",
                out.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            while (br.readLine() != null) { /* consume */ }
        }

        int code = p.waitFor();
        if (code != 0) {
            throw new IOException("ffmpeg conversion failed. Exit code=" + code + ". Check ffmpegPath and input format.");
        }

        return out;
    }

    private static class SttText {
        final String lang;
        final String text;
        SttText(String lang, String text) {
            this.lang = lang;
            this.text = text;
        }
    }
}
