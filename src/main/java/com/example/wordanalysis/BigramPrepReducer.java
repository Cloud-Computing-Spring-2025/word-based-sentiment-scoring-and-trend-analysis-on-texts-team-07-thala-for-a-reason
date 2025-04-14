package com.example.wordanalysis;

import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.Reducer;
import java.io.IOException;
import java.util.StringJoiner;

public class BigramPrepReducer extends Reducer<Text, Text, Text, Text> {
    public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
        String[] parts = key.toString().split(",");
        if (parts.length != 2) return;

        StringJoiner sj = new StringJoiner(" ");
        for (Text val : values) sj.add(val.toString());

        context.write(new Text(parts[0]), new Text(parts[1] + "\t" + sj.toString()));
    }
}
