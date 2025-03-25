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
This task focuses on word frequency analysis with lemmatization using Apache Hadoop MapReduce. The goal is to process historical text data, extract word occurrences, and apply lemmatization to normalize words before aggregating their frequencies. This is Task 2 in a larger project on sentiment scoring and trend analysis.

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
This component reads the dataset, tokenizes sentences into words, applies lemmatization, and emits `(bookID, year, lemma)` as the key with `1` as the value.

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

