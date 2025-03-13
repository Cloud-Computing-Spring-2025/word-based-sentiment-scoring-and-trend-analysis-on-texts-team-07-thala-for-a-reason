package com.example.wordanalysis;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.HashMap;

public class SentimentScoreMapper extends Mapper<LongWritable, Text, Text, IntWritable> {

    private HashMap<String, Integer> sentimentMap = new HashMap<>();

    @Override
    protected void setup(Context context) throws IOException {
        // Load sentiment lexicon from HDFS
        Path lexiconPath = new Path("/input/task3/AFINN-lexicon.txt");  // HDFS Path
        FileSystem fs = FileSystem.get(context.getConfiguration());

        try (FSDataInputStream in = fs.open(lexiconPath);
             BufferedReader br = new BufferedReader(new InputStreamReader(in))) {

            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length == 2) {
                    sentimentMap.put(parts[0], Integer.parseInt(parts[1]));
                }
            }
        }
    }

   @Override
protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
    // Split by comma for the first three fields, then by tab for the frequency
    String[] parts = value.toString().split("\t");

    if (parts.length == 2) {
        String[] meta = parts[0].split(",");
        if (meta.length == 3) {
            String bookId = meta[0];
            String year = meta[1];
            String word = meta[2].trim();
            int frequency = Integer.parseInt(parts[1].trim());

            Integer sentimentScore = sentimentMap.get(word.toLowerCase());
            if (sentimentScore != null) {
                int totalScore = sentimentScore * frequency;
                String outputKey = bookId + "," + year;
                context.write(new Text(outputKey), new IntWritable(totalScore));
            } else {
                System.out.println("DEBUG: Word not found in lexicon - " + word);
            }
        } else {
            System.out.println("DEBUG: Malformed meta data - " + value.toString());
        }
    } else {
        System.out.println("DEBUG: Malformed line - " + value.toString());
    }
}

}
