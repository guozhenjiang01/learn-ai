package com.example.learnai.media;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.example.learnai.agent.ChatModelFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MediaScriptService {

    private static final String INDEX = "media_scripts";
    private final ElasticsearchClient client;

    @Autowired
    private ChatModelFactory chatModelFactory;

    public MediaScriptService(ElasticsearchClient client) {
        this.client = client;
    }

    private void ensureIndex() throws IOException {
        boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(INDEX))).value();
        if (!exists) {
            client.indices().create(c -> c.index(INDEX)
                .mappings(m -> m.properties("userId", p -> p.keyword(k -> k))));
        }
    }

    /** 创建脚本 */
    public MediaScript create(MediaScript script) throws IOException {
        ensureIndex();
        script.setId(UUID.randomUUID().toString());
        long now = System.currentTimeMillis();
        script.setCreatedAt(now);
        script.setUpdatedAt(now);
        if (script.getStatus() == null) script.setStatus("draft");
        client.index(i -> i.index(INDEX).id(script.getId())
                .document(script).refresh(Refresh.True));
        return script;
    }

    /** 更新脚本 */
    public MediaScript update(MediaScript script) throws IOException {
        script.setUpdatedAt(System.currentTimeMillis());
        client.update(u -> u.index(INDEX).id(script.getId())
                .doc(script).refresh(Refresh.True), MediaScript.class);
        return script;
    }

    /** 删除脚本 */
    public void delete(String id) throws IOException {
        client.delete(d -> d.index(INDEX).id(id).refresh(Refresh.True));
    }

    /** 查询单条 */
    public MediaScript getById(String id) throws IOException {
        var resp = client.get(g -> g.index(INDEX).id(id), MediaScript.class);
        if (resp.found() && resp.source() != null) {
            MediaScript s = resp.source();
            s.setId(resp.id());
            return s;
        }
        return null;
    }

    /** 列表：按用户筛选，支持按 topic/status 过滤 */
    public List<MediaScript> list(String userId, String topic, String status, int size) throws IOException {
        ensureIndex();
        var bool = new ArrayList<co.elastic.clients.elasticsearch._types.query_dsl.Query>();
        if (userId != null && !userId.isEmpty()) {
            bool.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(
                q -> q.term(t -> t.field("userId").value(userId))));
        }
        if (topic != null && !topic.isEmpty() && !"all".equals(topic)) {
            bool.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(
                q -> q.term(t -> t.field("topic").value(topic))));
        }
        if (status != null && !status.isEmpty() && !"all".equals(status)) {
            bool.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(
                q -> q.term(t -> t.field("status").value(status))));
        }

        SearchResponse<MediaScript> resp = client.search(s -> {
            s.index(INDEX).size(size)
             .sort(sort -> sort.field(f -> f.field("createdAt").order(SortOrder.Desc)));
            if (!bool.isEmpty()) {
                s.query(q -> q.bool(b -> b.must(bool)));
            } else {
                s.query(q -> q.matchAll(ma -> ma));
            }
            return s;
        }, MediaScript.class);

        List<MediaScript> list = new ArrayList<>();
        for (Hit<MediaScript> hit : resp.hits().hits()) {
            MediaScript m = hit.source();
            if (m != null) { m.setId(hit.id()); list.add(m); }
        }
        return list;
    }

    /** 一键生成通勤素材包 — 根据勾选的 topics 动态生成 */
    public String generateCommutePack(List<String> topics, String extra, String weather) {
        String weatherInfo = (weather != null && !weather.isEmpty())
            ? "本周天气参考：" + weather + "\n" : "";
        String extraInfo = (extra != null && !extra.isEmpty())
            ? "额外要求：" + extra + "\n" : "";

        // 默认全选
        if (topics == null || topics.isEmpty()) {
            topics = List.of("周五回太原", "周末日常", "周一返京");
        }

        StringBuilder topicDesc = new StringBuilder();
        for (String t : topics) {
            switch (t) {
                case "周五回太原" -> topicDesc.append("""
                    【周五晚·回太原】
                    场景：亦庄收工→北京丰台站→G551高铁→太原站→孩子出站口→到家热饭
                    情绪：疲惫中的期待
                    
                    """);
                case "周末日常" -> topicDesc.append("""
                    【周末·太原日常】
                    场景：带娃/菜市场/公园/老婆（北大博士）/收拾行李准备返京
                    情绪：温馨日常+即将分别的小情绪
                    
                    """);
                case "周一返京" -> topicDesc.append("""
                    【周一早·返京通勤】
                    场景：早起→太原站→高铁→亦庄京东→工位开机→周一恐惧
                    情绪：牛马上钟的无奈
                    
                    """);
            }
        }

        String prompt = """
            你是抖音短视频脚本创作专家。账号人设：
            厚道哥，京东P7程序员，年薪百万，北京亦庄工作/太原家住，
            每周五晚G551高铁回太原，周末超级奶爸，老婆北大博士，周一早返京。
            内容风格：真实接地气，不露脸(POV+手部+空镜+字幕+配音)。

            %s%s
            请生成以下%d条15-30秒抖音脚本：

            %s
            ═══ 格式要求（严格遵守） ═══
            每条脚本用分隔线「━━━」隔开，格式：
            ━━━
            【标题】吸睛标题
            【时长】秒数
            【画面】
            0-3秒：xxx
            3-8秒：xxx
            8-15秒：xxx
            15-30秒：xxx
            【字幕】
            （与分镜一一对应）
            【配音】旁白全文
            【BGM】建议风格
            【钩子】前3秒核心钩子
            【固定结尾】我是厚道哥，周内大厂牛马，周末带娃模范
            ━━━
            
            不露脸！POV视角+手部出镜+场景空镜+字幕配音。
            """.formatted(weatherInfo, extraInfo, topics.size(), topicDesc.toString());

        try {
            return chatModelFactory.getChatClient("deepSeekV4ProChatClient")
                .prompt().user(prompt).call().content();
        } catch (Exception e) {
            return "⚠ AI 生成失败: " + e.getMessage() + "\n请稍后重试";
        }
    }

    /** 根据评审意见修改脚本 */
    public String revisePack(String previousContent, String review) {
        String prompt = """
            你是抖音短视频脚本创作专家。以下是一组已生成的脚本，用户给了评审意见。
            请根据意见修改脚本，保持原有格式和结构。

            ═══ 原始脚本 ═══
            %s

            ═══ 评审意见 ═══
            %s

            请输出修改后的完整脚本，保持与原始相同的格式（━━━分隔、分镜结构等）。
            只输出修改后的脚本，不要加任何解释。
            """.formatted(previousContent, review);

        try {
            return chatModelFactory.getChatClient("deepSeekV4ProChatClient")
                .prompt().user(prompt).call().content();
        } catch (Exception e) {
            return "⚠ AI 修改失败: " + e.getMessage() + "\n请稍后重试";
        }
    }

    /** AI 生成单条脚本（保留） */
    public MediaScript generate(String topic, String templateLabel, String extraHint,
                                 String userId, String username) throws IOException {
        String prompt = buildGeneratePrompt(topic, templateLabel, extraHint);
        String generated;
        try {
            generated = chatModelFactory.getChatClient("deepSeekV4ProChatClient")
                .prompt().user(prompt).call().content();
        } catch (Exception e) {
            generated = "⚠ AI 生成失败: " + e.getMessage() + "\n请稍后重试";
        }

        String title = firstLine(generated, 30);
        MediaScript script = new MediaScript();
        script.setUserId(userId);
        script.setUsername(username);
        script.setTitle(title.isEmpty() ? topic : title);
        script.setTopic(topic);
        script.setTemplate(templateLabel);
        script.setContent(generated);
        script.setPlatform("抖音");
        script.setStatus("draft");
        return create(script);
    }

    /** 构建生成 prompt */
    private String buildGeneratePrompt(String topic, String templateLabel, String extra) {
        String extraStr = (extra != null && !extra.isEmpty()) ? "额外要求：" + extra + "\n" : "";
        return """
            你是抖音短视频脚本创作专家。账号人设：
            厚道哥，京东P7程序员，年薪百万，工作北京亦庄/家住太原，每周五高铁G551回太原，
            周末超级奶爸，老婆是北大博士。内容风格：真实、接地气、情绪共鸣。

            请根据以下要求生成一条15-30秒的抖音短视频脚本：
            选题：%s
            模板：%s
            %s
            脚本格式要求（严格遵守）：
            【标题】一行吸睛标题
            【时长】预估秒数
            【画面】分镜描述：0-3秒/3-8秒/8-15秒/15-30秒（每段独立一行，格式"秒数区间：画面描述"）
            【字幕】对应分镜的字幕文字（与分镜一一对应）
            【配音】旁白或配音文字
            【BGM】建议背景音乐风格
            【钩子】前3秒的核心钩子
            【结尾】固定：我是厚道哥，周内大厂牛马，周末带娃模范
            
            务必不露脸，用POV视角、手部出镜、场景空镜、字幕+配音。
            """.formatted(topic, templateLabel, extraStr);
    }

    private static String firstLine(String text, int maxLen) {
        if (text == null || text.isEmpty()) return "";
        String line = text.split("\\n")[0].replaceAll("[#*【】\\[\\]\"']", "").trim();
        return line.length() <= maxLen ? line : line.substring(0, maxLen);
    }
}
