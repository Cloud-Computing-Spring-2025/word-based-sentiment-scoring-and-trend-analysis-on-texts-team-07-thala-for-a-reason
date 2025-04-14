package com.example.wordanalysis;

import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.Mapper;
import java.io.IOException;

public class BigramPrepMapper extends Mapper<LongWritable, Text, Text, Text> {
    public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        String[] parts = value.toString().split("\t");
        if (parts.length != 2) return;

        String[] meta = parts[0].split(",");
        if (meta.length != 3) return;

        context.write(new Text(meta[0] + "," + meta[1]), new Text(meta[2]));
    }
}
