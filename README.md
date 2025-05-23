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

# Task 1

## Overview 
The goal of this task is to extract and clean raw text data from historical books (from sources like Project Gutenberg) and standardize it. The output of this task is cleaned text lines tagged with metadata (book ID and year) for each sentence or line in the text. This output is used in subsequent tasks like word frequency analysis and sentiment scoring.

## Input Dataset 
Each line of the input data contains metadata and a raw text sentence
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
- `Title`: A textual excerpt from the book.

## Implementation 
This task is implemented using Java MapReduce

### Mapper: `TextpreprocessingMapper.java`
This component reads the dataset

```java
package com.example.preprocessing;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public class PreprocessingMapper extends Mapper<LongWritable, Text, Text, Text> {
    private Set<String> stopWords = new HashSet<>();
    private static final Pattern PUNCTUATION = Pattern.compile("\\p{Punct}");

    @Override
    protected void setup(Context context) throws IOException {
        // Load stopwords from local resource or distributed cache
        InputStream in = getClass().getClassLoader().getResourceAsStream("stopwords.txt");
        if (in != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                stopWords.add(line.trim().toLowerCase());
            }
        }
    }

    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        String[] parts = value.toString().split("\t");
        if (parts.length != 2) return;

        String metadata = parts[0]; // "bookID,year"
        String text = parts[1].toLowerCase();
        text = PUNCTUATION.matcher(text).replaceAll(""); // remove punctuation

        StringBuilder cleaned = new StringBuilder();
        for (String token : text.split("\\s+")) {
            if (!stopWords.contains(token) && !token.isBlank()) {
                cleaned.append(token).append(" ");
            }
        }

        context.write(new Text(metadata), new Text(cleaned.toString().trim()));
    }
}
```
### Reducer: `TextpreprocessingReducer.java`

```java
package com.example.preprocessing;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

public class PreprocessingReducer extends Reducer<Text, Text, Text, Text> {
    @Override
    protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
        for (Text cleanedSentence : values) {
            context.write(key, cleanedSentence);
        }
    }
}
```

### Driver: `Textpreprocessingdriver.java`

```java
package com.example.preprocessing;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class PreprocessingDriver {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: PreprocessingDriver <input path> <output path>");
            System.exit(-1);
        }

        Job job = Job.getInstance(new Configuration(), "Data Preprocessing");
        job.setJarByClass(PreprocessingDriver.class);

        job.setMapperClass(PreprocessingMapper.class);
        job.setReducerClass(PreprocessingReducer.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        System.exit(job.waitForCompletion(true) ? 0 : 1);
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

## Output 
```
<BookID>,<Year>    <Cleaned Sentence>
```
Example:
```
1,1885    quick brown fox jumps lazy dog
2,1800    hope thing feathers
```




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
- `Title`: A textual excerpt from the book.

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

```java
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

```java
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

# Task 4

## Overview
The goal of this task is to analyze and extract named entities (NER) from historical texts, categorizing words into entities like person names, locations, organizations, and dates. The output provides named entity counts per Book ID and Year, enabling historical analysis of important references in different time periods.

## Input Dataset
Each line in the input dataset follows this structure:
```
<BookID>,<Year>,<Lemma>    <Count>
```
Example:
```
1,1885,cut    10
10,1841,drop    7
100,1778,method    12
1000,1717,control    8
5,1923,hope    15
8,1825,happy    9
2,1865,angry    -5
```
## Sample Output 

```
1,1880s,cut	10
10,1840s,drop	7
100,1770s,method	12
1000,1710s,control	8
1880s,cut	30
1840s,drop	20
1770s,method	45
1710s,control	25
```

Each record contains:
- `BookID`: Unique identifier for a book.
- `Year`: The year associated with the text.
- `Sentence`: A textual excerpt from the book.

# Implementation
This task is implemented using Java MapReduce with Stanford NLP for lemmatization.

### Mapper: `TrendAnalysisMapper.java`
This component reads the dataset, tokenizes sentences into words, applies lemmatization, and emits `(bookID, year, lemma)` as the key with frequency as the value.This mapper takes the sentiment scores and word frequency data from previous tasks and maps individual years to their corresponding decades while preserving the book ID for book-level analysis.

```java
package com.example.trendanalysis;

import java.io.IOException;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

public class TrendAnalysisMapper extends Mapper<Object, Text, Text, IntWritable> {
    private Text compositeKey = new Text();
    private IntWritable valueOut = new IntWritable();

    public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
        String[] parts = value.toString().split("\t");
        if (parts.length < 2) return;

        String[] keyParts = parts[0].split(",");
        if (keyParts.length < 3) return;

        String bookID = keyParts[0];  // Extract BookID
        int year = Integer.parseInt(keyParts[1]);  // Extract Year
        String wordOrSentiment = keyParts[2]; // Extract word or sentiment

        int decade = (year / 10) * 10;  // Convert year to decade (e.g., 1823 → 1820s)

