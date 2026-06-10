package com.example.learnai.terminal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TerminalWebSocketHandler.class);
    private Process process;
    private BufferedWriter writer;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("终端连接: {}", session.getId());
        try {
            // 使用 script 命令获得 PTY，直接启动 hermes
            ProcessBuilder pb = new ProcessBuilder(
                "script", "-qfc", "/home/ubuntu/.local/bin/hermes", "/dev/null"
            );
            pb.redirectErrorStream(true);
            pb.directory(new File(System.getProperty("user.home")));
            process = pb.start();

            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            // 读取 bash 输出，转发给 WebSocket
            executor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    char[] buf = new char[8192];
                    int n;
                    while ((n = reader.read(buf)) != -1) {
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(new String(buf, 0, n)));
                        }
                    }
                } catch (Exception e) {
                    log.debug("读取进程输出结束: {}", e.getMessage());
                }
            });

            // 监控进程退出
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
                writer.write(message.getPayload());
                writer.flush();
            }
        } catch (Exception e) {
            log.error("写入终端失败", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("终端断开: {}", session.getId());
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }
}
