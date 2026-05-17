package com.stockquery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockquery.config.SupabaseConfig;
import com.stockquery.model.Analysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupabaseService {

    private final SupabaseConfig supabaseConfig;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public void saveAnalysis(Analysis analysis) {
        try {
            String json = objectMapper.writeValueAsString(analysis);
            log.info("Saving analysis to Supabase: {}", json);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(supabaseConfig.getUrl() + "/rest/v1/analyses"))
                    .header("apikey", supabaseConfig.getKey())
                    .header("Authorization", "Bearer " + supabaseConfig.getKey())
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=representation")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Save analysis response: {}, body: {}", response.statusCode(), response.body());
        } catch (Exception e) {
            log.error("Failed to save analysis to Supabase", e);
        }
    }

    public List<Analysis> getAllAnalyses(String clientIp) {
        try {
            String url = supabaseConfig.getUrl() + "/rest/v1/analyses?client_ip=eq." + clientIp + "&order=created_at.desc&limit=100";
            log.info("getAllAnalyses url: {}", url);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", supabaseConfig.getKey())
                    .header("Authorization", "Bearer " + supabaseConfig.getKey())
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("getAllAnalyses response status: {}, body length: {}", response.statusCode(), response.body().length());
            JsonNode jsonNode = objectMapper.readTree(response.body());
            List<Analysis> analyses = new ArrayList<>();

            if (jsonNode.isArray()) {
                for (JsonNode node : jsonNode) {
                    analyses.add(objectMapper.readValue(node.toString(), Analysis.class));
                }
            }
            log.info("getAllAnalyses found {} records", analyses.size());
            return analyses;
        } catch (Exception e) {
            log.error("Failed to fetch all analyses from Supabase", e);
            return List.of();
        }
    }

    public List<Analysis> getAnalyses(String stockCode, String clientIp) {
        try {
            String url = supabaseConfig.getUrl() + "/rest/v1/analyses?stock_code=eq." + stockCode + "&client_ip=eq." + clientIp + "&order=created_at.desc";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", supabaseConfig.getKey())
                    .header("Authorization", "Bearer " + supabaseConfig.getKey())
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode jsonNode = objectMapper.readTree(response.body());
            List<Analysis> analyses = new ArrayList<>();

            if (jsonNode.isArray()) {
                for (JsonNode node : jsonNode) {
                    Analysis analysis = objectMapper.readValue(node.toString(), Analysis.class);
                    analyses.add(analysis);
                }
            }
            return analyses;
        } catch (Exception e) {
            log.error("Failed to fetch analyses from Supabase", e);
            return List.of();
        }
    }
}
