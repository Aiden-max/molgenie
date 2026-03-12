package com.example.molgenie.chem;

import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.io.MDLV2000Reader;
import org.openscience.cdk.smiles.SmilesGenerator;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class MolParser {

    public MoleculeRecord parseMol(InputStream in) throws Exception {
        try (MDLV2000Reader reader = new MDLV2000Reader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            IAtomContainer mol = reader.read(DefaultChemObjectBuilder.getInstance().newAtomContainer());
            String smiles = SmilesGenerator.unique().create(mol);
            Map<String, String> props = new HashMap<>();
            props.put("format", "MOL");
            return new MoleculeRecord(smiles, props);
        }
    }
}

