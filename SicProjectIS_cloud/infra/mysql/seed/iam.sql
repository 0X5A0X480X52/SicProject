-- Release seed; credentials are configured through IAM bootstrap.
SET NAMES utf8mb4;
INSERT INTO department(id, name, code) VALUES (1, '系统管理部门', 'SYSTEM');

INSERT INTO app_role(code, name, description, built_in) VALUES
 ('SYSTEM_ADMIN', '系统管理员', '系统全局管理', TRUE),
 ('SCIENCE_ADMIN', '科技处管理员', '科研统筹与流程审核', TRUE),
 ('DEPT_ADMIN', '二级单位管理员', '部门项目管理与审核', TRUE),
 ('PROJECT_LEADER', '项目负责人', '项目申请与执行', TRUE),
 ('EXPERT', '评审专家', '项目专家评审', TRUE),
 ('FINANCE_ADMIN', '财务人员', '财务审核', TRUE);

INSERT INTO app_permission(code, name) VALUES
 ('auth:login', '用户登录'), ('auth:me', '当前用户'), ('admin:overview', '管理概览'),
 ('user:manage', '用户管理'), ('role:manage', '角色管理'), ('permission:manage', '权限管理'),
 ('audit:read', '审计读取'), ('department:member:manage', '部门成员管理'),
 ('expert:qualification:apply', '专家资格申请'),
 ('expert:qualification:review:dept', '部门专家资格审核'),
 ('expert:qualification:review:science', '科技处专家资格审核'),
 ('project:view', '项目查看'), ('project:grant:view', '项目授权查看'),
 ('project:grant:leader', '项目负责人授权'), ('project:grant:member', '项目成员授权'),
 ('project:grant:expert', '项目专家授权'), ('project:grant:finance', '项目财务授权'),
 ('project:grant:proxy', '项目代录授权'), ('project:grant:revoke', '项目授权撤销'),
 ('workflow:definition:view', '流程定义查看'), ('workflow:definition:upload', '流程定义上传'),
 ('workflow:definition:validate', '流程定义校验'), ('workflow:definition:publish', '流程定义发布');

INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM app_role r CROSS JOIN app_permission p WHERE r.code = 'SYSTEM_ADMIN';

INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM app_role r CROSS JOIN app_permission p
 WHERE r.code = 'SCIENCE_ADMIN' AND p.code IN ('admin:overview','audit:read','project:view','project:grant:view',
 'project:grant:leader','project:grant:member','project:grant:expert','project:grant:finance',
 'project:grant:proxy','project:grant:revoke','expert:qualification:review:science',
 'department:member:manage','workflow:definition:view','workflow:definition:upload',
 'workflow:definition:validate','workflow:definition:publish');

INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM app_role r CROSS JOIN app_permission p
 WHERE r.code = 'DEPT_ADMIN' AND p.code IN ('project:view','project:grant:view','project:grant:member',
 'expert:qualification:review:dept','department:member:manage');

INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM app_role r CROSS JOIN app_permission p
 WHERE r.code IN ('PROJECT_LEADER','FINANCE_ADMIN') AND p.code = 'project:view';

INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM app_role r CROSS JOIN app_permission p
 WHERE r.code = 'EXPERT' AND p.code = 'workflow:definition:view';
