# vectorization-service
Java-based tooling for both creating and using term vectorizations in a simple way

# Getting Started
The vectorization service is a Java 17 application that uses Maven for build and dependency management. You will need:
1.	A Java Development Kit -- we recommend Adoptium.
2.	Apache Maven -- many IDEs already bundle Maven.

## Build and Test
To build and test SentenceVectorizer, ensure that both Java and Maven are installed and on your path, and type:
```
mvn clean install
``` 
To build a monolithic JAR suitable for deployment :
```
mvn clean install shade:shade
``` 
If you want to build the full project site, use:
```
mvn clean install site
``` 
# Training a model
To use the trainer, you must first prepare a training corpus. This is composed 
of a dirctory with one or more UTF-8 text files that will be separated into sentences at both sentence 
punctuation and line breaks. Once your corpus has been prepared, you can run the trainer with :
```
java -jar SentenceVectorizer-1.0-SNAPSHOT-bin.jar build -d "./path/to/save/models" -s "./path/to/training/corpus" -l "en_us"
```
...specifying your text locale in place of "en_us". The `-s` parameter can be 
specified several times if you want to train on more than one directory tree. 
The training process is fairly CPU intensive, and takes 10-20 minutes on a 
typical laptop.

When finished, the trained model will be saved with the locale name as the 
filename, and ".word2vec" as the suffix.

For details of other command line options, invoke with no parameters:
```
java -jar SentenceVectorizer-1.0-SNAPSHOT-bin.jar
```
# Serving a model
To launch an HTTP API server, invoke with:
```
java -jar SentenceVectorizer-1.0-SNAPSHOT-bin.jar service -p 8080 -d "./path/to/saved/models"
```
...replacing 8080 with the port on which you want to listen for connections, and the value of -d with a path containinig .word2vec files. 

To invoke the API, you can connect to two endpoints:
```
/{locale}/vectorize?text=QueryText
```
Which will yield a JSON array of vectors that embed the meaning of the provided query text. Query text is expected to be a sentence or short phrase.

The second endpoint is:
```
/{locale}/nearest/{term}
```
Which will yield the ten nearest terms to the provided term as a JSON array.

The first call for a given locale triggers the locale to be loaded. This can take several seconds. Concurrent requests will block until loading is complete, after which calles can be made in parallel.

Test corpus courtesy of:

D. Goldhahn, T. Eckart & U. Quasthoff: Building Large Monolingual Dictionaries at the Leipzig Corpora Collection: From 100 to 200 Languages.
In: Proceedings of the 8th International Language Resources and Evaluation (LREC'12), 2012
