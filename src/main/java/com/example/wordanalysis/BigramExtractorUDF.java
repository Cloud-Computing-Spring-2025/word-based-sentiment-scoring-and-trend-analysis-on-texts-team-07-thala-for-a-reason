package com.example.udf;

import java.util.ArrayList;
import java.util.List;
import org.apache.hadoop.hive.ql.exec.UDF;
import org.apache.hadoop.io.Text;

public class BigramExtractorUDF extends UDF {

    public List<Text> evaluate(Text line) {
        List<Text> bigrams = new ArrayList<>();
        if (line == null || line.toString().trim().isEmpty()) {
            return bigrams;
        }

        String[] words = line.toString().trim().split("\\s+");
        for (int i = 0; i < words.length - 1; i++) {
            bigrams.add(new Text(words[i] + " " + words[i + 1]));
        }
        return bigrams;
    }
}
