# Word-Based Sentiment Scoring and Trend Analysis on Historical Texts
Project Overview - This project processes historical texts from Project Gutenberg using Hadoop MapReduce and Hive. The goal is to clean the data, compute word frequencies, perform sentiment analysis, analyze trends over time, and extract bigrams using a Hive UDF.

Setup Instructions

Prerequisites

Ensure you have the following installed:
	•	Hadoop
	•	Hive
	•	Java 8 or later
	•	Maven
	•	NLTK (for stopwords & lemmatization)
	•	Sentiment Lexicons (AFINN or SentiWordNet)
 
Dataset

Download books from Project Gutenberg and store them in the data/ directory.
# Task 2

## Overview
The goal of this task is to compute word frequencies by splitting sentences into individual words and applying lemmatization to normalize variations. The output provides word frequencies per book ID and year, enabling historical text analysis.

## Input Dataset
The dataset consists of historical texts, where each line is formatted as:
```
<BookID>,<Year>\t<Sentence>
```
Example:
```
1,1885\tcut during certain region
10,1841\tdrop party politics none
100,1778\teach maybe method late
1000,1717\tnight control make look
```
Each record contains:
- `BookID`: Unique identifier for a book.
- `Year`: The year associated with the text.
- `Sentence`: A textual excerpt from the book.

## Implementation
This task is implemented using Java MapReduce with Stanford NLP for lemmatization.

### Mapper: `WordFrequencyMapper.java`
This component reads the dataset, tokenizes sentences into words, applies lemmatization, and emits `(bookID, year, lemma)` as the key with frequency as the value.

```java
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
        String text = parts[1];

        StringTokenizer tokenizer = new StringTokenizer(text);
        while (tokenizer.hasMoreTokens()) {
            String word = tokenizer.nextToken();
            // Lemmatize the word
            String lemma = new Sentence(word).lemma(0);
            // Emit (bookID, year, lemma) as key and 1 as value
            compositeKey.set(bookIDYear + "," + lemma);
            context.write(compositeKey, one);
        }
    }
}
```

### Reducer: `WordFrequencyReducer.java`
The reducer aggregates word counts for each `(bookID, year, lemma)` combination.

```java
package com.example.wordanalysis;

import java.io.IOException;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

public class WordFrequencyReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
    private IntWritable result = new IntWritable();

    public void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
        int sum = 0;
        for (IntWritable val : values) {
            sum += val.get();
        }
        result.set(sum);
        context.write(key, result);
    }
}
```

## Running the Job
### Compilation & Packaging
Ensure Hadoop and Stanford NLP dependencies are properly configured. Use Maven to compile and package the project:
```
mvn clean package
```

### Execution in Hadoop
1. Upload input data to HDFS:
```
hdfs dfs -put input_data.txt /input/docs
```
2. Run the MapReduce job:
```
hadoop jar word-analysis.jar com.example.wordanalysis.WordFrequencyDriver /input/docs /output
```
3. Retrieve the results:
```
hdfs dfs -cat /output/part-r-00000
```

## Output Format
The output contains the frequency of each lemmatized word per book and year:
```
<BookID>,<Year>,<Lemma>\t<Count>
```
Example:
```
1,1885,cut\t1
10,1841,drop\t1
100,1778,method\t1
1000,1717,control\t1
```


# Task 3

## Overview
Sentiment Scoring: The goal is to assign sentiment scores to individual words (lemmas) from the books using the AFINN lexicon.
Final Output: The task will produce a sentiment score per book and year, which can later be used for long-term trend analysis.

## Input Dataset
The input to Task 3 is the output from Task 2 (Word Frequency Analysis). The data is structured in a CSV-like format:

Example:
```
bookID,year,lemma,freq
1,1885,certain,1
1,1885,cut,1
1,1885,during,1
```
Where:

bookID: Unique identifier for each book.
year: The year the book was published.
lemma: The lemmatized version of the word.
freq: The frequency (how many times the word appears) in the book.

Additionally, the AFINN lexicon (AFINN-lexicon.txt) is required to assign sentiment scores to words. The lexicon has the following format:

