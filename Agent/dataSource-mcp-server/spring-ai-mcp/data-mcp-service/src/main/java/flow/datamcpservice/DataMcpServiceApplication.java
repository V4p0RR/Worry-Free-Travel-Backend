package flow.datamcpservice;

import flow.datamcpservice.service.DataService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DataMcpServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataMcpServiceApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider toolCallbackProvider(DataService dataService) {
        return MethodToolCallbackProvider.builder().toolObjects(dataService).build();
    }

}