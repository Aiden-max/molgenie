package com.example.molgenie.chem;

import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.qsar.DescriptorValue;
import org.openscience.cdk.qsar.descriptors.molecular.WeightDescriptor;
import org.openscience.cdk.qsar.result.DoubleArrayResult;
import org.openscience.cdk.qsar.result.DoubleResult;
import org.openscience.cdk.qsar.result.IDescriptorResult;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.CDKHydrogenAdder;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import org.springframework.stereotype.Component;

@Component
public class MoleculeValidator {

    private final SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
    private final WeightDescriptor weightDesc = new WeightDescriptor();

    public ValidationResult validate(String smiles) {
        if (smiles == null || smiles.trim().isEmpty()) {
            return new ValidationResult(smiles, null, null, false, "Empty SMILES");
        }

        try {
            // 1. 解析 SMILES
            IAtomContainer mol = parser.parseSmiles(smiles);

            // 2. 分子预处理：感知原子类型 + 添加并转换氢
            AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(mol);
            CDKHydrogenAdder hadder = CDKHydrogenAdder.getInstance(mol.getBuilder());
            hadder.addImplicitHydrogens(mol);
            AtomContainerManipulator.convertImplicitToExplicitHydrogens(mol);

            // 3. 计算分子量
            DescriptorValue weightValue = weightDesc.calculate(mol);
            Double mw = extractDoubleValue(weightValue);

            // 4. 估算 LogP
            Double logP = null;
            try {
                double lp = estimateLogP(smiles);
                if (!Double.isNaN(lp) && !Double.isInfinite(lp)) {
                    logP = lp;
                }
            } catch (Exception ignored) {
                // LogP 估算失败
                logP = null;
                System.err.println("LogP estimation failed for SMILES: " + smiles);
            }

            // 5. 验证 Lipinski-like 规则
            boolean valid = false;
            String reason = "";
            if (mw != null && logP != null) {
                if (mw <= 500.0 && logP >= -1.0 && logP <= 5.0) {
                    valid = true;
                } else {
                    reason = (mw > 500.0) ? "MW>500" : "LogP out of range";
                }
            } else {
                reason = (mw == null) ? "MW calculation failed" : "LogP estimation failed";
            }

            return new ValidationResult(
                    smiles,
                    mw != null ? mw.floatValue() : null,
                    logP != null ? logP.floatValue() : null,
                    valid,
                    valid ? "" : reason
            );

        } catch (Exception e) {
            return new ValidationResult(smiles, null, null, false, "Invalid SMILES or processing error: " + e.getMessage());
        }
    }

    /**
     * 安全地从 DescriptorValue 中提取 double 数值。
     * @param dv DescriptorValue
     * @return 提取的 Double 值，如果失败则返回 null
     */
    private Double extractDoubleValue(DescriptorValue dv) {
        if (dv == null) {
            return null;
        }
        if (dv.getException() != null) {
            System.err.println("Descriptor calculation error for " + (dv.getNames() != null && dv.getNames().length > 0 ? dv.getNames()[0] : "Unknown") + ": " + dv.getException().getMessage());
            return null;
        }

        IDescriptorResult result = dv.getValue();
        if (result == null) {
            return null;
        }

        if (result instanceof DoubleResult dr) {
            double v = dr.doubleValue();
            return Double.isNaN(v) || Double.isInfinite(v) ? null : v;
        } else if (result instanceof DoubleArrayResult dar) {
            Double firstValObj = dar.get(0);
            if (firstValObj != null) {
                double v = firstValObj;
                return Double.isNaN(v) || Double.isInfinite(v) ? null : v;
            }
        }
        return null;
    }



    private double estimateLogP(String s) {
        int c = count(s, 'C') + count(s, 'c');
        int o = count(s, 'O') + count(s, 'o');
        int n = count(s, 'N') + count(s, 'n');
        int cl = countStr(s, "Cl") + countStr(s, "cl");
        return c * 0.5 - o * 0.7 - n * 0.5 + cl * 0.7;
    }

    private int count(String s, char ch) {
        return (int) s.chars().filter(c -> c == ch).count();
    }

    private int countStr(String s, String substr) {
        if (substr == null || substr.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = s.indexOf(substr, idx)) != -1) {
            count++;
            idx += substr.length();
        }
        return count;
    }

    public record ValidationResult(String smiles, Float mw, Float logP, boolean isValid, String reason) {}
}