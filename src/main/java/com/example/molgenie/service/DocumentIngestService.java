package com.example.molgenie.service;

import com.example.molgenie.chem.MolParser;
import com.example.molgenie.chem.MoleculeRecord;
import com.example.molgenie.chem.SdfParser;
import com.example.molgenie.debug.DebugLogger;
import com.example.molgenie.kb.KbService;
import com.example.molgenie.kb.IngestResult;
import com.example.molgenie.service.DocumentVectorService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DocumentIngestService {

    private final KbService kb;
    private final SdfParser sdfParser;
    private final MolParser molParser;
    private final DocumentVectorService docVectors;

    public DocumentIngestService(KbService kb, SdfParser sdfParser, MolParser molParser, DocumentVectorService docVectors) {
        this.kb = kb;
        this.sdfParser = sdfParser;
        this.molParser = molParser;
        this.docVectors = docVectors;
    }

    public IngestResult ingest(List<MultipartFile> files) {
        int moleculesAdded = 0;
        int docsProcessed = 0;
        List<String> warnings = new ArrayList<>();

        if (files == null || files.isEmpty()) {
            DebugLogger.log("DocumentIngestService#ingest", "no files provided", "KB3", "pre-run",
                    Map.of());
            return new IngestResult(0, 0, List.of("No files provided."));
        }

        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) continue;
            docsProcessed++;
            String name = f.getOriginalFilename() == null ? "upload" : f.getOriginalFilename();
            String ext = extLower(name);
            DebugLogger.log("DocumentIngestService#ingest", "processing file", "KB4", "pre-run",
                    Map.of("name", name, "ext", ext));
            try (InputStream in = f.getInputStream()) {
                switch (ext) {
                    case "sdf" -> {
                        byte[] bytes = readAllBytes(in);
                        List<MoleculeRecord> mols = sdfParser.parseSdf(new ByteArrayInputStream(bytes));
                        moleculesAdded += addAll(mols, name, "SDF");
                        String docText = buildSdfDocText(name, mols);
                        docVectors.index(name, "SDF", docText);
                    }
                    case "mol" -> {
                        byte[] bytes = readAllBytes(in);
                        MoleculeRecord r = molParser.parseMol(new ByteArrayInputStream(bytes));
                        kb.addMolecule(r.smiles(), r.properties(), name, "MOL");
                        moleculesAdded += 1;
                        String docText = buildMolDocText(name, r);
                        docVectors.index(name, "MOL", docText);
                    }
                    case "docx" -> {
                        byte[] bytes = readAllBytes(in);
                        moleculesAdded += ingestDocx(new ByteArrayInputStream(bytes), name, warnings);
                        String docText = buildDocxText(bytes, name, warnings);
                        docVectors.index(name, "DOCX", docText);
                    }
                    case "xlsx" -> {
                        byte[] bytes = readAllBytes(in);
                        moleculesAdded += ingestXlsx(new ByteArrayInputStream(bytes), name, warnings);
                        String docText = buildXlsxText(bytes, name, warnings);
                        docVectors.index(name, "XLSX", docText);
                    }
                    default -> warnings.add("Unsupported file type: " + name);
                }
            } catch (Exception e) {
                warnings.add("Failed to ingest " + name + ": " + e.getMessage());
            }
        }

        return new IngestResult(moleculesAdded, docsProcessed, warnings);
    }

    private int ingestDocx(InputStream in, String fileName, List<String> warnings) throws Exception {
        // 仅处理 DOCX 中嵌入的 .sdf/.mol 文件，不从正文抽取 SMILES
        byte[] bytes = readAllBytes(in);
        int added = ingestEmbeddedZipFiles(new ByteArrayInputStream(bytes), fileName, warnings);

        return added;
    }

    private int ingestXlsx(InputStream in, String fileName, List<String> warnings) throws Exception {
        // 仅处理 XLSX 中嵌入的 .sdf/.mol 文件，不从单元格文本抽取 SMILES
        byte[] bytes = readAllBytes(in);
        int added = ingestEmbeddedZipFiles(new ByteArrayInputStream(bytes), fileName, warnings);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (wb.getNumberOfSheets() == 0) {
                warnings.add("XLSX parsed but has no sheets: " + fileName);
            }
        } catch (Exception e) {
            warnings.add("XLSX text parse failed (still may have embedded molecules): " + fileName + " - " + e.getMessage());
        }
        return added;
    }

    private int ingestEmbeddedZipFiles(InputStream in, String containerName, List<String> warnings) throws Exception {
        int added = 0;
        try (ZipInputStream zis = new ZipInputStream(in, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String n = entry.getName();
                String ext = extLower(n);
                if (!("sdf".equals(ext) || "mol".equals(ext))) continue;

                byte[] embedded = readAllBytes(zis);
                String source = containerName + "::" + n;
                try {
                    if ("sdf".equals(ext)) {
                        List<MoleculeRecord> mols = sdfParser.parseSdf(new ByteArrayInputStream(embedded));
                        added += addAll(mols, source, "SDF(embedded)");
                    } else {
                        MoleculeRecord r = molParser.parseMol(new ByteArrayInputStream(embedded));
                        kb.addMolecule(r.smiles(), r.properties(), source, "MOL(embedded)");
                        added += 1;
                    }
                } catch (Exception e) {
                    warnings.add("Failed to parse embedded " + source + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            warnings.add("Failed to scan embedded files in " + containerName + ": " + e.getMessage());
        }
        return added;
    }

    private int addAll(List<MoleculeRecord> records, String source, String sourceType) {
        if (records == null || records.isEmpty()) return 0;
        int n = 0;
        for (MoleculeRecord r : records) {
            if (r == null || r.smiles() == null || r.smiles().isBlank()) continue;
            try {
                kb.addMolecule(r.smiles(), r.properties(), source, sourceType);
            } catch (Exception e) {
                // swallow and continue; caller will already handle warnings for the file
                continue;
            }
            n++;
        }
        return n;
    }

    private static String extLower(String name) {
        String n = name == null ? "" : name;
        int i = n.lastIndexOf('.');
        if (i < 0 || i == n.length() - 1) return "";
        return n.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private static byte[] readAllBytes(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) >= 0) {
            if (r > 0) bos.write(buf, 0, r);
        }
        return bos.toByteArray();
    }

    private static String buildSdfDocText(String fileName, List<MoleculeRecord> mols) {
        StringBuilder sb = new StringBuilder();
        sb.append("SDF file: ").append(fileName).append('\n');
        if (mols != null) {
            int max = Math.min(20, mols.size());
            for (int i = 0; i < max; i++) {
                MoleculeRecord m = mols.get(i);
                if (m == null) continue;
                sb.append("Molecule ").append(i + 1).append(": ");
                if (m.smiles() != null) sb.append(m.smiles());
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String buildMolDocText(String fileName, MoleculeRecord r) {
        StringBuilder sb = new StringBuilder();
        sb.append("MOL file: ").append(fileName).append('\n');
        if (r != null && r.smiles() != null) {
            sb.append("SMILES: ").append(r.smiles()).append('\n');
        }
        return sb.toString();
    }

    private static String buildDocxText(byte[] bytes, String fileName, List<String> warnings) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String text = doc.getParagraphs().stream()
                    .map(p -> p.getText() == null ? "" : p.getText())
                    .filter(s -> !s.isBlank())
                    .limit(500)
                    .reduce("", (a, b) -> a + "\n" + b);
            return text == null ? "" : text;
        } catch (Exception e) {
            warnings.add("DOCX text parse failed for doc embedding: " + fileName + " - " + e.getMessage());
            return "";
        }
    }

    private static String buildXlsxText(byte[] bytes, String fileName, List<String> warnings) {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            int cellCount = 0;
            for (int i = 0; i < wb.getNumberOfSheets() && i < 10; i++) {
                var sheet = wb.getSheetAt(i);
                if (sheet == null) continue;
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        if (cell == null) continue;
                        String v = cell.toString();
                        if (v != null && !v.isBlank()) {
                            sb.append(v).append('\n');
                            cellCount++;
                            if (cellCount >= 5000) break;
                        }
                    }
                    if (cellCount >= 5000) break;
                }
                if (cellCount >= 5000) break;
            }
            return sb.toString();
        } catch (Exception e) {
            warnings.add("XLSX text parse failed for doc embedding: " + fileName + " - " + e.getMessage());
            return "";
        }
    }
}

