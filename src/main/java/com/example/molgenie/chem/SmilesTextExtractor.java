package com.example.molgenie.chem;

import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort SMILES extraction from free text / spreadsheet cells.
 * Strategy:
 * - Find candidate tokens with a permissive regex
 * - Validate candidates by CDK SmilesParser
 * - Return unique SMILES (preserve insertion order)
 */
public final class SmilesTextExtractor {

    private static final Pattern CANDIDATE = Pattern.compile(
            // A permissive SMILES-ish token:
            // starts with common atom/aromatic symbols or '['
            // then continues with allowed SMILES characters
            "(?<![A-Za-z0-9])" +
                    "([A-Za-z\\[][-A-Za-z0-9@+\\-\\[\\]\\(\\)=#$\\\\/%.]{5,})" +
                    "(?![A-Za-z0-9])"
    );

    private final SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());

    public Set<String> extractValidated(String text, int max) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (text == null || text.isBlank() || max <= 0) return out;

        Matcher m = CANDIDATE.matcher(text);
        while (m.find()) {
            String token = m.group(1);
            if (token == null) continue;

            String cand = token.trim();
            if (cand.length() < 6) continue;
            if (looksLikeUrlOrEmail(cand)) continue;

            // Quick filter: must contain at least one atom-like letter
            if (!cand.chars().anyMatch(ch -> (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z'))) continue;

            if (isValidSmiles(cand)) {
                out.add(cand);
                if (out.size() >= max) break;
            }
        }
        return out;
    }

    private boolean isValidSmiles(String s) {
        try {
            parser.parseSmiles(s);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean looksLikeUrlOrEmail(String s) {
        String t = s.toLowerCase();
        return t.contains("http") || t.contains("www.") || t.contains("@");
    }
}

