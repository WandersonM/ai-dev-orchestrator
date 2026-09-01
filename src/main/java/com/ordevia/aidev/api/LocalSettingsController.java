package com.ordevia.aidev.api;

import com.ordevia.aidev.agent.codex.CodexCliAgentRunner;
import com.ordevia.aidev.config.LocalSettingsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/local-settings")
public class LocalSettingsController {
    private final LocalSettingsService settings;
    private final CodexCliAgentRunner codex;

    public LocalSettingsController(LocalSettingsService settings, CodexCliAgentRunner codex) {
        this.settings = settings;
        this.codex = codex;
    }

    @GetMapping
    public SettingsView get() {
        return new SettingsView(settings.get(), codex.status());
    }

    @PutMapping
    public SettingsView update(@RequestBody LocalSettingsService.UpdateLocalSettingsRequest request) {
        return new SettingsView(settings.save(request), codex.status());
    }

    public record SettingsView(LocalSettingsService.LocalSettingsView settings,
                               CodexCliAgentRunner.Status codex) {}
}
