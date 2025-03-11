package com.example.wordanalysis;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class TextPreprocessingDriver {

    public static void main(String[] args) throws Exception {
        System.out.println("Number of arguments: " + args.length);
        for (int i = 0; i < args.length; i++) {
            System.out.println("Argument " + i + ": " + args[i]);
        }

        // Check if exactly 2 arguments are provided
        if (args.length != 2) {
            System.err.println("Usage: TextPreprocessingDriver <input path> <output path>");
            System.exit(-1);
        }

        String inputPath = args[0].trim();
        String outputPath = args[1].trim();

        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "Text Preprocessing");

        job.setJarByClass(TextPreprocessingDriver.class);
        job.setMapperClass(TextPreprocessingMapper.class);
        job.setReducerClass(TextPreprocessingReducer.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job, new Path(inputPath));
        FileOutputFormat.setOutputPath(job, new Path(outputPath));

        boolean jobCompletionStatus = job.waitForCompletion(true);
        System.exit(jobCompletionStatus ? 0 : 1);
    }
}