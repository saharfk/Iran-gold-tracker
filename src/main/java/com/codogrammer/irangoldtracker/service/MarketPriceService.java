package com.codogrammer.irangoldtracker.service;

import com.codogrammer.irangoldtracker.dto.MarketResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class MarketPriceService {

    private final RestClient restClient;

    @Value("${brs.api.key}")
    private String apiKey;

    public MarketPriceService(RestClient restClient) {
        this.restClient = restClient;
    }

    public MarketResponse getMarketPrices() {

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/Market/Gold_Currency.php")
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(MarketResponse.class);
    }
}
