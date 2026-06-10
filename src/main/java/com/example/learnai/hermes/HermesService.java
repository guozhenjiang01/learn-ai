package com.example.learnai.hermes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Service
public class HermesService {

    private static final Logger log = LoggerFactory.getLogger(HermesService.class);
    private static final String HERMES_BIN = "/home/ubuntu/.local/bin/hermes";
    private static final String AGENT_LOG = "/home/ubuntu/.hermes/logs/agent.log";
    private static final long TIMEOUT_SECONDS = 180;
    private static final int MAX_HISTORY_TURNS = 20;

    // 只推送有意义的日志行
    private static final Pattern INTERESTING_LOG = Pattern.compile(
        "tool (\\w+) (started|completed)|API call|Turn (started|ended)|client (created|closed)");

    private final Map<String, List<Turn>> sessions = new ConcurrentHashMap<>();

    public HermesResult chat(String userId, String username, String message) {
        long startTime = System.currentTimeMillis();
        log.info("┌─ Hermes chat 开始 ─────────────────────────────");
        log.info("│ userId  : {}", userId);
        log.info("│ username: {}", username);
        log.info("│ 入参    : {}", message);

        HermesResult result = new HermesResult();
        result.setInput(message);

        List<Turn> history = sessions.computeIfAbsent(userId, k -> new ArrayList<>());
        log.info("│ 历史轮数: {}", history.size());

        String fullPrompt = buildPrompt(history, username, message);
        ProcessBuilder pb = buildProcess(fullPrompt);

        StringBuilder output = new StringBuilder();
        int lineCount = 0;
        Process process = null;

        try {
            long procStart = System.currentTimeMillis();
            process = pb.start();
            log.info("│ 进程启动, PID: {}", process.pid());

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineCount++;
                    if (output.length() > 0) output.append("\n");
                    output.append(line);
                }
            }

