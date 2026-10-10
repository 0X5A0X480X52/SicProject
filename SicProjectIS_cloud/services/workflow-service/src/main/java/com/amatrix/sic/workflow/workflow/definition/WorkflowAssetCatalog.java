package com.amatrix.sic.workflow.workflow.definition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

/** Release asset access shared by the offline seed exporter and online definition use cases. */
public final class WorkflowAssetCatalog {
    private WorkflowAssetCatalog() {}
    private static final String BASE_PATH = "workflows/bpmn_auxiliary_tags_augmented/";
    private static final List<String> ASSET_NAMES = List.of(
            "项目申请_辅助标签版.bpmn",
            "纵向项目合同_辅助标签版.bpmn",
            "项目结题_辅助标签版.bpmn");

    public static List<String> names() { return ASSET_NAMES; }
    public static String basePath() { return BASE_PATH; }
    public static String read(String assetName) {
        try {
            ClassPathResource resource = new ClassPathResource(BASE_PATH + assetName);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read workflow asset: " + assetName, ex);
        }
    }

}
