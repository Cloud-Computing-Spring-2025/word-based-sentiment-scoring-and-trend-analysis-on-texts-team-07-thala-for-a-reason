# Word-Based Sentiment Scoring and Trend Analysis on Historical Texts
Project Overview - This project processes historical texts from Project Gutenberg using Hadoop MapReduce and Hive. The goal is to clean the data, compute word frequencies, perform sentiment analysis, analyze trends over time, and extract bigrams using a Hive UDF.
📂 project_root/
├── 📂 data/                 # Raw dataset (books in text format)
├── 📂 scripts/              # Bash scripts for running jobs
├── 📂 src/                  # Java source code for MapReduce jobs
│   ├── Preprocessing.java   # Task 1: Data Cleaning
│   ├── WordFrequency.java   # Task 2: Word Frequency & Lemmatization
│   ├── SentimentAnalysis.java # Task 3: Sentiment Scoring
│   ├── TrendAnalysis.java   # Task 4: Trend Aggregation
│   ├── BigramUDF.java       # Task 5: Hive UDF for Bigram Analysis
├── 📂 hive/                 # Hive-related scripts
│   ├── create_table.hql     # Creates Hive tables
│   ├── load_data.hql        # Loads data into Hive
│   ├── bigram_query.hql     # Runs bigram analysis
├── 📜 README.md             # Setup and execution guide
└── 📜 report.pdf            # Documentation and insights
