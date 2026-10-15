CREATE UNIQUE INDEX uk_module_instance_project_code
    ON module_instance(project_id, module_code);
