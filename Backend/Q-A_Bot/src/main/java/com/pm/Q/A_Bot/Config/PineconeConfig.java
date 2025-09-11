package com.pm.Q.A_Bot.Config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pinecone.PineconeVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;

import java.time.Duration;

@Configuration
public class PineconeConfig {

    @Value("${spring.ai.vectorstore.pinecone.api-key}")
    private String apiKey;

    @Value("${spring.ai.vectorstore.pinecone.index-name}")
    private String indexName;

    @Value("${spring.ai.vectorstore.pinecone.environment}")
    private String environment;

    @Value("${llm.service.timeout.connect:10}")
    private int connectTimeout;

    @Value("${llm.service.timeout.read:60}")
    private int readTimeout;

    @Value("${llm.service.timeout.socket:30}")
    private int socketTimeout;

    private final EmbeddingModel embeddingModel;

    public PineconeConfig(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Enhanced RestTemplate with connection pooling and proper timeout handling
     */
    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Configure connection pooling
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(50); // Max total connections
        connectionManager.setDefaultMaxPerRoute(20); // Max connections per route

        // Configure request timeouts
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(connectTimeout))
                .setResponseTimeout(Timeout.ofSeconds(readTimeout))
                .build();

        // Build HTTP client with connection pooling and timeouts
        HttpClient httpClient = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        // Create request factory with the configured HTTP client
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

        return builder
                .requestFactory(() -> requestFactory)
                .setConnectTimeout(Duration.ofSeconds(connectTimeout))
                .setReadTimeout(Duration.ofSeconds(readTimeout))
                .build();
    }

    @Bean
    @Primary
    public VectorStore vectorStore() {
        return PineconeVectorStore.builder(embeddingModel)
                .apiKey(apiKey)
                .indexName(indexName)
                .build();
    }
}