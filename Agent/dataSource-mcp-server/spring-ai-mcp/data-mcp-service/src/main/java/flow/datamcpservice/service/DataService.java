package flow.datamcpservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import flow.datamcpservice.entity.DataSourceInfo;
import flow.datamcpservice.entity.DateRangeInfo;
import lombok.extern.slf4j.Slf4j;
import pool.AdaptiveBufferedThreadPoolExecutor;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DataService {

    private final Map<Integer, DataSourceInfo> dataSources = new LinkedHashMap<>();

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private AdaptiveBufferedThreadPoolExecutor threadPoolExecutor;

    public DataService() {
        loadDataSourcesFromJson();
    }

    private void loadDataSourcesFromJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("config/data-sources.json");
            if (inputStream == null) {
                log.error("未找到 data-sources.json，跳过数据源初始化");
                return;
            }
            List<DataSourceInfo> list = mapper.readValue(inputStream, new TypeReference<>() {
            });
            for (DataSourceInfo info : list) {
                dataSources.put(info.getId(), info);
            }
        } catch (Exception e) {
            log.error("加载数据源配置失败：{}", e.getMessage(), e);
        }
    }

    @Tool(description = "列出所有可用数据源及其介绍")
    public String getAvailableDataSources() {
        return dataSources.values().stream()
                .map(info -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("- ID: ").append(info.getId()).append("\n");
                    sb.append("  描述: ").append(info.getDescription()).append("\n");
                    sb.append("  URL: ").append(info.getUrl()).append("\n");
                    if (info.getParams() != null && !info.getParams().isEmpty()) {
                        sb.append("  参数:\n");
                        for (Map.Entry<String, DateRangeInfo> entry : info.getParams().entrySet()) {
                            sb.append("    - ").append(entry.getKey())
                                    .append("（").append(entry.getValue().getType())
                                    .append("）：").append(entry.getValue().getDescription()).append("\n");
                        }
                    }
                    return sb.toString();
                })
                .collect(Collectors.joining("\n", "当前可用数据源：\n", ""));
    }

    @Tool(description = "通过数据源唯一ID集合获取相应数据。\n" +
            "参数说明：\n" +
            "- sourceIDs：需要请求的数据源ID列表；\n" +
            "- inputParams：每个数据源对应的参数集合，结构为 Map<数据源ID, 参数键值对>，" +
            "用于为不同数据源分别设置请求参数。")
    public String getDataBySourceIDs(List<Integer> sourceIDs, HashMap<Integer, HashMap<String, Object>> inputParams) {
        log.info("获取数据源：{}", sourceIDs);

        // 存储任务结果
        List<Future<String>> futures = new ArrayList<>();

        for (Integer id : sourceIDs) {
            Future<String> future = threadPoolExecutor.submit(() -> {
                DataSourceInfo info = dataSources.get(id);
                StringBuilder singleResult = new StringBuilder();

                if (info == null) {
                    singleResult.append("数据源ID ").append(id).append(" 未找到\n");
                    return singleResult.toString();
                }

                try {
                    // 构建 URL
                    UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(info.getUrl());

                    // 获取当前数据源的参数
                    HashMap<String, Object> paramsForThisSource = inputParams.getOrDefault(id, new HashMap<>());

                    // 添加匹配参数
                    if (info.getParams() != null) {
                        for (String key : info.getParams().keySet()) {
                            if (paramsForThisSource.containsKey(key)) {
                                builder.queryParam(key, paramsForThisSource.get(key));
                            }
                        }
                    }

                    String finalUrl = builder.toUriString();
                    log.info("请求数据源 [{}]: {}", id, finalUrl);

                    String response = restTemplate.getForObject(finalUrl, String.class);
                    singleResult.append("数据源 ID: ").append(id).append(" 请求成功\n");
                    singleResult.append("返回内容:\n").append(response).append("\n\n");
                    log.info("请求数据源 [{}] 成功，返回内容：{}", id, response);
                } catch (Exception e) {
                    singleResult.append("数据源 ID: ").append(id)
                            .append(" 请求失败，错误信息: ").append(e.getMessage()).append("\n\n");
                    log.error("请求数据源 [{}] 异常：{}", id, e.getMessage(), e);
                }

                return singleResult.toString();
            });

            futures.add(future);
        }

        // 等待所有任务完成并合并结果
        StringBuilder result = new StringBuilder();
        for (Future<String> future : futures) {
            try {
                result.append(future.get());
            } catch (Exception e) {
                log.error("获取任务结果失败：{}", e.getMessage(), e);
                result.append("获取任务结果失败：").append(e.getMessage()).append("\n\n");
            }
        }

        log.info("获取数据源结果：\n{}", result.toString());
        return result.toString();
    }

}