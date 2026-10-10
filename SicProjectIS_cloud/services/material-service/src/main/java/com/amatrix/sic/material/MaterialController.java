package com.amatrix.sic.material;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.security.ActorContextFactory;

@RestController
public class MaterialController{
    private final MaterialApplicationService service;public MaterialController(MaterialApplicationService service){this.service=service;}
    @PostMapping(value="/api/projects/{projectId}/materials/{materialTypeCode}/versions",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<Map<String,Object>> upload(@AuthenticationPrincipal Jwt jwt,@PathVariable Long projectId,@PathVariable String materialTypeCode,@RequestParam("file")MultipartFile file){return ApiResponse.ok(service.upload(ActorContextFactory.from(jwt),projectId,materialTypeCode,file));}
    @GetMapping("/api/projects/{projectId}/materials") ApiResponse<List<Map<String,Object>>> list(@AuthenticationPrincipal Jwt jwt,@PathVariable Long projectId){return ApiResponse.ok(service.list(ActorContextFactory.from(jwt),projectId));}
    @GetMapping("/api/material-versions/{id}/download") ResponseEntity<Resource> download(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id){var file=service.download(ActorContextFactory.from(jwt),id);MediaType type;try{type=file.contentType()==null?MediaType.APPLICATION_OCTET_STREAM:MediaType.parseMediaType(file.contentType());}catch(Exception ex){type=MediaType.APPLICATION_OCTET_STREAM;}return ResponseEntity.ok().contentLength(file.size()).contentType(type).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+UriUtils.encode(file.filename(),StandardCharsets.UTF_8)).body(file.resource());}
    @DeleteMapping("/api/material-versions/{id}") ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id){service.delete(ActorContextFactory.from(jwt),id);return ApiResponse.ok(null);}
}