```
love    3
hate    -2
joy     2
anger   -2
```

## Implementation
### SentimentScoreDriver.java:
The driver class sets up and runs the MapReduce job. It configures the input and output paths, specifies the Mapper (SentimentScoreMapper) and Reducer (SentimentScoreReducer), and submits the job to Hadoop for execution.

```
public static void main(String[] args) throws Exception {
    if (args.length != 2) {
        System.err.println("Usage: SentimentScoreDriver <input path> <output path>");
        System.exit(-1);
    }

    // Set up Hadoop job configuration
    Configuration conf = new Configuration();
    Job job = Job.getInstance(conf, "Sentiment Scoring");

    // Specify Mapper and Reducer
    job.setJarByClass(SentimentScoreDriver.class);
    job.setMapperClass(SentimentScoreMapper.class);
    job.setReducerClass(SentimentScoreReducer.class);

    // Set output key and value types
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(IntWritable.class);

    // Define input and output paths
    FileInputFormat.addInputPath(job, new Path(args[0]));
    FileOutputFormat.setOutputPath(job, new Path(args[1]));

    // Execute the job
    System.exit(job.waitForCompletion(true) ? 0 : 1);
}
```
Input/Output Paths: These are passed as command-line arguments when running the job.
Job Configuration: Sets up the Mapper and Reducer classes and output data types.
Running the Job: The job is submitted to Hadoop for execution, which processes the input data and produces the output.

### Mapper: `SentimentScoreMapper.java`
The mapper processes each input line, looks up the sentiment score for the word (lemma) from the AFINN lexicon, and calculates the total sentiment score for each word based on its frequency.

```
protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
    String[] parts = value.toString().split("\t");

    if (parts.length == 2) {
        String[] meta = parts[0].split(",");
        if (meta.length == 3) {
            String bookId = meta[0];
            String year = meta[1];
            String word = meta[2].trim().toLowerCase();
            int frequency = Integer.parseInt(parts[1].trim());

            // Fetch sentiment score for the word
            Integer sentimentScore = sentimentMap.get(word);

            if (sentimentScore != null) {
                int totalScore = sentimentScore * frequency;
                String outputKey = bookId + "," + year;
                context.write(new Text(outputKey), new IntWritable(totalScore));
            }
        }
    }
}
    
```
Parsing Input: The input is split into bookID, year, lemma, and frequency.
Lexicon Lookup: The word's sentiment score is looked up from the AFINN lexicon.
Output: The mapper emits the cumulative sentiment score for each (bookID, year).

### Reducer: `SentimentScoreReducer.java`
The reducer aggregates the sentiment scores for each book and year. It sums the individual sentiment scores from the mapper and outputs the final cumulative sentiment score.

```java
protected void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
    int sum = 0;
    for (IntWritable value : values) {
        sum += value.get();  // Sum all sentiment scores for each book/year
    }
    context.write(key, new IntWritable(sum));
}

```
Group by Key: The reducer receives all sentiment scores for a given (bookID, year).
Aggregation: It sums up the sentiment scores for each book and year.
Output: The final sentiment score for each book and year is emitted.

## Running the Job
### Compilation & Packaging
Ensure Hadoop and Stanford NLP dependencies are properly configured. Use Maven to compile and package the project:
```
mvn clean package
```

### Execution in Hadoop
1. Upload input data to HDFS:
```
hadoop fs -put input/task2_output.txt /input/task3/
hadoop fs -put input/AFINN-lexicon.txt /input/task3/

```
2. Run the MapReduce job:
```
hadoop jar sentiment-analysis-task3.jar com.example.wordanalysis.SentimentScoreDriver /input/task3/task2_output.txt /output/task3

```
3. Retrieve the results:
```
hadoop fs -cat /output/task3/part-r-00000
```
4. Download the output:
```
docker cp resourcemanager:/opt/hadoop-2.7.4/share/hadoop/mapreduce/output/task3 ./output/
```

## Output Format
```
bookID,year,total_sentiment_score
1,1885,12
2,1800,-5
3,1900,8
```
