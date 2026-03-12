package com.example.molgenie.kb;

import com.example.molgenie.chem.CdkFingerprint;
import com.example.molgenie.chem.CdkFingerprint;
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
import io.milvus.param.collection.GetCollectionStatisticsParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.dml.SearchParam.Builder;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.GetCollStatResponseWrapper;
import io.milvus.response.SearchResultsWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class MilvusKnowledgeBase {

    private final MilvusClient client;
    private final String collection;
    private final int fpBits;
    private final CdkFingerprint fp;
    private final DashScopeEmbeddingService embedding;
    private final int textDim;

    public MilvusKnowledgeBase(
            MilvusClient client,
            DashScopeEmbeddingService embedding,
            @Value("${molgenie.kb.milvus.collection:molgenie_kb_v2}") String collection,
            @Value("${molgenie.kb.milvus.fingerprintBits:1024}") int fpBits
    ) {
        this.client = client;
        this.collection = collection;
        this.fpBits = fpBits;
        this.fp = new CdkFingerprint(fpBits);
        this.embedding = embedding;
        this.textDim = embedding.dimensions();
        ensureCollection();
    }

    public String addMolecule(String smiles,
                              Map<String, String> properties,
                              String sourceFileName,
                              String sourceType) throws Exception {
        String id = UUID.randomUUID().toString();
        long ts = Instant.now().toEpochMilli();

        byte[] bin = fp.fingerprintBinary(smiles);
        String flatProps = toFlatProperties(properties);
        String text = buildText(smiles, flatProps, sourceFileName, sourceType);
        DebugLogger.log("MilvusKnowledgeBase#addMolecule", "embedding text for molecule", "H3", "pre-run",
                Map.of("id", id, "sourceType", sourceType, "sourceFile", sourceFileName));
        float[] textVecArr = embedding.embed(text);
        List<Float> textVec = toFloatList(textVecArr);

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("id", List.of(id)));
        fields.add(new InsertParam.Field("smiles", List.of(smiles)));
        fields.add(new InsertParam.Field("sourceFileName", List.of(sourceFileName == null ? "" : sourceFileName)));
        fields.add(new InsertParam.Field("sourceType", List.of(sourceType == null ? "" : sourceType)));
        fields.add(new InsertParam.Field("ingestedAt", List.of(ts)));
        fields.add(new InsertParam.Field("properties", List.of(flatProps)));
        fields.add(new InsertParam.Field("text", List.of(text)));
        fields.add(new InsertParam.Field("textVec", List.of(textVec)));
        fields.add(new InsertParam.Field("fp", List.of(bin)));

        R<?> r = client.insert(InsertParam.newBuilder()
                .withCollectionName(collection)
                .withFields(fields)
                .build());
        if (r.getStatus() != 0) {
            DebugLogger.log("MilvusKnowledgeBase#addMolecule", "Milvus insert failed", "H4", "pre-run",
                    Map.of("id", id, "status", r.getStatus(), "message", r.getMessage()));
            throw new RuntimeException("Milvus insert failed: " + r.getMessage());
        }
        // flush is optional; many deployments auto-flush. Keep simple.
        return id;
    }

    public long count(boolean flush) {
        R<io.milvus.grpc.GetCollectionStatisticsResponse> r = client.getCollectionStatistics(
                GetCollectionStatisticsParam.newBuilder()
                        .withCollectionName(collection)
                        .withFlush(flush)
                        .build()
        );
        if (r.getStatus() != 0) {
            throw new RuntimeException("Milvus getCollectionStatistics failed: " + r.getMessage());
        }
        GetCollStatResponseWrapper w = new GetCollStatResponseWrapper(r.getData());
        return w.getRowCount();
    }

    public List<KbMolecule> vectorSearchBySmiles(String smiles, int topK) throws Exception {
        byte[] bin = fp.fingerprintBinary(smiles);

        Builder b = SearchParam.newBuilder()
                .withCollectionName(collection)
                .withVectorFieldName("fp")
                .withVectors(List.of(bin))
                .withTopK(Math.max(1, Math.min(200, topK)))
                .withMetricType(MetricType.JACCARD)
                .withOutFields(List.of("id", "smiles", "sourceFileName", "sourceType", "ingestedAt", "properties"))
                .withParams("{\"nprobe\":16}");

        R<io.milvus.grpc.SearchResults> r = client.search(b.build());
        if (r.getStatus() != 0) {
            throw new RuntimeException("Milvus search failed: " + r.getMessage());
        }

        List<KbMolecule> out = new ArrayList<>();
        SearchResultsWrapper w = new SearchResultsWrapper(r.getData().getResults());

        // Use per-field lists to construct output
        List<?> ids = w.getFieldData("id", 0);
        List<?> smilesList = w.getFieldData("smiles", 0);
        List<?> sourceNames = w.getFieldData("sourceFileName", 0);
        List<?> sourceTypes = w.getFieldData("sourceType", 0);
        List<?> ingestedAts = w.getFieldData("ingestedAt", 0);
        List<?> props = w.getFieldData("properties", 0);

        int n = ids == null ? 0 : ids.size();
        for (int i = 0; i < n; i++) {
            String id = ids.get(i) == null ? "" : ids.get(i).toString();
            String smi = smilesList != null && i < smilesList.size() && smilesList.get(i) != null ? smilesList.get(i).toString() : "";
            String sfn = sourceNames != null && i < sourceNames.size() && sourceNames.get(i) != null ? sourceNames.get(i).toString() : "";
            String st = sourceTypes != null && i < sourceTypes.size() && sourceTypes.get(i) != null ? sourceTypes.get(i).toString() : "";
            Instant t = Instant.now();
            if (ingestedAts != null && i < ingestedAts.size() && ingestedAts.get(i) != null) {
                try {
                    long ms = Long.parseLong(ingestedAts.get(i).toString());
                    t = Instant.ofEpochMilli(ms);
                } catch (Exception ignored) {
                }
            }
            Map<String, String> p = parseFlatProperties(props != null && i < props.size() && props.get(i) != null ? props.get(i).toString() : "");
            out.add(new KbMolecule(id, smi, p, sfn, st, t));
        }
        return out;
    }

    public List<KbMolecule> vectorSearchByText(String query, int topK) throws Exception {
        float[] qv = embedding.embed(query);

        Builder b = SearchParam.newBuilder()
                .withCollectionName(collection)
                .withVectorFieldName("textVec")
                .withVectors(List.of(qv))
                .withTopK(Math.max(1, Math.min(200, topK)))
                .withMetricType(MetricType.COSINE)
                .withOutFields(List.of("id", "smiles", "sourceFileName", "sourceType", "ingestedAt", "properties"))
                .withParams("{\"nprobe\":16}");

        R<io.milvus.grpc.SearchResults> r = client.search(b.build());
        if (r.getStatus() != 0) {
            throw new RuntimeException("Milvus search failed: " + r.getMessage());
        }

        SearchResultsWrapper w = new SearchResultsWrapper(r.getData().getResults());

        List<?> ids = w.getFieldData("id", 0);
        List<?> smilesList = w.getFieldData("smiles", 0);
        List<?> sourceNames = w.getFieldData("sourceFileName", 0);
        List<?> sourceTypes = w.getFieldData("sourceType", 0);
        List<?> ingestedAts = w.getFieldData("ingestedAt", 0);
        List<?> props = w.getFieldData("properties", 0);

        int n = ids == null ? 0 : ids.size();
        List<KbMolecule> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String id = ids.get(i) == null ? "" : ids.get(i).toString();
            String smi = smilesList != null && i < smilesList.size() && smilesList.get(i) != null ? smilesList.get(i).toString() : "";
            String sfn = sourceNames != null && i < sourceNames.size() && sourceNames.get(i) != null ? sourceNames.get(i).toString() : "";
            String st = sourceTypes != null && i < sourceTypes.size() && sourceTypes.get(i) != null ? sourceTypes.get(i).toString() : "";
            Instant t = Instant.now();
            if (ingestedAts != null && i < ingestedAts.size() && ingestedAts.get(i) != null) {
                try {
                    long ms = Long.parseLong(ingestedAts.get(i).toString());
                    t = Instant.ofEpochMilli(ms);
                } catch (Exception ignored) {
                }
            }
            Map<String, String> p = parseFlatProperties(props != null && i < props.size() && props.get(i) != null ? props.get(i).toString() : "");
            out.add(new KbMolecule(id, smi, p, sfn, st, t));
        }
        return out;
    }

    private void ensureCollection() {
        R<Boolean> has = client.hasCollection(HasCollectionParam.newBuilder().withCollectionName(collection).build());
        if (has.getStatus() != 0) {
            throw new RuntimeException("Milvus hasCollection failed: " + has.getMessage());
        }
        if (Boolean.TRUE.equals(has.getData())) {
            client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(collection).build());
            return;
        }

        List<FieldType> fields = List.of(
                FieldType.newBuilder().withName("id").withDataType(DataType.VarChar).withPrimaryKey(true).withAutoID(false).withMaxLength(64).build(),
                FieldType.newBuilder().withName("smiles").withDataType(DataType.VarChar).withMaxLength(4096).build(),
                FieldType.newBuilder().withName("sourceFileName").withDataType(DataType.VarChar).withMaxLength(1024).build(),
                FieldType.newBuilder().withName("sourceType").withDataType(DataType.VarChar).withMaxLength(128).build(),
                FieldType.newBuilder().withName("ingestedAt").withDataType(DataType.Int64).build(),
                FieldType.newBuilder().withName("properties").withDataType(DataType.VarChar).withMaxLength(4096).build(),
                FieldType.newBuilder().withName("text").withDataType(DataType.VarChar).withMaxLength(4096).build(),
                FieldType.newBuilder().withName("textVec").withDataType(DataType.FloatVector).withDimension(textDim).build(),
                FieldType.newBuilder().withName("fp").withDataType(DataType.BinaryVector).withDimension(fpBits).build()
        );

        R<?> create = client.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(collection)
                .withDescription("MolGenie chemical KB")
                .withShardsNum(2)
                .withFieldTypes(fields)
                .build());
        if (create.getStatus() != 0) {
            throw new RuntimeException("Milvus createCollection failed: " + create.getMessage());
        }

        // Create binary vector index (BIN_IVF_FLAT) for fp
        R<?> idx = client.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(collection)
                .withFieldName("fp")
                .withIndexType(IndexType.BIN_IVF_FLAT)
                .withMetricType(MetricType.JACCARD)
                .withExtraParam("{\"nlist\":1024}")
                .build());
        if (idx.getStatus() != 0) {
            throw new RuntimeException("Milvus createIndex failed: " + idx.getMessage());
        }

        // Create float vector index for textVec
        R<?> idx2 = client.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(collection)
                .withFieldName("textVec")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"nlist\":1024}")
                .build());
        if (idx2.getStatus() != 0) {
            throw new RuntimeException("Milvus createIndex(textVec) failed: " + idx2.getMessage());
        }

        client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(collection).build());
    }

    private static String buildText(String smiles, String flatProps, String sourceFileName, String sourceType) {
        StringBuilder sb = new StringBuilder();
        sb.append("smiles: ").append(smiles == null ? "" : smiles).append('\n');
        if (sourceType != null && !sourceType.isBlank()) sb.append("sourceType: ").append(sourceType).append('\n');
        if (sourceFileName != null && !sourceFileName.isBlank()) sb.append("sourceFile: ").append(sourceFileName).append('\n');
        if (flatProps != null && !flatProps.isBlank()) sb.append("properties:\n").append(flatProps);
        return sb.toString();
    }

    private static String toFlatProperties(Map<String, String> props) {
        if (props == null || props.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, String> e : props.entrySet()) {
            if (i++ > 0) sb.append('\n');
            sb.append(safe(e.getKey())).append('=').append(safe(e.getValue()));
        }
        return sb.toString();
    }

    private static Map<String, String> parseFlatProperties(String s) {
        Map<String, String> out = new HashMap<>();
        if (s == null || s.isBlank()) return out;
        String[] lines = s.split("\\r?\\n");
        for (String line : lines) {
            int i = line.indexOf('=');
            if (i <= 0) continue;
            String k = line.substring(0, i).trim();
            String v = line.substring(i + 1).trim();
            if (!k.isEmpty()) out.put(k, v);
        }
        return out;
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) {
            list.add(v);
        }
        return list;
    }
}

