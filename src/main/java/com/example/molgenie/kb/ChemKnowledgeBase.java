package com.example.molgenie.kb;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ChemKnowledgeBase {

    private final CopyOnWriteArrayList<KbMolecule> molecules = new CopyOnWriteArrayList<>();

    public KbMolecule addMolecule(String smiles,
                                  Map<String, String> properties,
                                  String sourceFileName,
                                  String sourceType) {
        KbMolecule m = new KbMolecule(
                UUID.randomUUID().toString(),
                smiles,
                properties,
                sourceFileName,
                sourceType,
                Instant.now()
        );
        molecules.add(m);
        return m;
    }

    public int size() {
        return molecules.size();
    }

    public List<KbMolecule> latest(int limit) {
        return molecules.stream()
                .sorted(Comparator.comparing(KbMolecule::ingestedAt).reversed())
                .limit(Math.max(0, limit))
                .toList();
    }

    public List<KbMolecule> search(String q, int limit) {
        String query = q == null ? "" : q.trim().toLowerCase();
        if (query.isEmpty()) {
            return latest(limit);
        }
        List<KbMolecule> out = new ArrayList<>();
        for (KbMolecule m : molecules) {
            if (m.smiles() != null && m.smiles().toLowerCase().contains(query)) {
                out.add(m);
            } else if (m.sourceFileName() != null && m.sourceFileName().toLowerCase().contains(query)) {
                out.add(m);
            } else if (m.properties() != null) {
                for (Map.Entry<String, String> e : m.properties().entrySet()) {
                    if ((e.getKey() != null && e.getKey().toLowerCase().contains(query)) ||
                            (e.getValue() != null && e.getValue().toLowerCase().contains(query))) {
                        out.add(m);
                        break;
                    }
                }
            }
            if (out.size() >= limit) break;
        }
        out.sort(Comparator.comparing(KbMolecule::ingestedAt).reversed());
        return out;
    }
}

