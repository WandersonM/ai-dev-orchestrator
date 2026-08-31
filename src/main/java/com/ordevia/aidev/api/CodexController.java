package com.ordevia.aidev.api;

import com.ordevia.aidev.agent.codex.CodexCliAgentRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/codex")
public class CodexController {
    private final CodexCliAgentRunner codex;

    public CodexController(CodexCliAgentRunner codex) {
        this.codex = codex;
    }

    @GetMapping("/status")
    public CodexCliAgentRunner.Status status() {
        return codex.status();
    }
}
