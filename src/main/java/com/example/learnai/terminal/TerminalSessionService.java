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

    /** 解析 raw 文件 → pyte 渲染 → 更新 ES */
    public void parseAndUpdate(TerminalSession session) {
        try {
            if (session.getRawFile() == null) return;
            String clean = renderWithPyte(session.getRawFile());
            // 截取前50万字符
            if (clean.length() > 500000) clean = clean.substring(0, 500000);
            String[] lines = clean.split("\n");
            session.setContent(clean);
            session.setLines(lines.length);
            session.setEndedAt(System.currentTimeMillis());
            session.setTitle(generateTitle(clean));
            save(session);
        } catch (Exception e) {
            System.err.println("[Terminal] 解析失败: " + e.getMessage());
        }
    }

    /** 调用 pyte 虚拟终端渲染 raw 文件为干净文本 */
    private static String renderWithPyte(String filePath) {
        try {
            String scriptDir = System.getProperty("user.dir") + "/scripts";
            ProcessBuilder pb = new ProcessBuilder(
                "python3", scriptDir + "/terminal-render.py", filePath);
            pb.redirectErrorStream(false);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
            String err = new String(p.getErrorStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                System.err.println("[Terminal] pyte 渲染失败 (exit=" + exitCode + "): " + err);
                // 回退：读原始文件做简单去 ANSI
                return fallbackStripAnsi(Files.readString(Path.of(filePath)));
            }
            return out.trim();
        } catch (Exception e) {
            System.err.println("[Terminal] pyte 调用异常: " + e.getMessage());
            try {
                return fallbackStripAnsi(Files.readString(Path.of(filePath)));
            } catch (Exception ex) {
                return "";
            }
        }
    }

    /** 回退方案：简单去 ANSI（pyte 不可用时） */
    private static String fallbackStripAnsi(String text) {
        if (text == null) return "";
        text = text.replaceAll("\u001b\\[[0-9;?>]*[a-zA-Z]", "");
        text = text.replaceAll("\u001b\\][^\u0007]*\u0007", "");
        text = text.replaceAll("\u001b\\][^\u001b]*\u001b\\\\", "");
        text = text.replaceAll("\u001b", "");
        text = text.replaceAll("\u0000", "");
        text = text.replaceAll("\\r", "\n");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text;
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
