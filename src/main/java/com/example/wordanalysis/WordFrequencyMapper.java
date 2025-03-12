package com.example.wordanalysis;

import java.io.IOException;
import java.util.StringTokenizer;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import edu.stanford.nlp.simple.Sentence;

public class WordFrequencyMapper extends Mapper<Object, Text, Text, IntWritable> {
    private final static IntWritable one = new IntWritable(1);
    private Text compositeKey = new Text();

    public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
        String[] parts = value.toString().split("\t");
        if (parts.length < 2) return;

        String bookIDYear = parts[0]; // e.g., "1,1885"
        String text = parts[1]; // e.g., "cut during certain region"

        StringTokenizer tokenizer = new StringTokenizer(text);
        while (tokenizer.hasMoreTokens()) {
            String word = tokenizer.nextToken();
            // Lemmatize the word
            String lemma = new Sentence(word).lemma(0);
            // Emit (bookID, year, lemma) as key and 1 as value
            compositeKey.set(bookIDYear + "," + lemma); // e.g., "1,1885,cut"
            context.write(compositeKey, one);
        }
    }
}