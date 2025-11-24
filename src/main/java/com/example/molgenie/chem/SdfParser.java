package com.example.molgenie.chem;

import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.io.IteratingSDFReader;
import org.openscience.cdk.smiles.SmilesGenerator;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

@Component
public class SdfParser {

    public List<MoleculeRecord> parseSdf(InputStream inputStream) throws Exception {
        List<MoleculeRecord> records = new ArrayList<>();
        try (IteratingSDFReader reader = new IteratingSDFReader(
                inputStream, SilentChemObjectBuilder.getInstance())) {

            SmilesGenerator smiGen = SmilesGenerator.unique();
            while (reader.hasNext()) {
                IAtomContainer mol = reader.next();
                if (mol == null) continue;
                String smiles = smiGen.create(mol);
                Map<String, String> props = new HashMap<>();
                for (Object key : mol.getProperties().keySet()) {
                    Object val = mol.getProperty(key);
                    if (val != null) props.put(key.toString(), val.toString());
                }
                records.add(new MoleculeRecord(smiles, props));
            }
        }
        return records;
    }
}