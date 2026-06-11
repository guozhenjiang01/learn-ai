package com.example.learnai.terminal;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.example.learnai.agent.ChatModelFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TerminalSessionService {

    private static final String INDEX = "terminal_sessions";
    private final ElasticsearchClient client;

    @Autowired
    private ChatModelFactory chatModelFactory;

    public TerminalSessionService(ElasticsearchClient client) {
        this.client = client;
    }

    private void ensureIndex() throws IOException {
        boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(INDEX))).value();
        if (!exists) {
            client.indices().create(c -> c.index(INDEX));
        }
    }

    public TerminalSession save(TerminalSession session) {
        try {
            ensureIndex();
            if (session.getId() == null) session.setId(UUID.randomUUID().toString());
            client.index(i -> i.index(INDEX).id(session.getId()).document(session));
        } catch (Exception e) {
            System.err.println("[Terminal] 保存失败: " + e.getMessage());
        }
        return session;
    }

    /** 解析 raw 文件 → 去 ANSI → 更新 ES */
    public void parseAndUpdate(TerminalSession session) {
        try {
            if (session.getRawFile() == null) return;
            String raw = Files.readString(Path.of(session.getRawFile()));
            String clean = stripAnsi(raw);
            // 截取前5000行/50万字符
            if (clean.length() > 500000) clean = clean.substring(0, 500000);
            String[] lines = clean.split("\n");
            session.setContent(clean);
            session.setLines(lines.length);
            session.setEndedAt(System.currentTimeMillis());
            // 生成标题：取第一句有意义的话，截10字
            session.setTitle(generateTitle(clean));
            save(session);
        } catch (Exception e) {
            System.err.println("[Terminal] 解析失败: " + e.getMessage());
        }
    }

    /** 去除 ANSI 转义序列 */
    static String stripAnsi(String text) {
        if (text == null) return "";
        // Remove OSC sequences (title, etc.)
        text = text.replaceAll("\u001b\\][^\u0007]*\u0007", "");
        text = text.replaceAll("\u001b\\][^\u001b]*\u001b\\\\", "");
        // Remove CSI sequences: ESC [ ... m (colors) and other SGR
        text = text.replaceAll("\u001b\\[[0-9;]*[a-zA-Z]", "");
        // Remove other ESC sequences
        text = text.replaceAll("\u001b[>=]", "");
        // Remove standalone ESC (leftovers)
        text = text.replaceAll("\u001b", "");
        // Remove carriage returns
        text = text.replaceAll("\r", "");
        // Collapse multiple blank lines
        text = text.replaceAll("\\n{3,}", "\\n\\n");
        return filterNoise(text);
    }

    /** 过滤中间噪声：思考过程、进度条、空行等 */
    private static String filterNoise(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder out = new StringBuilder();
        String[] lines = text.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            // 跳过思考/加载指示器
            if (trimmed.matches("^[⏳⌛⏲].*")) continue;
            if (trimmed.matches("^[⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏].*")) continue;
            if (trimmed.matches("^(thinking|思考|Loading|处理中).*")) continue;
            if (trimmed.equals("thinking...") || trimmed.equals("⏳") || trimmed.equals("...")) continue;
            // 跳过纯符号/进度条
            if (trimmed.matches("^[=#\\-━▁▂▃▄▅▆▇█]{5,}$")) continue;
            if (trimmed.matches("^\\d+%$")) continue;
            // 跳过 prompt 空行
            if (trimmed.matches("^[➤>]\\s*$")) continue;
            out.append(trimmed).append("\n");
        }
        return out.toString().trim();
    }

    /** 用AI总结内容生成20字以内标题 */
    private String generateTitle(String content) {
        if (content == null || content.isBlank()) return "空会话";
        try {
            String snippet = content.length() > 800 ? content.substring(0, 800) : content;
            String prompt = "用20字以内总结以下AI助手对话的核心话题，尽量短，能说清就行。只输出总结不要解释：\n" + snippet;
            String result = chatModelFactory.getChatClient("deepSeekV4ProChatClient")
                .prompt().user(prompt).call().content();
            if (result == null || result.isBlank()) return "会话记录";
            result = result.replaceAll("[\"'\"\\s]+", "").trim();
            if (result.length() > 20) result = result.substring(0, 20);
            return result.isEmpty() ? "会话记录" : result;
        } catch (Exception e) {
            System.err.println("[Terminal] 标题生成失败，使用回退: " + e.getMessage());
            return fallbackTitle(content);
        }
    }

    /** AI不可用时的回退标题 */
    private static String fallbackTitle(String content) {
        for (String line : content.split("\n")) {
            String t = line.trim();
            if (t.length() >= 3 && !t.matches("^[=#\\-━▁▂▃▄▅▆▇█*·•●○◎◉◎]+$")
                && !t.startsWith("✓") && !t.startsWith("✗") && !t.startsWith("📋")) {
                return t.length() <= 20 ? t : t.substring(0, 20);
            }
        }
        return "会话记录";
    }

    /** 查询历史会话 */
    public List<TerminalSession> list(String userId, Long fromTs, Long toTs, int size) throws IOException {
        ensureIndex();
        SearchResponse<TerminalSession> resp = client.search(s -> {
            s.index(INDEX).size(size)
             .sort(sort -> sort.field(f -> f.field("startedAt").order(SortOrder.Desc)));

            var bool = new ArrayList<co.elastic.clients.elasticsearch._types.query_dsl.Query>();
            if (userId != null && !userId.isEmpty()) {
                bool.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(
                    q -> q.term(t -> t.field("userId").value(userId))));
            }
            if (fromTs != null && toTs != null) {
                bool.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(
                    q -> q.range(r -> r.field("startedAt")
                        .gte(co.elastic.clients.json.JsonData.of(fromTs))
                        .lte(co.elastic.clients.json.JsonData.of(toTs)))));
            }

            if (!bool.isEmpty()) {
                s.query(q -> q.bool(b -> b.must(bool)));
            } else {
                s.query(q -> q.matchAll(ma -> ma));
            }
            return s;
        }, TerminalSession.class);

        List<TerminalSession> list = new ArrayList<>();
        for (Hit<TerminalSession> hit : resp.hits().hits()) {
            TerminalSession s = hit.source();
            if (s != null) {
                s.setId(hit.id());
                boolean needsUpdate = false;
                // 修复旧记录：内容里存的是字面 \n 而非真换行
                if (s.getContent() != null && s.getContent().contains("\\n") && !s.getContent().contains("\n")) {
                    s.setContent(s.getContent().replace("\\n", "\n"));
                    needsUpdate = true;
                }
                // 旧记录没标题，生成并回存
                if ((s.getTitle() == null || s.getTitle().isBlank()) && s.getContent() != null) {
                    s.setTitle(generateTitle(s.getContent()));
                    needsUpdate = true;
                }
                if (needsUpdate) {
                    final TerminalSession fs = s;
                    new Thread(() -> {
                        try {
                            client.update(u -> u.index(INDEX).id(fs.getId())
                                .doc(fs), TerminalSession.class);
                        } catch (Exception ignored) {}
                    }).start();
                }
                list.add(s);
            }
        }
        return list;
    }

    public TerminalSession getById(String id) throws IOException {
        var resp = client.get(g -> g.index(INDEX).id(id), TerminalSession.class);
        if (resp.found() && resp.source() != null) {
            TerminalSession s = resp.source();
            s.setId(resp.id());
            return s;
        }
        return null;
    }
}
