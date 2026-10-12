package com.amatrix.sic.workflow.controller;

import com.amatrix.sic.workflow.service.form.NodeFormService;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.amatrix.sic.contracts.ApiResponse;
import com.amatrix.sic.security.ActorContextFactory;

/**
 * 节点表单定义、读取、单例保存、历史记录新增/修改/删除 API，从请求提取模块和用户上下文。
 */
@RestController
@RequestMapping("/api/node-forms")
public class NodeFormController {
    private final NodeFormService service;public NodeFormController(NodeFormService service){this.service=service;}
    /**
     * {@code GET /api/node-forms/definitions}：列出节点表单注册定义，供前端选择表单组件和写入方式。
     * <p>返回 formCode、模块/节点/状态、dataKind、writeMode 等元数据；SINGLE_INSTANCE、HISTORY_RECORD、READ_ONLY 区分保存行为。
     * <p>接口处于需登录的 /api 范围，读取表单目录不表示获得具体项目的表单读写权限。
     */
    @GetMapping("/definitions") ApiResponse<List<Map<String,Object>>> definitions(){return ApiResponse.ok(service.definitions());}
    /**
     * {@code GET /api/node-forms/{formCode}}：按表单代码与项目/模块上下文读取节点表单数据。
     * <p>formCode 为注册表单代码；projectId、moduleInstanceId、stateRecordId 为可选上下文，由 CanReadNodeForm 策略检查可读范围。
     * <p>返回 definition、上下文及按 dataKind 组织的数据；历史型表单读取记录列表，单例表单读取保存数据或兼容业务草稿。
     * <p>stateRecordId 是状态记录 ID，不是表单记录 ID，也不是模块当前 seq；本接口只读取，不补写业务记录。
     */
    @GetMapping("/{formCode}") ApiResponse<Map<String,Object>> get(@PathVariable String formCode,@RequestParam(required=false)Long projectId,@RequestParam(required=false)Long moduleInstanceId,@RequestParam(required=false)Long stateRecordId){return ApiResponse.ok(service.get(formCode,projectId,moduleInstanceId,stateRecordId));}
    /**
     * {@code PUT /api/node-forms/{formCode}}：保存当前节点的单例表单草稿及对应业务草稿。
     * <p>formCode 必须为 SINGLE_INSTANCE；JSON 必须提供 moduleInstanceId，并按 dataKind 提供 applicationDraft、contractDraft 等业务结构。
     * <p>服务锁内检查节点、状态、模块归属和当前操作资格，不允许写入 READ_ONLY 或历史记录型表单。
     * <p>返回保存后的表单响应；此保存不会推进流程，正式提交仍调用 transitions。
     */
    @PutMapping("/{formCode}") ApiResponse<Map<String,Object>> save(@AuthenticationPrincipal Jwt jwt,@PathVariable String formCode,@RequestBody Map<String,Object> request){Long module=id(request,"moduleInstanceId");return ApiResponse.ok(service.save(module,formCode,request,actor(jwt)));}
    /**
     * {@code POST /api/node-forms/{formCode}/records}：为当前节点的历史记录型表单新增一条记录。
     * <p>formCode 必须为 HISTORY_RECORD；JSON 必须包含 moduleInstanceId 及表单对应业务结构，可附 stateRecordId。
     * <p>检查当前节点与操作资格，使用独立记录键保存本轮数据；返回 definition、新 recordId 和表单数据。
     * <p>新增记录本身不触发流程迁移，也不能替代正式专家评分接口。
     */
    @PostMapping("/{formCode}/records") ApiResponse<Map<String,Object>> create(@AuthenticationPrincipal Jwt jwt,@PathVariable String formCode,@RequestBody Map<String,Object> request){Long module=id(request,"moduleInstanceId");return ApiResponse.ok(service.createRecord(module,formCode,request,actor(jwt)));}
    /**
     * {@code PUT /api/node-forms/{formCode}/records/{recordId}}：修改当前节点、本轮次的一条历史表单记录。
     * <p>recordId 为表单记录主键；JSON moduleInstanceId 与 formCode 一起界定记录归属和可修改范围。
     * <p>要求 HISTORY_RECORD 及当前任务操作资格；记录不匹配时返回 404，节点已变化时返回 409。
     * <p>返回更新后的 definition、recordId 和表单数据，不更新其他轮次记录，也不推进流程。
     */
    @PutMapping("/{formCode}/records/{recordId}") ApiResponse<Map<String,Object>> update(@AuthenticationPrincipal Jwt jwt,@PathVariable String formCode,@PathVariable Long recordId,@RequestBody Map<String,Object> request){Long module=id(request,"moduleInstanceId");return ApiResponse.ok(service.updateRecord(module,formCode,recordId,request,actor(jwt)));}
    /**
     * {@code DELETE /api/node-forms/{formCode}/records/{recordId}}：删除当前节点、本轮次中匹配的历史表单记录。
     * <p>查询参数 moduleInstanceId 必填；路径 formCode、recordId 与当前模块/轮次共同限定删除范围。
     * <p>要求 HISTORY_RECORD 和当前操作资格，节点或状态不匹配会被拒绝；成功返回 data=null。
     * <p>此接口不删除状态迁移记录或业务材料，未匹配记录不在 Controller 中额外转换为 404。
     */
    @DeleteMapping("/{formCode}/records/{recordId}") ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt,@PathVariable String formCode,@PathVariable Long recordId,@RequestParam Long moduleInstanceId){service.deleteRecord(moduleInstanceId,formCode,recordId,ActorContextFactory.from(jwt));return ApiResponse.ok(null);}
    private static com.amatrix.sic.contracts.ActorContext actor(Jwt jwt){return ActorContextFactory.from(jwt);}private static Long id(Map<String,Object>r,String k){Object v=r.get(k);return v instanceof Number n?n.longValue():Long.valueOf(String.valueOf(v));}
}
