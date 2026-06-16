package com.amatrix.sicprojectis_backend.structured;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sicprojectis_backend.common.ApiResponse;
import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.structured.dto.AcceptanceDraftRequest;
import com.amatrix.sicprojectis_backend.structured.dto.AcceptanceDraftResponse;
import com.amatrix.sicprojectis_backend.structured.dto.ApplicationDraftRequest;
import com.amatrix.sicprojectis_backend.structured.dto.ApplicationDraftResponse;
import com.amatrix.sicprojectis_backend.structured.dto.ContractDraftRequest;
import com.amatrix.sicprojectis_backend.structured.dto.ContractDraftResponse;
import com.amatrix.sicprojectis_backend.structured.dto.FinancialSettlementRequest;
import com.amatrix.sicprojectis_backend.structured.dto.AchievementRequest;
import com.amatrix.sicprojectis_backend.structured.entity.AcceptanceFinancialSettlement;
import com.amatrix.sicprojectis_backend.structured.entity.ProjectAchievement;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class StructuredBusinessController {
    private final StructuredBusinessService service;
    public StructuredBusinessController(StructuredBusinessService service) { this.service = service; }

    @GetMapping("/applications")
    public ApiResponse<ApplicationDraftResponse> getApplication(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long projectId) { return ApiResponse.ok(service.getApplication(user, projectId)); }
    @PutMapping("/applications")
    public ApiResponse<ApplicationDraftResponse> saveApplication(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long projectId, @RequestBody ApplicationDraftRequest request) { return ApiResponse.ok(service.saveApplication(user, projectId, request)); }
    @GetMapping("/contracts")
    public ApiResponse<ContractDraftResponse> getContract(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long projectId) { return ApiResponse.ok(service.getContract(user, projectId)); }
    @PutMapping("/contracts")
    public ApiResponse<ContractDraftResponse> saveContract(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long projectId, @RequestBody ContractDraftRequest request) { return ApiResponse.ok(service.saveContract(user, projectId, request)); }
    @GetMapping("/acceptances")
    public ApiResponse<AcceptanceDraftResponse> getAcceptance(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long projectId) { return ApiResponse.ok(service.getAcceptance(user, projectId)); }
    @PutMapping("/acceptances")
    public ApiResponse<AcceptanceDraftResponse> saveAcceptance(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long projectId, @RequestBody AcceptanceDraftRequest request) { return ApiResponse.ok(service.saveAcceptance(user, projectId, request)); }
    @GetMapping("/achievements") public ApiResponse<List<ProjectAchievement>> achievements(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long projectId){return ApiResponse.ok(service.getAchievements(user,projectId));}
    @PostMapping("/achievements") public ApiResponse<ProjectAchievement> addAchievement(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long projectId,@RequestBody AchievementRequest request){return ApiResponse.ok(service.addAchievement(user,projectId,request));}
    @GetMapping("/acceptance-financial-settlements") public ApiResponse<List<AcceptanceFinancialSettlement>> settlements(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long projectId){return ApiResponse.ok(service.getSettlements(user,projectId));}
    @PostMapping("/acceptance-financial-settlements") public ApiResponse<AcceptanceFinancialSettlement> addSettlement(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long projectId,@RequestBody FinancialSettlementRequest request){return ApiResponse.ok(service.addSettlement(user,projectId,request));}
}
