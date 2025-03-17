package com.example.wordanalysis;


import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.Mapper;
import java.io.IOException;

public class TrendAnalysisMapper extends Mapper<LongWritable, Text, Text, IntWritable> {
    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        // Check if the line contains a tab (for Task 3 Sentiment Score)
        if (value.toString().contains("\t")) {
            String[] fields = value.toString().split("\t"); // Split on tab
            
            if (fields.length == 2) {
                try {
                    String[] bookYear = fields[0].split(","); // Split BookID and Year by comma
                    if (bookYear.length == 2) {
                        int year = Integer.parseInt(bookYear[1].trim()); // Extract Year
                        int sentimentScore = Integer.parseInt(fields[1].trim()); // Extract Sentiment Score

                        // Convert year to decade
                        int decade = (year / 10) * 10;
                        String decadeKey = "SentimentScore_Decade_" + decade;

                        context.write(new Text(decadeKey), new IntWritable(sentimentScore));
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Invalid Task 3 Data: " + value.toString());
                }
            }
        } 
        // Task 2: Word Frequency Data (BookID, Year, Word, Count) -> Comma-separated
        else {
            String[] fields = value.toString().split(",");

            if (fields.length == 4) {
                try {
                    int year = Integer.parseInt(fields[1].trim()); // Extract Year
                    int count = Integer.parseInt(fields[3].trim()); // Extract Word Count

                    // Convert year to decade
                    int decade = (year / 10) * 10;
                    String decadeKey = "WordFrequency_Decade_" + decade;

                    context.write(new Text(decadeKey), new IntWritable(count));
                } catch (NumberFormatException e) {
                    System.err.println("Invalid Task 2 Data: " + value.toString());
                }
            }
        }
    }
}
