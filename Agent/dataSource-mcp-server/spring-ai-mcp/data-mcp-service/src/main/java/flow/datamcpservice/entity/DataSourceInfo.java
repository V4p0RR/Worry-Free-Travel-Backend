package flow.datamcpservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataSourceInfo {
    private Integer id;
    private String description;
    private String url;
    private String options;
    private HashMap<String, DateRangeInfo> params;
}