            long procElapsed = System.currentTimeMillis() - procStart;
            log.info("│ 进程输出完成: {} 行, 耗时 {}ms", lineCount, procElapsed);

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                result.setSuccess(false);
                result.setOutput(output.toString());
                result.setError("执行超时");
                result.setExitCode(-1);
                return result;
            }

            int exitCode = process.exitValue();
            String reply = output.toString().trim();
            result.setOutput(reply);
            result.setExitCode(exitCode);

            if (exitCode == 0) {
                result.setSuccess(true);
                history.add(new Turn(message, reply));
                trimHistory(history);
            } else {
                result.setSuccess(false);
                result.setError("Hermes 退出码: " + exitCode);
            }
        } catch (Exception e) {
            log.error("│ ✕ 异常: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setOutput(output.toString());
            result.setError(e.getMessage());
            result.setExitCode(-1);
            if (process != null && process.isAlive()) process.destroyForcibly();
        }

        log.info("│ success={}, exitCode={}, 总耗时 {}ms", result.isSuccess(), result.getExitCode(), System.currentTimeMillis() - startTime);
        log.info("└─ Hermes chat 结束 ─────────────────────────────");
        return result;
    }

    /**
     * 流式对话：同时推送 agent.log（思考过程）和 hermes 输出。
     */
    public Flux<String> chatStream(String userId, String username, String message) {
        long startTime = System.currentTimeMillis();
        log.info("┌─ Hermes chatStream ────────────────────────────");
        log.info("│ userId  : {}", userId);
        log.info("│ username: {}", username);
        log.info("│ 入参    : {}", message);

        List<Turn> history = sessions.computeIfAbsent(userId, k -> new ArrayList<>());
        String fullPrompt = buildPrompt(history, username, message);

        return Flux.create(sink -> {
            AtomicBoolean logDone = new AtomicBoolean(false);
            AtomicBoolean procDone = new AtomicBoolean(false);
            StringBuilder replyBuilder = new StringBuilder();
            Process process = null;

            // 线程1：tail agent.log 推送思考过程（非阻塞轮询）
            Thread logTailer = new Thread(() -> {
                try {
                    Path logPath = Path.of(AGENT_LOG);
                    if (!Files.exists(logPath)) { logDone.set(true); return; }
                    long pos = Files.size(logPath);
                    byte[] buf = new byte[8192];
                    StringBuilder lineBuf = new StringBuilder();

                    while (!procDone.get()) {
                        try (RandomAccessFile raf = new RandomAccessFile(AGENT_LOG, "r")) {
                            long len = raf.length();
                            if (len > pos) {
                                raf.seek(pos);
                                int n = raf.read(buf, 0, (int) Math.min(buf.length, len - pos));
                                pos += n;
                                for (int i = 0; i < n; i++) {
                                    char c = (char) buf[i];
                                    if (c == '\n') {
                                        String line = lineBuf.toString();
                                        lineBuf.setLength(0);
                                        if (INTERESTING_LOG.matcher(line).find()) {
                                            String shortLine = line.replaceAll("^.*?(\\d{2}:\\d{2}:\\d{2})[^]]*\\] ", "[$1] ")
                                                .replaceAll("thread=.*?\\) ", "")
                                                .replaceAll("provider=\\S+ ", "")
                                                .replaceAll("base_url=\\S+ ", "")
                                                .replaceAll("model=\\S+ ", "");
                                            if (shortLine.length() > 200) shortLine = shortLine.substring(0, 200) + "...";
                                            sink.next("[思考] " + shortLine);
                                        }
                                    } else if (c != '\r') {
                                        lineBuf.append(c);
                                    }
                                }
                            }
                        }
                        Thread.sleep(300);
                    }
                } catch (Exception ignored) {
                } finally {
                    logDone.set(true);
                    checkComplete(sink, logDone, procDone, replyBuilder.toString(), history, message);
                }
            }, "hermes-log-tailer");
            logTailer.setDaemon(true);
            logTailer.start();

            // 主线程：执行 hermes
            try {
                ProcessBuilder pb = buildProcess(fullPrompt);
                process = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        replyBuilder.append(line).append("\n");
                        sink.next(line);
                    }
                }

                boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!finished) process.destroyForcibly();

            } catch (Exception e) {
                log.error("│ ✕ 流式异常: {}", e.getMessage(), e);
                sink.next("❌ " + e.getMessage());
            } finally {
                procDone.set(true);
                if (process != null && process.isAlive()) process.destroyForcibly();
                checkComplete(sink, logDone, procDone, replyBuilder.toString(), history, message);
            }

            log.info("│ 总耗时 {}ms", System.currentTimeMillis() - startTime);
            log.info("└─ Hermes chatStream 结束 ────────────────────────");
        });
    }

    private void checkComplete(reactor.core.publisher.FluxSink<String> sink,
            AtomicBoolean logDone, AtomicBoolean procDone,
            String reply, List<Turn> history, String message) {
        if (logDone.get() && procDone.get()) {
            if (!reply.isBlank()) {
                history.add(new Turn(message, reply.trim()));
                trimHistory(history);
            }
            sink.complete();
        }
    }

    private ProcessBuilder buildProcess(String fullPrompt) {
        ProcessBuilder pb = new ProcessBuilder(HERMES_BIN, "-z", fullPrompt,
                "-t", "file,terminal,web,search", "--yolo");
        pb.directory(new java.io.File("/home/ubuntu"));
        pb.redirectErrorStream(true);
        pb.environment().put("HOME", "/home/ubuntu");
        pb.environment().put("PATH", "/home/ubuntu/.local/bin:" + System.getenv("PATH"));
        pb.environment().put("USER", "ubuntu");
        return pb;
    }

    public void clearSession(String userId) {
        sessions.remove(userId);
    }

    public List<Turn> getHistory(String userId) {
        return sessions.getOrDefault(userId, List.of());
    }

    private String buildPrompt(List<Turn> history, String username, String message) {
        StringBuilder sb = new StringBuilder();
        if (!history.isEmpty()) {
            sb.append("以下是之前的对话（请记住上下文）：\n");
            for (Turn t : history) {
                sb.append("用户：").append(t.question).append("\n");
                sb.append("助手：").append(t.answer).append("\n\n");
            }
            sb.append("---\n");
        }
        if (username != null) sb.append("[来自用户 ").append(username).append(" 的消息] ");
        sb.append(message);
        return sb.toString();
    }

    private void trimHistory(List<Turn> history) {
        while (history.size() > MAX_HISTORY_TURNS) history.remove(0);
    }

    public static class Turn {
        public String question, answer;
        public Turn() {}
        public Turn(String q, String a) { this.question = q; this.answer = a; }
    }

    public static class HermesResult {
        private String input, output, error;
        private boolean success;
        private int exitCode;
        public String getInput() { return input; }
        public void setInput(String v) { input = v; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean v) { success = v; }
        public String getOutput() { return output; }
        public void setOutput(String v) { output = v; }
        public String getError() { return error; }
        public void setError(String v) { error = v; }
        public int getExitCode() { return exitCode; }
        public void setExitCode(int v) { exitCode = v; }
    }
}