        int count = Integer.parseInt(parts[1]);  // Extract frequency/sentiment score

        // Emit (BookID, Decade) for book-level trend analysis
        compositeKey.set(bookID + "," + decade + "," + wordOrSentiment);
        valueOut.set(count);
        context.write(compositeKey, valueOut);

        // Emit (Decade) for overall trend analysis
        compositeKey.set(decade + "," + wordOrSentiment);
        context.write(compositeKey, valueOut);
    }
}
```

### Reducer: `TrendAnalysisReducer.java`
This reducer aggregates word frequencies and sentiment scores per decade and per book ID.

```java
package com.example.trendanalysis;

import java.io.IOException;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

public class TrendAnalysisReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
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

# Task 5

## Overview
This task extracts and analyzes **bigrams** (pairs of consecutive words) from lemmatized historical text using a custom **Hive User-Defined Function (UDF)**. The aim is to uncover co-occurrence patterns in language across books and years.

## Step 1: Input Preparation (MapReduce)
We used `BigramPrepMapper`, `BigramPrepReducer`, and `BigramPrepDriver` to format the output from **Task 2** into lemmatized lines. Each line includes:
```
bookID<TAB>year<TAB>lemmatized text
```
This structured output was saved and used as input for Hive bigram analysis.

## Step 2: Custom Hive UDF – `BigramExtractorUDF`

The `BigramExtractorUDF` takes a lemmatized sentence and returns a list of bigrams (two-word combinations).

**Example:**
- **Input:** `"the king walk slowly"`
- **Output:** `["the king", "king walk", "walk slowly"]`

### Hive UDF Logic (Simplified)
```java
public List<String> evaluate(String input) {
    if (input == null || input.isEmpty()) return Collections.emptyList();
    String[] words = input.trim().split("\s+");
    List<String> bigrams = new ArrayList<>();
    for (int i = 0; i < words.length - 1; i++) {
        bigrams.add(words[i] + " " + words[i + 1]);
    }
    return bigrams;
}
```

## Hive Queries Used

### Load the JAR and Register UDF
```sql
ADD JAR /root/bigram-udf.jar;
CREATE TEMPORARY FUNCTION extract_bigrams AS 'com.example.wordanalysis.BigramExtractorUDF';
```

### Create Input Table
```sql
CREATE TABLE IF NOT EXISTS bigram_input (
    book_id STRING,
    year INT,
    text STRING
)
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '\t'
STORED AS TEXTFILE;
```

### Load Input Data
```sql
LOAD DATA INPATH '/user/hadoop/input/bigram_input/part-r-00000'
OVERWRITE INTO TABLE bigram_input;
```

### Create Output Table
```sql
CREATE TABLE bigram_output (
    bigram STRING,
    freq INT
)
STORED AS TEXTFILE;
```

### Extract and Aggregate Bigrams
```sql
INSERT OVERWRITE TABLE bigram_output
SELECT bigram, COUNT(*) AS freq
FROM (
  SELECT explode(extract_bigrams(text)) AS bigram
  FROM bigram_input
) tmp
GROUP BY bigram;
```

## Output
The final output was downloaded from Hive and saved to:
```
output5/task5_output/task5_output.txt
```
Each line contains a bigram and its frequency, separated by a tab.


## Running the Job

### Compilation & Packaging
Ensure your UDF class is included in the project. Then, compile and package the project using Maven:
```
mvn clean package
```

### Upload the Formatted Input to HDFS
```
hdfs dfs -mkdir -p /user/hadoop/input/bigram_input
hdfs dfs -put bigram_input/part-r-00000 /user/hadoop/input/bigram_input/
```

### Start Hive and Run Queries

1. Launch Hive:
```
hive
```

2. Load the JAR and Register UDF:
```sql
ADD JAR /root/bigram-udf.jar;
CREATE TEMPORARY FUNCTION extract_bigrams AS 'com.example.wordanalysis.BigramExtractorUDF';
```

3. Create Input Table:
```sql
CREATE TABLE IF NOT EXISTS bigram_input (
    book_id STRING,
    year INT,
    text STRING
)
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '\t'
STORED AS TEXTFILE;
```

4. Load Data into Table:
```sql
LOAD DATA INPATH '/user/hadoop/input/bigram_input/part-r-00000'
OVERWRITE INTO TABLE bigram_input;
```

5. Create Output Table:
```sql
CREATE TABLE bigram_output (
    bigram STRING,
    freq INT
)
STORED AS TEXTFILE;
```

6. Run Bigram Extraction Query:
```sql
INSERT OVERWRITE TABLE bigram_output
SELECT bigram, COUNT(*) AS freq
FROM (
  SELECT explode(extract_bigrams(text)) AS bigram
  FROM bigram_input
) tmp
GROUP BY bigram;
```

### Retrieve the Output from HDFS
```
hdfs dfs -get /user/hive/warehouse/bigram_output/000000_0 output5/task5_output/task5_output.txt
```
