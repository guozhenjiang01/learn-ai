package com.example.learnai.user;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class UserService {

    private static final String USER_INDEX = "users";
    private static final String SESSION_INDEX = "sessions";

    private final ElasticsearchClient client;

    public UserService(ElasticsearchClient client) {
        this.client = client;
    }

    private void ensureIndex(String index) throws IOException {
        boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(index))).value();
        if (!exists) {
            if (index.equals(USER_INDEX)) {
                client.indices().create(c -> c.index(index)
                    .mappings(m -> m.properties("username", p -> p.keyword(k -> k))));
            } else {
                client.indices().create(c -> c.index(index));
            }
        }
    }

    private String hash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(password.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== 注册 ====================

    public User register(String username, String password) throws IOException {
        ensureIndex(USER_INDEX);
        SearchResponse<User> existing = client.search(s -> s
                .index(USER_INDEX)
                .query(q -> q.term(t -> t.field("username").value(username))),
                User.class);
        if (!existing.hits().hits().isEmpty()) {
            throw new RuntimeException("用户名已存在");
        }
        boolean isFirst = false;
        try {
            SearchResponse<User> all = client.search(s -> s
                    .index(USER_INDEX).query(q -> q.matchAll(ma -> ma)).size(0), User.class);
            isFirst = all.hits().total() != null && all.hits().total().value() == 0;
        } catch (Exception ignored) {}

        User user = new User(username, hash(password), isFirst ? "admin" : "user");
        user.setId(UUID.randomUUID().toString());
        client.index(i -> i.index(USER_INDEX).id(user.getId()).document(user).refresh(Refresh.True));
        user.setPassword(null);
        return user;
    }

    // ==================== 登录 ====================

    public Session login(String username, String password) throws IOException {
        ensureIndex(USER_INDEX);
        ensureIndex(SESSION_INDEX);
        SearchResponse<User> resp = client.search(s -> s
                .index(USER_INDEX)
                .query(q -> q.term(t -> t.field("username").value(username))),
                User.class);
        if (resp.hits().hits().isEmpty()) {
            throw new RuntimeException("用户名或密码错误");
        }
        Hit<User> hit = resp.hits().hits().get(0);
        User user = hit.source();
        if (user == null || !user.getPassword().equals(hash(password))) {
            throw new RuntimeException("用户名或密码错误");
        }
        if (user.isDisabled()) {
            throw new RuntimeException("账号已被禁用，请联系超管");
        }
        // 更新最后登录时间
        String userId = hit.id();
        client.update(u -> u.index(USER_INDEX).id(userId)
                .doc(Map.of("lastLoginAt", System.currentTimeMillis())), User.class);

        Session session = new Session(UUID.randomUUID().toString(), userId, user.getUsername(), user.getRole());
        client.index(i -> i.index(SESSION_INDEX).id(session.getToken()).document(session).refresh(Refresh.True));
        return session;
    }

    // ==================== 会话 ====================

    public Session verify(String token) throws IOException {
        ensureIndex(SESSION_INDEX);
        try {
            SearchResponse<Session> resp = client.search(s -> s
                    .index(SESSION_INDEX)
                    .query(q -> q.term(t -> t.field("_id").value(token)))
                    .size(1),
                    Session.class);
            if (resp.hits().hits().isEmpty()) return null;
            Session session = resp.hits().hits().get(0).source();
            if (session == null || session.isExpired()) {
                try { client.delete(d -> d.index(SESSION_INDEX).id(token)); } catch (Exception ignored) {}
                return null;
            }
            return session;
        } catch (Exception e) {
            return null;
        }
    }

    public void logout(String token) throws IOException {
        try { client.delete(d -> d.index(SESSION_INDEX).id(token)); } catch (Exception ignored) {}
    }

    // ==================== 用户查询 ====================

    /** 根据 ES 文档 ID 获取用户 */
    public User getUserById(String userId) throws IOException {
        ensureIndex(USER_INDEX);
        GetResponse<User> resp = client.get(g -> g.index(USER_INDEX).id(userId), User.class);
        if (resp.found() && resp.source() != null) {
            User u = resp.source();
            u.setId(resp.id());
            u.setPassword(null);
            return u;
        }
        return null;
    }

    /** 列出所有用户（仅超管可用） */
    public List<User> listUsers() throws IOException {
        ensureIndex(USER_INDEX);
        SearchResponse<User> resp = client.search(s -> s
                .index(USER_INDEX).query(q -> q.matchAll(ma -> ma)).size(200),
                User.class);
        List<User> users = new ArrayList<>();
        for (Hit<User> hit : resp.hits().hits()) {
            User u = hit.source();
            if (u != null) {
                u.setId(hit.id());
                u.setPassword(null);
                users.add(u);
            }
        }
        // 按创建时间排序
        users.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        return users;
    }

    /** 搜索用户 */
    public List<User> searchUsers(String keyword) throws IOException {
        ensureIndex(USER_INDEX);
        SearchResponse<User> resp = client.search(s -> s
                .index(USER_INDEX)
                .query(q -> q.wildcard(w -> w.field("username").value("*" + keyword + "*")))
                .size(50),
                User.class);
        List<User> users = new ArrayList<>();
        for (Hit<User> hit : resp.hits().hits()) {
            User u = hit.source();
            if (u != null) {
                u.setId(hit.id());
                u.setPassword(null);
                users.add(u);
            }
        }
        return users;
    }

    // ==================== 权限管理（升级/降级） ====================

    /** 提升为超管 */
    public void promoteToAdmin(String userId) throws IOException {
        ensureIndex(USER_INDEX);
        client.update(u -> u.index(USER_INDEX).id(userId)
                .doc(Map.of("role", "admin")), User.class);
    }

    /** 降级为普通用户（永久超管不可降级） */
    public void demoteFromAdmin(String userId) throws IOException {
        ensureIndex(USER_INDEX);
        User user = getUserById(userId);
        if (user == null) throw new RuntimeException("用户不存在");
        if (user.isPermanentAdmin()) {
            throw new RuntimeException(user.getUsername() + " 是永久超管，不可降级");
        }
        if (!"admin".equals(user.getRole())) {
            throw new RuntimeException("该用户不是超管，无需降级");
        }
        client.update(u -> u.index(USER_INDEX).id(userId)
                .doc(Map.of("role", "user")), User.class);
    }

    // ==================== 账号管理 ====================

    /** 禁用用户（永久超管不可禁用） */
    public void disableUser(String userId) throws IOException {
        ensureIndex(USER_INDEX);
        User user = getUserById(userId);
        if (user == null) throw new RuntimeException("用户不存在");
        if (user.isPermanentAdmin()) {
            throw new RuntimeException(user.getUsername() + " 是永久超管，不可禁用");
        }
        client.update(u -> u.index(USER_INDEX).id(userId)
                .doc(Map.of("status", "disabled")), User.class);
        // 删除该用户所有会话
        deleteAllSessions(userId);
    }

    /** 启用用户 */
    public void enableUser(String userId) throws IOException {
        ensureIndex(USER_INDEX);
        client.update(u -> u.index(USER_INDEX).id(userId)
                .doc(Map.of("status", "active")), User.class);
    }

    /** 超管重置用户密码 */
    public void resetPassword(String userId, String newPassword) throws IOException {
        ensureIndex(USER_INDEX);
        if (newPassword == null || newPassword.length() < 3) {
            throw new RuntimeException("密码至少3位");
        }
        client.update(u -> u.index(USER_INDEX).id(userId)
                .doc(Map.of("password", hash(newPassword))), User.class);
        // 重置密码后踢出所有会话
        deleteAllSessions(userId);
    }

    /** 设置用户备注 */
    public void setRemark(String userId, String remark) throws IOException {
        ensureIndex(USER_INDEX);
        client.update(u -> u.index(USER_INDEX).id(userId)
                .doc(Map.of("remark", remark)), User.class);
    }

    // ==================== 统计 ====================

    public Map<String, Long> getUserStats() throws IOException {
        ensureIndex(USER_INDEX);
        SearchResponse<User> resp = client.search(s -> s
                .index(USER_INDEX).query(q -> q.matchAll(ma -> ma)).size(0), User.class);
        long total = resp.hits().total() != null ? resp.hits().total().value() : 0;

        long admins = 0, disabled = 0;
        SearchResponse<User> adminResp = client.search(s -> s
                .index(USER_INDEX).query(q -> q.term(t -> t.field("role").value("admin"))).size(0), User.class);
        admins = adminResp.hits().total() != null ? adminResp.hits().total().value() : 0;

        SearchResponse<User> disResp = client.search(s -> s
                .index(USER_INDEX).query(q -> q.term(t -> t.field("status").value("disabled"))).size(0), User.class);
        disabled = disResp.hits().total() != null ? disResp.hits().total().value() : 0;

        return Map.of("total", total, "admins", admins, "disabled", disabled, "active", total - disabled);
    }

    // ==================== 工具方法 ====================

    private void deleteAllSessions(String userId) throws IOException {
        try {
            SearchResponse<Session> resp = client.search(s -> s
                    .index(SESSION_INDEX)
                    .query(q -> q.match(m -> m.field("userId").query(userId)))
                    .size(1000), Session.class);
            for (Hit<Session> hit : resp.hits().hits()) {
                try { client.delete(d -> d.index(SESSION_INDEX).id(hit.id())); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }
}
