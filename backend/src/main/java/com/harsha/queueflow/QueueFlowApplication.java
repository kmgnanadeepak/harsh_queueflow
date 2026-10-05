package com.harsha.queueflow;

import com.harsha.queueflow.config.QueueFlowProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(QueueFlowProperties.class)
@EnableJpaAuditing
@EnableScheduling
@EnableAsync
public class QueueFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(QueueFlowApplication.class, args);
    }
}
