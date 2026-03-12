package com.example.molgenie.chem;

import org.openscience.cdk.fingerprint.Fingerprinter;
import org.openscience.cdk.fingerprint.IBitFingerprint;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import java.util.BitSet;

public final class CdkFingerprint {

    private final SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
    private final Fingerprinter fingerprinter;
    private final int size;

    public CdkFingerprint(int sizeBits) {
        this.size = sizeBits;
        this.fingerprinter = new Fingerprinter(sizeBits);
    }

    public int sizeBits() {
        return size;
    }

    public byte[] fingerprintBinary(String smiles) throws Exception {
        var mol = parser.parseSmiles(smiles);
        IBitFingerprint fp = fingerprinter.getBitFingerprint(mol);
        BitSet bs = fp.asBitSet();
        return bitSetToFixedBytes(bs, size);
    }

    private static byte[] bitSetToFixedBytes(BitSet bs, int sizeBits) {
        int sizeBytes = (sizeBits + 7) / 8;
        byte[] out = new byte[sizeBytes];
        for (int i = 0; i < sizeBits; i++) {
            if (!bs.get(i)) continue;
            int byteIndex = i / 8;
            int bitIndex = i % 8;
            // Milvus binary vectors use little-endian bits within a byte (bit 0 is LSB)
            out[byteIndex] |= (byte) (1 << bitIndex);
        }
        return out;
    }
}

