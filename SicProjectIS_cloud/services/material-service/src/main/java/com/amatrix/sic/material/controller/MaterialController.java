package com.amatrix.sic.material.controller;

import com.amatrix.sic.material.service.MaterialApplicationService;

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

/**
 * 材料版本 multipart 上传、项目材料列表、版本流式下载和删除 API；下载用 UTF-8 Content-Disposition 文件名。
 */
@RestController
public class MaterialController{
    private final MaterialApplicationService service;public MaterialController(MaterialApplicationService service){this.service=service;}
    /**
     * {@code POST /api/projects/{projectId}/materials/{materialTypeCode}/versions}：向项目的指定材料类型上传一个新文件版本。
     * <p>要求项目 MATERIAL_WRITE 权限；multipart/form-data 的 file 字段是非空文件，materialTypeCode 是材料类型代码。
     * <p>先写对象存储，再事务保存版本、SHA-256 与事件；数据库失败时尝试清理刚上传的对象。
     * <p>返回 version、context，包含 materialVersionId、materialId、versionNo 和下载地址；后续流转必须引用版本 ID。
     */
    @PostMapping(value="/api/projects/{projectId}/materials/{materialTypeCode}/versions",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<Map<String,Object>> upload(@AuthenticationPrincipal Jwt jwt,@PathVariable Long projectId,@PathVariable String materialTypeCode,@RequestParam("file")MultipartFile file){return ApiResponse.ok(service.upload(ActorContextFactory.from(jwt),projectId,materialTypeCode,file));}
    /**
     * {@code GET /api/projects/{projectId}/materials}：查询项目材料及其版本上下文。
     * <p>要求项目 MATERIAL_READ 权限；projectId 为项目主键，返回材料版本列表、当前版本标志及下载地址。
     * <p>响应仅包含元数据，文件内容通过版本 download 接口另行获取。
     */
    @GetMapping("/api/projects/{projectId}/materials") ApiResponse<List<Map<String,Object>>> list(@AuthenticationPrincipal Jwt jwt,@PathVariable Long projectId){return ApiResponse.ok(service.list(ActorContextFactory.from(jwt),projectId));}
    /**
     * {@code GET /api/material-versions/{id}/download}：下载某个材料版本对应的原始文件。
     * <p>id 为 materialVersionId，服务先按版本定位项目，再检查当前 MATERIAL_READ 权限；历史下载地址不会永久赋权。
     * <p>返回二进制 Resource，设置长度、内容类型及 UTF-8 Content-Disposition 文件名，不使用 ApiResponse JSON 包装。
     * <p>未知内容类型回退为 application/octet-stream；版本不存在返回 404。
     */
    @GetMapping("/api/material-versions/{id}/download") ResponseEntity<Resource> download(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id){var file=service.download(ActorContextFactory.from(jwt),id);MediaType type;try{type=file.contentType()==null?MediaType.APPLICATION_OCTET_STREAM:MediaType.parseMediaType(file.contentType());}catch(Exception ex){type=MediaType.APPLICATION_OCTET_STREAM;}return ResponseEntity.ok().contentLength(file.size()).contentType(type).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+UriUtils.encode(file.filename(),StandardCharsets.UTF_8)).body(file.resource());}
    /**
     * {@code DELETE /api/material-versions/{id}}：删除可删除的材料版本，并登记对象存储清理任务。
     * <p>id 为具体版本 ID，要求项目 MATERIAL_DELETE 权限；锁内检查生成单据、材料预留及引用保护。
     * <p>工作流生成单据或被预留/确认的版本拒绝删除（409）；允许删除时，数据库删除与清理任务同事务持久化。
     * <p>返回 data=null；对象由后续清理任务删除，成功响应不表示 MinIO 对象已即时消失。
     */
    @DeleteMapping("/api/material-versions/{id}") ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id){service.delete(ActorContextFactory.from(jwt),id);return ApiResponse.ok(null);}
}
