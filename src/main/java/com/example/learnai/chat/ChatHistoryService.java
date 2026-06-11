package com.example.learnai.chat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ChatHistoryService {

    private static final String INDEX = "chat_history";
    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000;

    private final ElasticsearchClient client;

    public ChatHistoryService(ElasticsearchClient client) {
        this.client = client;
    }

    private void ensureIndex() throws IOException {
        boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(INDEX))).value();
        if (!exists) {
            client.indices().create(c -> c.index(INDEX)
                .mappings(m -> m.properties("userId", p -> p.keyword(k -> k))));
        }
    }

    public ChatMessage save(ChatMessage msg) throws IOException {
        ensureIndex();
        msg.setId(UUID.randomUUID().toString());
        client.index(i -> i.index(INDEX).id(msg.getId()).document(msg).refresh(Refresh.True));
        // 异步清理超过30天的旧记录
        new Thread(() -> {
            try { cleanup(); } catch (Exception ignored) {}
        }).start();
        return msg;
    }

    public List<ChatMessage> list(String userId, boolean isAdmin, String targetUserId) throws IOException {
        ensureIndex();
        long thirtyDaysAgo = System.currentTimeMillis() - THIRTY_DAYS_MS;
        SearchResponse<ChatMessage> resp;
        if (isAdmin) {
            // 超管：如果指定了 targetUserId 则只看该用户，否则看全部
            resp = client.search(s -> s
                    .index(INDEX)
                    .query(q -> {
                        var bool = new ArrayList<co.elastic.clients.elasticsearch._types.query_dsl.Query>();
                        bool.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(
                            qb -> qb.range(r -> r.field("createdAt")
                                .gte(co.elastic.clients.json.JsonData.of(thirtyDaysAgo)))));
                        if (targetUserId != null && !targetUserId.isEmpty()) {
                            bool.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(
                                qb -> qb.term(t -> t.field("userId").value(targetUserId))));
                        }
                        return q.bool(b -> b.must(bool));
                    })
                    .sort(sort -> sort.field(f -> f.field("createdAt").order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)))
                    .size(500),
                    ChatMessage.class);
        } else {
            resp = client.search(s -> s
                    .index(INDEX)
                    .query(q -> q.bool(b -> b.must(List.of(
                        co.elastic.clients.elasticsearch._types.query_dsl.Query.of(
                            qb -> qb.term(t -> t.field("userId").value(userId))),
                        co.elastic.clients.elasticsearch._types.query_dsl.Query.of(
                            qb -> qb.range(r -> r.field("createdAt")
                                .gte(co.elastic.clients.json.JsonData.of(thirtyDaysAgo))))
                    ))))
                    .sort(sort -> sort.field(f -> f.field("createdAt").order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)))
                    .size(500),
                    ChatMessage.class);
        }
        List<ChatMessage> list = new ArrayList<>();
        for (Hit<ChatMessage> hit : resp.hits().hits()) {
            ChatMessage m = hit.source();
            if (m != null) {
                m.setId(hit.id());
                list.add(m);
            }
        }
        return list;
    }

    /** 删除超过30天的旧记录 */
    private void cleanup() throws IOException {
        long thirtyDaysAgo = System.currentTimeMillis() - THIRTY_DAYS_MS;
        client.deleteByQuery(d -> d
            .index(INDEX)
            .query(q -> q.range(r -> r.field("createdAt")
                .lt(co.elastic.clients.json.JsonData.of(thirtyDaysAgo)))));
    }
}
