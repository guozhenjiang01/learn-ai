package com.example.learnai.weather;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@RestController
@RequestMapping("/weather")
public class WeatherController {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    // 北京亦庄 39.79,116.50  榆次 37.70,112.74
    private static final double[][] CITIES = {
        {39.79, 116.50}, // 北京
        {37.70, 112.74}  // 榆次
    };
    private static final String[] NAMES = {"北京", "榆次"};

    @GetMapping
    public String weather() throws IOException, InterruptedException {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < CITIES.length; i++) {
            String url = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code&daily=temperature_2m_max,temperature_2m_min,weather_code&forecast_days=3&timezone=Asia%%2FShanghai",
                CITIES[i][0], CITIES[i][1]);
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(NAMES[i]).append("\",\"data\":").append(resp.body()).append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}
