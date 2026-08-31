package com.ordevia.aidev.api;

import com.ordevia.aidev.governance.application.ApprovalService;
import com.ordevia.aidev.governance.domain.ApprovalRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {
    private final ApprovalService approvals;
    public ApprovalController(ApprovalService approvals){this.approvals=approvals;}

    @GetMapping("/pending") public List<ApprovalRequest> pending(){return approvals.pending();}
    @GetMapping("/{id}") public ApprovalRequest get(@PathVariable UUID id){return approvals.get(id);}
    @GetMapping("/work-item/{workItemId}") public List<ApprovalRequest> byWorkItem(@PathVariable UUID workItemId){return approvals.byWorkItem(workItemId);}
    @PostMapping("/{id}/approve") public ApprovalRequest approve(@PathVariable UUID id,@Valid @RequestBody DecisionRequest request){return approvals.approve(id,request.decidedBy(),request.note());}
    @PostMapping("/{id}/reject") public ApprovalRequest reject(@PathVariable UUID id,@Valid @RequestBody DecisionRequest request){return approvals.reject(id,request.decidedBy(),request.note());}

    public record DecisionRequest(@NotBlank String decidedBy,String note){}
}
