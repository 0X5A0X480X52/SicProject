package com.amatrix.sic.workflow;

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

@RestController
@RequestMapping("/api/node-forms")
public class NodeFormController {
    private final NodeFormService service;public NodeFormController(NodeFormService service){this.service=service;}
    @GetMapping("/definitions") ApiResponse<List<Map<String,Object>>> definitions(){return ApiResponse.ok(service.definitions());}
    @GetMapping("/{formCode}") ApiResponse<Map<String,Object>> get(@PathVariable String formCode,@RequestParam(required=false)Long projectId,@RequestParam(required=false)Long moduleInstanceId,@RequestParam(required=false)Long stateRecordId){return ApiResponse.ok(service.get(formCode,projectId,moduleInstanceId,stateRecordId));}
    @PutMapping("/{formCode}") ApiResponse<Map<String,Object>> save(@AuthenticationPrincipal Jwt jwt,@PathVariable String formCode,@RequestBody Map<String,Object> request){Long module=id(request,"moduleInstanceId");return ApiResponse.ok(service.save(module,formCode,request,actor(jwt)));}
    @PostMapping("/{formCode}/records") ApiResponse<Map<String,Object>> create(@AuthenticationPrincipal Jwt jwt,@PathVariable String formCode,@RequestBody Map<String,Object> request){Long module=id(request,"moduleInstanceId");return ApiResponse.ok(service.createRecord(module,formCode,request,actor(jwt)));}
    @PutMapping("/{formCode}/records/{recordId}") ApiResponse<Map<String,Object>> update(@AuthenticationPrincipal Jwt jwt,@PathVariable String formCode,@PathVariable Long recordId,@RequestBody Map<String,Object> request){Long module=id(request,"moduleInstanceId");return ApiResponse.ok(service.updateRecord(module,formCode,recordId,request,actor(jwt)));}
    @DeleteMapping("/{formCode}/records/{recordId}") ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt,@PathVariable String formCode,@PathVariable Long recordId,@RequestParam Long moduleInstanceId){service.deleteRecord(moduleInstanceId,formCode,recordId,ActorContextFactory.from(jwt));return ApiResponse.ok(null);}
    private static com.amatrix.sic.contracts.ActorContext actor(Jwt jwt){return ActorContextFactory.from(jwt);}private static Long id(Map<String,Object>r,String k){Object v=r.get(k);return v instanceof Number n?n.longValue():Long.valueOf(String.valueOf(v));}
}
