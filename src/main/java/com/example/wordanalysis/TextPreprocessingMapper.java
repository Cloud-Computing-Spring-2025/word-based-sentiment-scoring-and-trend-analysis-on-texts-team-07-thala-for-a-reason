package com.example.wordanalysis;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

public class TextPreprocessingMapper extends Mapper<LongWritable, Text, Text, Text> {

    @Override
    protected void map(LongWritable key, Text value, Context context) 
            throws IOException, InterruptedException {

        // Split input line by comma (BookID, BookName, PublicationYear)
        String[] fields = value.toString().split(",", -1);

        if (fields.length == 3) {
            String bookID = fields[0].trim();
            String bookName = fields[1].trim().toLowerCase().replaceAll("[^a-zA-Z0-9\\s]", "");
            String publicationYear = fields[2].trim();

            // Emit (bookID, year) as key and cleaned book name as value
            context.write(new Text(bookID + "," + publicationYear), new Text(bookName));
        }
    }
}