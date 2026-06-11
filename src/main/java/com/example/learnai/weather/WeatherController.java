package com.example.learnai.weather;

import com.example.learnai.audit.OperationLog;
import com.example.learnai.audit.OperationLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
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
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final OperationLogService audit;

    public WeatherController(OperationLogService audit) {
        this.audit = audit;
    }

    // 北京亦庄 39.79,116.50  榆次 37.70,112.74
    private static final String[][] CITIES = {
        {"北京", "Beijing"},
        {"榆次", "Yuci"}
    };

    // wttr.in weather code → WMO code
    private static final Map<Integer, Integer> WWO_TO_WMO = Map.ofEntries(
        Map.entry(113, 0),   // Clear/Sunny
        Map.entry(116, 1),   // Partly Cloudy
        Map.entry(119, 2),   // Cloudy
        Map.entry(122, 3),   // Overcast
        Map.entry(143, 45),  // Mist
        Map.entry(176, 51),  // Light rain
        Map.entry(179, 71),  // Patchy snow
        Map.entry(182, 71),  // Patchy sleet
        Map.entry(185, 71),  // Freezing drizzle
        Map.entry(200, 95),  // Thundery outbreaks
        Map.entry(227, 71),  // Blowing snow
        Map.entry(230, 71),  // Blizzard
        Map.entry(248, 45),  // Fog
        Map.entry(260, 45),  // Freezing fog
        Map.entry(263, 51),  // Patchy light drizzle
        Map.entry(266, 51),  // Light drizzle
        Map.entry(281, 61),  // Freezing drizzle → 61 rain
        Map.entry(284, 61),  // Heavy freezing drizzle
        Map.entry(293, 51),  // Patchy light rain
        Map.entry(296, 51),  // Light rain
        Map.entry(299, 61),  // Moderate rain
        Map.entry(302, 61),  // Moderate rain
        Map.entry(305, 61),  // Heavy rain
        Map.entry(308, 61),  // Heavy rain
        Map.entry(311, 51),  // Light freezing rain
        Map.entry(314, 61),  // Moderate freezing rain
        Map.entry(317, 61),  // Light sleet
        Map.entry(320, 61),  // Moderate sleet
        Map.entry(323, 71),  // Light snow
        Map.entry(326, 71),  // Moderate snow
        Map.entry(329, 71),  // Heavy snow
        Map.entry(332, 71),  // Heavy snow
        Map.entry(335, 71),  // Heavy snow
        Map.entry(338, 71),  // Heavy snow
        Map.entry(350, 71),  // Ice pellets
        Map.entry(353, 51),  // Light rain shower
        Map.entry(356, 61),  // Moderate rain shower
        Map.entry(359, 61),  // Heavy rain shower
        Map.entry(362, 71),  // Light sleet shower
        Map.entry(365, 71),  // Moderate sleet shower
        Map.entry(368, 71),  // Light snow shower
        Map.entry(371, 71),  // Moderate snow shower
        Map.entry(374, 71),  // Ice pellets shower
        Map.entry(377, 71),  // Ice pellets shower
        Map.entry(386, 95),  // Thundery outbreaks
        Map.entry(389, 95),  // Thunder with rain
        Map.entry(392, 95),  // Thunder with snow
        Map.entry(395, 95)   // Thunder with snow
    );

    @GetMapping
    public String weather(HttpServletRequest req) throws IOException, InterruptedException {
        // 记录查看天气（非阻塞）
        String username = (String) req.getAttribute("username");
        String userId = (String) req.getAttribute("userId");
        if (username != null) {
            try { audit.log(new OperationLog(userId, username, "VIEW_WEATHER", "查看天气", clientIp(req))); }
            catch (Exception ignored) {}
        }

        ArrayNode result = MAPPER.createArrayNode();
        for (String[] city : CITIES) {
            String url = "https://wttr.in/" + city[1] + "?format=j1";
            HttpRequest httpReq = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> resp = HTTP.send(httpReq, HttpResponse.BodyHandlers.ofString());
            JsonNode wttr = MAPPER.readTree(resp.body());

            ObjectNode data = MAPPER.createObjectNode();
            // current
            JsonNode cc = wttr.get("current_condition").get(0);
            ObjectNode current = MAPPER.createObjectNode();
            int wwoCode = cc.get("weatherCode").asInt();
            int wmoCode = WWO_TO_WMO.getOrDefault(wwoCode, 0);
            current.put("weather_code", wmoCode);
            current.put("temperature_2m", cc.get("temp_C").asDouble());
            double feelsLike = cc.has("FeelsLikeC") ? cc.get("FeelsLikeC").asDouble()
                : cc.get("temp_C").asDouble();
            current.put("apparent_temperature", feelsLike);
            current.put("relative_humidity_2m", cc.get("humidity").asInt());
            current.put("wind_speed_10m", cc.get("windspeedKmph").asDouble());
            data.set("current", current);

            // daily (3 days)
            JsonNode weather = wttr.get("weather");
            ArrayNode times = MAPPER.createArrayNode();
            ArrayNode maxTemps = MAPPER.createArrayNode();
            ArrayNode minTemps = MAPPER.createArrayNode();
            ArrayNode wmoCodes = MAPPER.createArrayNode();
            int days = Math.min(3, weather.size());
            for (int d = 0; d < days; d++) {
                JsonNode day = weather.get(d);
                times.add(day.get("date").asText());
                maxTemps.add(day.get("maxtempC").asDouble());
                minTemps.add(day.get("mintempC").asDouble());
                // daily weather code: use hourly code at noon, or first available
                int dailyWwo = 113;
                JsonNode hourly = day.get("hourly");
                if (hourly != null && hourly.size() > 4) {
                    dailyWwo = hourly.get(4).get("weatherCode").asInt();
                }
                wmoCodes.add(WWO_TO_WMO.getOrDefault(dailyWwo, 0));
            }
            ObjectNode daily = MAPPER.createObjectNode();
            daily.set("time", times);
            daily.set("temperature_2m_max", maxTemps);
            daily.set("temperature_2m_min", minTemps);
            daily.set("weather_code", wmoCodes);
            data.set("daily", daily);

            ObjectNode cityObj = MAPPER.createObjectNode();
            cityObj.put("name", city[0]);
            cityObj.set("data", data);
            result.add(cityObj);
        }
        return MAPPER.writeValueAsString(result);
    }

    private String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        return ip != null ? ip.split(",")[0].trim() : req.getRemoteAddr();
    }
}
