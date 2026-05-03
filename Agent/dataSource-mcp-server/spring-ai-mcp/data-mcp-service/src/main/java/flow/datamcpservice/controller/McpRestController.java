package flow.datamcpservice.controller;

import flow.datamcpservice.service.DataService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST wrapper for MCP tools, so Python agent can call via plain HTTP.
 * Mirrors the @Tool methods in DataService as REST endpoints.
 */
@RestController
@RequestMapping("/api/mcp")
public class McpRestController {

    private final DataService dataService;

    public McpRestController(DataService dataService) {
        this.dataService = dataService;
    }

    /**
     * 数据源导航器 —— 列出所有可用数据源
     */
    @GetMapping("/datasources")
    public String listDataSources() {
        return dataService.getAvailableDataSources();
    }

    /**
     * 数据聚合器 —— 按ID集合获取数据
     */
    @PostMapping("/fetch")
    public String fetchData(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Integer> sourceIds = (List<Integer>) request.get("sourceIds");

        @SuppressWarnings("unchecked")
        Map<String, Object> rawParams = (Map<String, Object>) request.getOrDefault("params", new HashMap<>());

        // Convert string keys to Integer keys
        HashMap<Integer, HashMap<String, Object>> params = new HashMap<>();
        if (rawParams != null) {
            for (Map.Entry<String, Object> entry : rawParams.entrySet()) {
                try {
                    Integer sourceId = Integer.parseInt(entry.getKey());
                    @SuppressWarnings("unchecked")
                    HashMap<String, Object> sourceParams = (HashMap<String, Object>) entry.getValue();
                    params.put(sourceId, sourceParams);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return dataService.getDataBySourceIDs(sourceIds, params);
    }
}
