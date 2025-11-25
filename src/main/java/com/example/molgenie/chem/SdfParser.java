package com.example.molgenie.chem;

import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.io.iterator.IteratingSDFReader;
import org.openscience.cdk.smiles.SmilesGenerator;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Component
public class SdfParser {

    private static final Logger logger = Logger.getLogger(SdfParser.class.getName());

    /**
     *
     * @param inputStream 包含 SDF 数据的 InputStream。
     * @return 包含解析出的 MoleculeRecord 的列表。
     * @throws Exception 如果解析过程中发生错误。
     */
    public List<MoleculeRecord> parseSdf(InputStream inputStream) throws Exception {
        List<MoleculeRecord> records = new ArrayList<>();

        SmilesGenerator smiGen = SmilesGenerator.unique();

        // 使用 try-with-resources 管理 IteratingSDFReader
        try (IteratingSDFReader reader = new IteratingSDFReader(
                new InputStreamReader(inputStream), // IteratingSDFReader 通常接受 Reader
                DefaultChemObjectBuilder.getInstance())) { // 或 SilentChemObjectBuilder.getInstance()

            // 迭代读取每个分子
            while (reader.hasNext()) {
                try {
                    IAtomContainer molecule = reader.next();
                    if (molecule != null) {
                        // --- 提取 SMILES ---
                        String smiles;
                        try {
                            smiles = smiGen.create(molecule);
                        } catch (Exception e) {
                            logger.warning("Failed to generate SMILES for molecule. Skipping. Error: " + e.getMessage());
                            // 可以选择跳过无法生成 SMILES 的分子，或抛出异常
                            continue; // 跳过此分子
                            // throw new Exception("Error generating SMILES", e);
                        }

                        // --- 提取 Properties ---
                        Map<String, String> properties = new HashMap<>();
                        // getProperties() 通常返回 Map<Object, Object>
                        Map<Object, Object> molProps = molecule.getProperties();
                        if (molProps != null) {
                            for (Map.Entry<Object, Object> entry : molProps.entrySet()) {
                                Object key = entry.getKey();
                                Object val = entry.getValue();
                                // 将键和值都转换为字符串
                                if (key != null) { // 确保 key 不为 null
                                    properties.put(key.toString(), val != null ? val.toString() : "");
                                }
                            }
                        }

                        // --- 创建 MoleculeRecord ---
                        MoleculeRecord record = new MoleculeRecord(smiles, properties);
                        records.add(record);
                    }
                } catch (Exception e) {
                    // 处理单个分子读取/处理错误
                    logger.warning("Error processing a molecule from SDF stream. Skipping. Error: " + e.getMessage());
                    // 可以选择记录日志并继续，或抛出异常中断
                    // throw new Exception("Error processing molecule in SDF", e);
                }
            }

        } catch (Exception e) {
            logger.severe("Failed to parse SDF from InputStream. Error: " + e.getMessage());
            throw e;
        }

        logger.info("Parsed " + records.size() + " molecules from SDF stream.");
        return records;
    }
}