package com.example.learnai.audit;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.JsonData;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OperationLogService {

    private static final String INDEX = "operation_logs";
    private final ElasticsearchClient client;

    public OperationLogService(ElasticsearchClient client) {
        this.client = client;
    }

    private void ensureIndex() throws IOException {
        boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(INDEX))).value();
        if (!exists) {
            client.indices().create(c -> c.index(INDEX));
        }
    }

    public void log(OperationLog log) {
        try {
            ensureIndex();
            if (log.getId() == null) log.setId(UUID.randomUUID().toString());
            if (log.getTimestamp() == 0) log.setTimestamp(System.currentTimeMillis());
            client.index(i -> i.index(INDEX).id(log.getId()).document(log));
        } catch (Exception e) {
            System.err.println("[Audit] 记录失败: " + e.getMessage());
        }
    }

    /** 查询操作日志（按时间倒序） */
    public List<OperationLog> query(String userId, String action,
                                     Long fromTs, Long toTs, int size) throws IOException {
        ensureIndex();
        SearchResponse<OperationLog> resp = client.search(s -> {
            s.index(INDEX).size(size)
             .sort(sort -> sort.field(f -> f.field("timestamp").order(SortOrder.Desc)));

            // Build bool query
            var boolBuilder = new ArrayList<co.elastic.clients.elasticsearch._types.query_dsl.Query>();

            if (userId != null && !userId.isEmpty()) {
                boolBuilder.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(
                    q -> q.term(t -> t.field("userId").value(userId))));
            }
            if (action != null && !action.isEmpty()) {
                boolBuilder.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(
                    q -> q.term(t -> t.field("action").value(action))));
            }
            if (fromTs != null && toTs != null) {
                boolBuilder.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(
                    q -> q.range(r -> r.field("timestamp")
                        .gte(JsonData.of(fromTs))
                        .lte(JsonData.of(toTs)))));
            }

            if (!boolBuilder.isEmpty()) {
                s.query(q -> q.bool(b -> b.must(boolBuilder)));
            } else {
                s.query(q -> q.matchAll(ma -> ma));
            }
            return s;
        }, OperationLog.class);

        List<OperationLog> logs = new ArrayList<>();
        for (Hit<OperationLog> hit : resp.hits().hits()) {
            OperationLog log = hit.source();
            if (log != null) {
                log.setId(hit.id());
                logs.add(log);
            }
        }
        return logs;
    }
}
