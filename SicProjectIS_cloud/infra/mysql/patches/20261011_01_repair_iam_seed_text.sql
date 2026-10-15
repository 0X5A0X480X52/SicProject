-- Reference: fc8c604 IAM V2 seed; original labels unchanged in the release seed.
-- Apply only to sic_iam after backup. Exact corrupt byte patterns only; custom labels remain.
-- Does not change IDs, codes, users, role assignments or role permissions. Safe to repeat.
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE department SET name=CONVERT(0xe7b3bbe7bb9fe7aea1e79086e983a8e997a8 USING utf8mb4)
 WHERE code='SYSTEM' AND id=1 AND BINARY name=BINARY CONVERT(CONVERT(0xe7b3bbe7bb9fe7aea1e79086e983a8e997a8 USING latin1) USING utf8mb4);

UPDATE app_role SET name=CONVERT(0xe7b3bbe7bb9fe7aea1e79086e59198 USING utf8mb4)
 WHERE code='SYSTEM_ADMIN' AND BINARY name=BINARY CONVERT(CONVERT(0xe7b3bbe7bb9fe7aea1e79086e59198 USING latin1) USING utf8mb4);

UPDATE app_role SET description=CONVERT(0xe7b3bbe7bb9fe585a8e5b180e7aea1e79086 USING utf8mb4)
 WHERE code='SYSTEM_ADMIN' AND BINARY description=BINARY CONVERT(CONVERT(0xe7b3bbe7bb9fe585a8e5b180e7aea1e79086 USING latin1) USING utf8mb4);

UPDATE app_role SET name=CONVERT(0xe7a791e68a80e5a484e7aea1e79086e59198 USING utf8mb4)
 WHERE code='SCIENCE_ADMIN' AND BINARY name=BINARY CONVERT(CONVERT(0xe7a791e68a80e5a484e7aea1e79086e59198 USING latin1) USING utf8mb4);

UPDATE app_role SET description=CONVERT(0xe7a791e7a094e7bb9fe7adb9e4b88ee6b581e7a88be5aea1e6a0b8 USING utf8mb4)
 WHERE code='SCIENCE_ADMIN' AND BINARY description=BINARY CONVERT(CONVERT(0xe7a791e7a094e7bb9fe7adb9e4b88ee6b581e7a88be5aea1e6a0b8 USING latin1) USING utf8mb4);

UPDATE app_role SET name=CONVERT(0xe4ba8ce7baa7e58d95e4bd8de7aea1e79086e59198 USING utf8mb4)
 WHERE code='DEPT_ADMIN' AND BINARY name=BINARY CONVERT(CONVERT(0xe4ba8ce7baa7e58d95e4bd8de7aea1e79086e59198 USING latin1) USING utf8mb4);

UPDATE app_role SET description=CONVERT(0xe983a8e997a8e9a1b9e79baee7aea1e79086e4b88ee5aea1e6a0b8 USING utf8mb4)
 WHERE code='DEPT_ADMIN' AND BINARY description=BINARY CONVERT(CONVERT(0xe983a8e997a8e9a1b9e79baee7aea1e79086e4b88ee5aea1e6a0b8 USING latin1) USING utf8mb4);

UPDATE app_role SET name=CONVERT(0xe9a1b9e79baee8b49fe8b4a3e4baba USING utf8mb4)
 WHERE code='PROJECT_LEADER' AND BINARY name=BINARY CONVERT(CONVERT(0xe9a1b9e79baee8b49fe8b4a3e4baba USING latin1) USING utf8mb4);

UPDATE app_role SET description=CONVERT(0xe9a1b9e79baee794b3e8afb7e4b88ee689a7e8a18c USING utf8mb4)
 WHERE code='PROJECT_LEADER' AND BINARY description=BINARY CONVERT(CONVERT(0xe9a1b9e79baee794b3e8afb7e4b88ee689a7e8a18c USING latin1) USING utf8mb4);

UPDATE app_role SET name=CONVERT(0xe8af84e5aea1e4b893e5aeb6 USING utf8mb4)
 WHERE code='EXPERT' AND BINARY name=BINARY CONVERT(CONVERT(0xe8af84e5aea1e4b893e5aeb6 USING latin1) USING utf8mb4);

UPDATE app_role SET description=CONVERT(0xe9a1b9e79baee4b893e5aeb6e8af84e5aea1 USING utf8mb4)
 WHERE code='EXPERT' AND BINARY description=BINARY CONVERT(CONVERT(0xe9a1b9e79baee4b893e5aeb6e8af84e5aea1 USING latin1) USING utf8mb4);

UPDATE app_role SET name=CONVERT(0xe8b4a2e58aa1e4babae59198 USING utf8mb4)
 WHERE code='FINANCE_ADMIN' AND BINARY name=BINARY CONVERT(CONVERT(0xe8b4a2e58aa1e4babae59198 USING latin1) USING utf8mb4);

