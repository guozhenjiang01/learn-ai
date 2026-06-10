package com.example.learnai.memo;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MemoService {

    private static final String INDEX = "memos";

    private final ElasticsearchClient client;

    public MemoService(ElasticsearchClient client) {
        this.client = client;
    }

    private void ensureIndex() throws IOException {
        boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(INDEX))).value();
        if (!exists) {
            client.indices().create(c -> c.index(INDEX)
                .mappings(m -> m.properties("userId", p -> p.keyword(k -> k))));
        }
    }

    public Memo add(Memo memo) throws IOException {
        ensureIndex();
        memo.setId(UUID.randomUUID().toString());
        client.index(i -> i
                .index(INDEX)
                .id(memo.getId())
                .document(memo)
                .refresh(Refresh.True));
        return memo;
    }

    public List<Memo> list(String userId, boolean isAdmin) throws IOException {
        ensureIndex();
        SearchResponse<Memo> resp;
        if (isAdmin) {
            resp = client.search(s -> s
                    .index(INDEX)
                    .query(q -> q.matchAll(ma -> ma))
                    .sort(sort -> sort.field(f -> f.field("createdAt").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
                    .size(200),
                    Memo.class);
        } else {
            resp = client.search(s -> s
                    .index(INDEX)
                    .query(q -> q.term(t -> t.field("userId").value(userId)))
                    .sort(sort -> sort.field(f -> f.field("createdAt").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
                    .size(200),
                    Memo.class);
        }
        List<Memo> list = new ArrayList<>();
        for (Hit<Memo> hit : resp.hits().hits()) {
            Memo m = hit.source();
            if (m != null) {
                m.setId(hit.id());
                list.add(m);
            }
        }
        return list;
    }

    public void delete(String id) throws IOException {
        client.delete(d -> d.index(INDEX).id(id).refresh(Refresh.True));
    }
}
