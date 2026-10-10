package com.amatrix.sic.project;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.contracts.IamOverviewStats;
import com.amatrix.sic.contracts.UserSummary;

@FeignClient(name = "iam-service", contextId = "projectIamDirectoryClient")
public interface IamDirectoryClient {
    @GetMapping("/internal/users/{userId}/summary")
    ApiResponse<UserSummary> summary(@RequestHeader("Authorization") String authorization, @PathVariable Long userId);

    @GetMapping("/internal/admin/overview-stats")
    ApiResponse<IamOverviewStats> overviewStats(@RequestHeader("Authorization") String authorization);
}
