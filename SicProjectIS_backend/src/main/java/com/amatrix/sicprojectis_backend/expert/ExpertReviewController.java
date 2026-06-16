package com.amatrix.sicprojectis_backend.expert;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.amatrix.sicprojectis_backend.common.ApiResponse;
import com.amatrix.sicprojectis_backend.expert.dto.AssignExpertRequest;
import com.amatrix.sicprojectis_backend.expert.dto.CreateExpertReviewBatchRequest;
import com.amatrix.sicprojectis_backend.expert.dto.ExpertReviewBatchDetailResponse;
import com.amatrix.sicprojectis_backend.expert.dto.SubmitExpertScoreRequest;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/expert-reviews")
public class ExpertReviewController {
    private final ExpertReviewService service; public ExpertReviewController(ExpertReviewService service){this.service=service;}
    @PostMapping("/batches") public ApiResponse<ExpertReviewBatchDetailResponse> create(@AuthenticationPrincipal AuthenticatedUser user,@RequestBody CreateExpertReviewBatchRequest request){return ApiResponse.ok(service.create(user,request));}
    @GetMapping("/batches/{batchId}") public ApiResponse<ExpertReviewBatchDetailResponse> detail(@PathVariable Long batchId){return ApiResponse.ok(service.detail(batchId));}
    @PostMapping("/batches/{batchId}/assignments") public ApiResponse<ExpertReviewBatchDetailResponse> assign(@PathVariable Long batchId,@RequestBody AssignExpertRequest request){return ApiResponse.ok(service.assign(batchId,request));}
    @PostMapping("/assignments/{assignmentId}/scores") public ApiResponse<ExpertReviewBatchDetailResponse> submit(@PathVariable Long assignmentId,@RequestBody SubmitExpertScoreRequest request){return ApiResponse.ok(service.submit(assignmentId,request));}
}
