package com.llmmemory.summarization;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@Configuration // Marks this class as a source of configuration properties
@ConfigurationProperties(prefix = "openai.api") // Binds properties with the prefix "openai.api" to the fields of this class
@Data // Lombok annotation to generate getters, setters, toString, equals, and hashCode methods automatically
public class OpenAiConfig {
    private String key;
    private String model;
    private String url;

    // Registers RestTemplate as a Spring bean so it can be injected into SummarizationService
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
