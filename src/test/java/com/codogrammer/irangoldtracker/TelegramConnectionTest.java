package com.codogrammer.irangoldtracker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TelegramConnectionTest {

    public static void main(String[] args) throws Exception {

        var client = HttpClient.newHttpClient();

        var request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.telegram.org"))
                .GET()
                .build();

        var response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        System.out.println(response.statusCode());
        System.out.println(response.body());
    }
}
