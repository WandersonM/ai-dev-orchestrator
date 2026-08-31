package com.ordevia.aidev.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordevia.aidev.github.application.GitHubFeedbackService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/integrations/github")
public class GitHubWebhookController {
    private final ObjectMapper mapper;
    private final GitHubFeedbackService feedback;
    private final String webhookSecret;

    public GitHubWebhookController(ObjectMapper mapper,
                                   GitHubFeedbackService feedback,
                                   @Value("${aidev.github.webhook-secret:}") String webhookSecret) {
        this.mapper = mapper;
        this.feedback = feedback;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(@RequestHeader(value="X-GitHub-Event", required=false) String event,
                                     @RequestHeader(value="X-Hub-Signature-256", required=false) String signature,
                                     @RequestBody String rawBody) throws Exception {
        verifySignature(rawBody, signature);
        JsonNode payload = mapper.readTree(rawBody);
        if ("ping".equals(event)) return ResponseEntity.ok(new WebhookResult("pong", null));

        String repository = payload.path("repository").path("full_name").asText(null);
        int number = payload.path("pull_request").path("number").asInt(payload.path("issue").path("number").asInt(0));
        if (!StringUtils.hasText(repository) || number <= 0) {
            return ResponseEntity.accepted().body(new WebhookResult("ignored: no linked pull request identity", null));
        }

        if ("pull_request_review".equals(event) && "submitted".equals(payload.path("action").asText())) {
            JsonNode review = payload.path("review");
            return ResponseEntity.accepted().body(new WebhookResult("review",
                    feedback.review(repository, number, review.path("state").asText(), review.path("body").asText(""), review.path("user").path("login").asText("unknown"))));
        }
        if ("pull_request_review_comment".equals(event) && "created".equals(payload.path("action").asText())) {
            JsonNode comment = payload.path("comment");
            return ResponseEntity.accepted().body(new WebhookResult("review_comment",
                    feedback.comment(repository, number, comment.path("body").asText(""), comment.path("user").path("login").asText("unknown"))));
        }
        if ("issue_comment".equals(event) && "created".equals(payload.path("action").asText()) && !payload.path("issue").path("pull_request").isMissingNode()) {
            JsonNode comment = payload.path("comment");
            return ResponseEntity.accepted().body(new WebhookResult("pr_comment",
                    feedback.comment(repository, number, comment.path("body").asText(""), comment.path("user").path("login").asText("unknown"))));
        }
        return ResponseEntity.accepted().body(new WebhookResult("ignored event " + event, null));
    }

    private void verifySignature(String rawBody, String signature) throws Exception {
        if (!StringUtils.hasText(webhookSecret)) return;
        if (!StringUtils.hasText(signature) || !signature.startsWith("sha256=")) throw new SecurityException("Missing GitHub webhook signature");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII))) {
            throw new SecurityException("Invalid GitHub webhook signature");
        }
    }

    public record WebhookResult(String event, Object result) {}
}
