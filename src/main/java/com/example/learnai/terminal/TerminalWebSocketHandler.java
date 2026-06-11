package com.example.learnai.terminal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TerminalWebSocketHandler.class);
    private Process process;
    private BufferedWriter writer;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // 通过 Spring 注入（静态获取）
    private static TerminalSessionService sessionService;

    // 录制
    private TerminalSession terminalSession;
    private BufferedWriter recordWriter;
    private Path recordFile;

    public static void setSessionService(TerminalSessionService svc) {
        sessionService = svc;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("终端连接: {}", session.getId());

        // 从URL参数获取用户信息
        String userId = getParam(session, "userId");
        String username = getParam(session, "username");
        if (username == null) username = userId;

        // 初始化录制
        if (sessionService != null && userId != null) {
            terminalSession = new TerminalSession(userId, username);
            try {
                Files.createDirectories(Path.of("/home/ubuntu/learn-ai/terminal-logs"));
                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                recordFile = Path.of("/home/ubuntu/learn-ai/terminal-logs/" + ts + "_" + sanitize(username) + ".log");
                recordWriter = new BufferedWriter(new FileWriter(recordFile.toFile()));
                terminalSession.setRawFile(recordFile.toString());
            } catch (Exception e) {
                log.error("创建录制文件失败", e);
            }
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "script", "-qfc", "/home/ubuntu/.local/bin/hermes", "/dev/null"
            );
            pb.redirectErrorStream(true);
            pb.directory(new File(System.getProperty("user.home")));
            process = pb.start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            executor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    char[] buf = new char[8192];
                    int n;
                    while ((n = reader.read(buf)) != -1) {
                        String chunk = new String(buf, 0, n);
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(chunk));
                        }
                        // 写入录制文件
                        if (recordWriter != null) {
                            try { recordWriter.write(chunk); recordWriter.flush(); }
                            catch (Exception ignored) {}
                        }
                    }
                } catch (Exception e) {
                    log.debug("读取进程输出结束: {}", e.getMessage());
                }
            });

            executor.submit(() -> {
                try {
                    int exit = process.waitFor();
                    log.info("终端进程退出, exit={}", exit);
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage("\r\n[进程退出, code=" + exit + "]\r\n"));
                        session.close();
                    }
                } catch (Exception e) {
                    log.error("等待进程退出异常", e);
                }
            });

        } catch (Exception e) {
            log.error("启动终端失败", e);
            try { session.close(); } catch (Exception ex) {}
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            if (writer != null) {
                String payload = message.getPayload();
                writer.write(payload);
                writer.flush();
                // 也录制用户输入
                if (recordWriter != null) {
                    try { recordWriter.write(payload); recordWriter.flush(); }
                    catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            log.error("写入终端失败", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("终端断开: {} status={}", session.getId(), status);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
        // 关闭录制，异步解析存ES
        if (recordWriter != null) {
            try { recordWriter.close(); } catch (Exception ignored) {}
        }
        if (terminalSession != null && sessionService != null && recordFile != null) {
            executor.submit(() -> {
                try {
                    sessionService.parseAndUpdate(terminalSession);
                    log.info("终端会话已存档: {} ({}行)", recordFile.getFileName(), terminalSession.getLines());
                } catch (Exception e) {
                    log.error("存档失败", e);
                }
            });
        }
    }

    private String getParam(WebSocketSession session, String key) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query == null) return null;
        for (String p : query.split("&")) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) return kv[1];
        }
        return null;
    }

    private String sanitize(String s) {
        return s == null ? "anon" : s.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]", "_");
    }
}
