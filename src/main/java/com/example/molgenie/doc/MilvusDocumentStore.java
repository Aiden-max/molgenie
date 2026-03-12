package com.example.molgenie.doc;

import com.example.molgenie.debug.DebugLogger;
import com.example.molgenie.service.DashScopeEmbeddingService;
import io.milvus.client.MilvusClient;
import io.milvus.grpc.DataType;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.response.SearchResultsWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class MilvusDocumentStore {

    private final MilvusClient client;
    private final DashScopeEmbeddingService embedding;
    private final String collection;
    private final int dim;

    public MilvusDocumentStore(MilvusClient client,
                               DashScopeEmbeddingService embedding,
                               @Value("${molgenie.doc.milvus.collection:molgenie_docs_v1}") String collection) {
        this.client = client;
        this.embedding = embedding;
        this.collection = collection;
        this.dim = embedding.dimensions();
        ensureCollection();
    }

    public void addDocument(String fileName, String fileType, String text) throws Exception {
        if (text == null || text.isBlank()) {
            return;
        }
        String id = UUID.randomUUID().toString();
        float[] vecArr = embedding.embed(text);
        List<Float> vec = toFloatList(vecArr);
        DebugLogger.log("MilvusDocumentStore#addDocument", "embedding doc", "D1", "run",
                Map.of("id", id, "fileName", fileName, "fileType", fileType));

        InsertParam.Field fId = new InsertParam.Field("id", List.of(id));
        InsertParam.Field fName = new InsertParam.Field("fileName", List.of(fileName == null ? "" : fileName));
        InsertParam.Field fType = new InsertParam.Field("fileType", List.of(fileType == null ? "" : fileType));
        InsertParam.Field fText = new InsertParam.Field("text", List.of(trimTo(text, 8000)));
        InsertParam.Field fVec = new InsertParam.Field("embedding", List.of(vec));

        R<?> r = client.insert(InsertParam.newBuilder()
                .withCollectionName(collection)
                .withFields(List.of(fId, fName, fType, fText, fVec))
                .build());
        if (r.getStatus() != 0) {
            DebugLogger.log("MilvusDocumentStore#addDocument", "Milvus insert failed", "D2", "run",
                    Map.of("status", r.getStatus(), "message", r.getMessage()));
            throw new RuntimeException("Milvus doc insert failed: " + r.getMessage());
        }
    }

    private void ensureCollection() {
        R<Boolean> has = client.hasCollection(
                HasCollectionParam.newBuilder().withCollectionName(collection).build());
        if (has.getStatus() != 0) {
            throw new RuntimeException("Milvus hasCollection(doc) failed: " + has.getMessage());
        }
        if (Boolean.TRUE.equals(has.getData())) {
            client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(collection).build());
            return;
        }

        List<FieldType> fields = List.of(
                FieldType.newBuilder().withName("id").withDataType(DataType.VarChar).withPrimaryKey(true)
                        .withAutoID(false).withMaxLength(64).build(),
                FieldType.newBuilder().withName("fileName").withDataType(DataType.VarChar).withMaxLength(512).build(),
                FieldType.newBuilder().withName("fileType").withDataType(DataType.VarChar).withMaxLength(64).build(),
                FieldType.newBuilder().withName("text").withDataType(DataType.VarChar).withMaxLength(8192).build(),
                FieldType.newBuilder().withName("embedding").withDataType(DataType.FloatVector).withDimension(dim).build()
        );

        R<?> create = client.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(collection)
                .withDescription("MolGenie document store")
                .withShardsNum(2)
                .withFieldTypes(fields)
                .build());
        if (create.getStatus() != 0) {
            throw new RuntimeException("Milvus createCollection(doc) failed: " + create.getMessage());
        }

        R<?> idx = client.createIndex(io.milvus.param.index.CreateIndexParam.newBuilder()
                .withCollectionName(collection)
                .withFieldName("embedding")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"nlist\":1024}")
                .build());
        if (idx.getStatus() != 0) {
            throw new RuntimeException("Milvus createIndex(embedding) failed: " + idx.getMessage());
        }

        client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(collection).build());
    }

    private static String trimTo(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }

    private static List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) {
            list.add(v);
        }
        return list;
    }
}

