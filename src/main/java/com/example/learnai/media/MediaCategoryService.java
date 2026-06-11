package com.example.learnai.media;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MediaCategoryService {

    private static final String INDEX = "media_categories";
    private final ElasticsearchClient client;
    private boolean initialized = false;

    public MediaCategoryService(ElasticsearchClient client) {
        this.client = client;
    }

    private void ensureIndex() throws IOException {
        boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(INDEX))).value();
        if (!exists) {
            client.indices().create(c -> c.index(INDEX));
        }
        if (!initialized) {
            initDefaults();
            initialized = true;
        }
    }

    /** 初始化默认类目 */
    private void initDefaults() throws IOException {
        List<MediaCategory> existing = listAll();
        if (!existing.isEmpty()) return;

        String[][] defaults = {
            {"通勤", "🚄"}, {"工资", "💰"}, {"老婆", "👩‍🎓"},
            {"带娃", "👶"}, {"大厂", "🏢"}, {"其他", "📝"}
        };
        for (int i = 0; i < defaults.length; i++) {
            MediaCategory c = new MediaCategory(defaults[i][0], defaults[i][1], i);
            c.setId(UUID.randomUUID().toString());
            client.index(idx -> idx.index(INDEX).id(c.getId()).document(c).refresh(Refresh.True));
        }
    }

    public List<MediaCategory> listAll() throws IOException {
        ensureIndex();
        SearchResponse<MediaCategory> resp = client.search(s -> s
            .index(INDEX).size(50)
            .sort(sort -> sort.field(f -> f.field("sort").order(SortOrder.Asc)))
            .query(q -> q.matchAll(ma -> ma)),
            MediaCategory.class);
        List<MediaCategory> list = new ArrayList<>();
        for (Hit<MediaCategory> hit : resp.hits().hits()) {
            MediaCategory c = hit.source();
            if (c != null) { c.setId(hit.id()); list.add(c); }
        }
        return list;
    }

    /** 添加自定义类目 */
    public MediaCategory add(String name) throws IOException {
        ensureIndex();
        // 检查重复
        List<MediaCategory> existing = listAll();
        for (MediaCategory c : existing) {
            if (c.getName().equals(name)) return c;
        }
        MediaCategory cat = new MediaCategory(name, "📌", existing.size());
        cat.setId(UUID.randomUUID().toString());
        client.index(i -> i.index(INDEX).id(cat.getId()).document(cat).refresh(Refresh.True));
        return cat;
    }

    /** 删除类目 */
    public void delete(String id) throws IOException {
        client.delete(d -> d.index(INDEX).id(id).refresh(Refresh.True));
    }
}