UPDATE app_role SET description=CONVERT(0xe8b4a2e58aa1e5aea1e6a0b8 USING utf8mb4)
 WHERE code='FINANCE_ADMIN' AND BINARY description=BINARY CONVERT(CONVERT(0xe8b4a2e58aa1e5aea1e6a0b8 USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe794a8e688b7e799bbe5bd95 USING utf8mb4)
 WHERE code='auth:login' AND BINARY name=BINARY CONVERT(CONVERT(0xe794a8e688b7e799bbe5bd95 USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe5bd93e5898de794a8e688b7 USING utf8mb4)
 WHERE code='auth:me' AND BINARY name=BINARY CONVERT(CONVERT(0xe5bd93e5898de794a8e688b7 USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe7aea1e79086e6a682e8a788 USING utf8mb4)
 WHERE code='admin:overview' AND BINARY name=BINARY CONVERT(CONVERT(0xe7aea1e79086e6a682e8a788 USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe794a8e688b7e7aea1e79086 USING utf8mb4)
 WHERE code='user:manage' AND BINARY name=BINARY CONVERT(CONVERT(0xe794a8e688b7e7aea1e79086 USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe8a792e889b2e7aea1e79086 USING utf8mb4)
 WHERE code='role:manage' AND BINARY name=BINARY CONVERT(CONVERT(0xe8a792e889b2e7aea1e79086 USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe69d83e99990e7aea1e79086 USING utf8mb4)
 WHERE code='permission:manage' AND BINARY name=BINARY CONVERT(CONVERT(0xe69d83e99990e7aea1e79086 USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe5aea1e8aea1e8afbbe58f96 USING utf8mb4)
 WHERE code='audit:read' AND BINARY name=BINARY CONVERT(CONVERT(0xe5aea1e8aea1e8afbbe58f96 USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe983a8e997a8e68890e59198e7aea1e79086 USING utf8mb4)
 WHERE code='department:member:manage' AND BINARY name=BINARY CONVERT(CONVERT(0xe983a8e997a8e68890e59198e7aea1e79086 USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe4b893e5aeb6e8b584e6a0bce794b3e8afb7 USING utf8mb4)
 WHERE code='expert:qualification:apply' AND BINARY name=BINARY CONVERT(CONVERT(0xe4b893e5aeb6e8b584e6a0bce794b3e8afb7 USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe983a8e997a8e4b893e5aeb6e8b584e6a0bce5aea1e6a0b8 USING utf8mb4)
 WHERE code='expert:qualification:review:dept' AND BINARY name=BINARY CONVERT(CONVERT(0xe983a8e997a8e4b893e5aeb6e8b584e6a0bce5aea1e6a0b8 USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe7a791e68a80e5a484e4b893e5aeb6e8b584e6a0bce5aea1e6a0b8 USING utf8mb4)
 WHERE code='expert:qualification:review:science' AND BINARY name=BINARY CONVERT(CONVERT(0xe7a791e68a80e5a484e4b893e5aeb6e8b584e6a0bce5aea1e6a0b8 USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe9a1b9e79baee69fa5e79c8b USING utf8mb4)
 WHERE code='project:view' AND BINARY name=BINARY CONVERT(CONVERT(0xe9a1b9e79baee69fa5e79c8b USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe9a1b9e79baee68e88e69d83e69fa5e79c8b USING utf8mb4)
 WHERE code='project:grant:view' AND BINARY name=BINARY CONVERT(CONVERT(0xe9a1b9e79baee68e88e69d83e69fa5e79c8b USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe9a1b9e79baee8b49fe8b4a3e4babae68e88e69d83 USING utf8mb4)
 WHERE code='project:grant:leader' AND BINARY name=BINARY CONVERT(CONVERT(0xe9a1b9e79baee8b49fe8b4a3e4babae68e88e69d83 USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe9a1b9e79baee68890e59198e68e88e69d83 USING utf8mb4)
 WHERE code='project:grant:member' AND BINARY name=BINARY CONVERT(CONVERT(0xe9a1b9e79baee68890e59198e68e88e69d83 USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe9a1b9e79baee4b893e5aeb6e68e88e69d83 USING utf8mb4)
 WHERE code='project:grant:expert' AND BINARY name=BINARY CONVERT(CONVERT(0xe9a1b9e79baee4b893e5aeb6e68e88e69d83 USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe9a1b9e79baee8b4a2e58aa1e68e88e69d83 USING utf8mb4)
 WHERE code='project:grant:finance' AND BINARY name=BINARY CONVERT(CONVERT(0xe9a1b9e79baee8b4a2e58aa1e68e88e69d83 USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe9a1b9e79baee4bba3e5bd95e68e88e69d83 USING utf8mb4)
 WHERE code='project:grant:proxy' AND BINARY name=BINARY CONVERT(CONVERT(0xe9a1b9e79baee4bba3e5bd95e68e88e69d83 USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe9a1b9e79baee68e88e69d83e692a4e99480 USING utf8mb4)
 WHERE code='project:grant:revoke' AND BINARY name=BINARY CONVERT(CONVERT(0xe9a1b9e79baee68e88e69d83e692a4e99480 USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe6b581e7a88be5ae9ae4b989e69fa5e79c8b USING utf8mb4)
 WHERE code='workflow:definition:view' AND BINARY name=BINARY CONVERT(CONVERT(0xe6b581e7a88be5ae9ae4b989e69fa5e79c8b USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe6b581e7a88be5ae9ae4b989e4b88ae4bca0 USING utf8mb4)
 WHERE code='workflow:definition:upload' AND BINARY name=BINARY CONVERT(CONVERT(0xe6b581e7a88be5ae9ae4b989e4b88ae4bca0 USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe6b581e7a88be5ae9ae4b989e6a0a1e9aa8c USING utf8mb4)
 WHERE code='workflow:definition:validate' AND BINARY name=BINARY CONVERT(CONVERT(0xe6b581e7a88be5ae9ae4b989e6a0a1e9aa8c USING latin1) USING utf8mb4);

UPDATE app_permission SET name=CONVERT(0xe6b581e7a88be5ae9ae4b989e58f91e5b883 USING utf8mb4)
 WHERE code='workflow:definition:publish' AND BINARY name=BINARY CONVERT(CONVERT(0xe6b581e7a88be5ae9ae4b989e58f91e5b883 USING latin1) USING utf8mb4);

COMMIT;
