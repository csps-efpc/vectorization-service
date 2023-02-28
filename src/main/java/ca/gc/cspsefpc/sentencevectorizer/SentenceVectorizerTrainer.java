/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package ca.gc.cspsefpc.sentencevectorizer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.IOUtils;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

/**
 * Class encapsulating the functionality necessary to train an embedding model from a text corpus
 * @author JTurner
 */
public class SentenceVectorizerTrainer {

    public void trainModel(int numOccurrences, int numFeatures, int windowSize, String[] fileSpec, String workingDir, String locale) throws IOException {
        System.out.println("Training locale " + locale + " from corpus in " + Arrays.deepToString(fileSpec) + " for addition to " + workingDir);
        String outputPath = workingDir + File.separator + locale + ".word2vec";
        
        Word2Vec vec = buildModel(fileSpec, numOccurrences, numFeatures, windowSize);

        WordVectorSerializer.writeWord2VecModel(vec, outputPath);
        
        vec = WordVectorSerializer.readWord2VecModel(outputPath, true);
        Logger.getLogger(SentenceVectorizerTrainer.class.getName()).log(Level.INFO, "Basic term tests:");
        for (String word : Arrays.asList("phone", "money", "computer", "deck", "regulation", "minister")) {
            Logger.getLogger(SentenceVectorizerTrainer.class.getName()).log(Level.INFO, "Closest words to \"{0}\":", word);
            Collection<String> lst = vec.wordsNearest(word, 10);
            System.out.println(lst);
        }
        Logger.getLogger(SentenceVectorizerTrainer.class.getName()).log(Level.INFO, "Non-English term tests:");
        for (String word : Arrays.asList("rue", "marché", "pays", "soulier")) {
            Logger.getLogger(SentenceVectorizerTrainer.class.getName()).log(Level.INFO, "Closest words to \"{0}\":", word);
            Collection<String> lst = vec.wordsNearest(word, 10);
            System.out.println(lst);
        }
    }

    public Word2Vec buildModel(String[] fileSpec, int numOccurrences, int numFeatures, int windowSize) throws IOException {
        // The file is a unique temp file, so we can do this safely on Windows in spite of the partially-broken MMIO on the platform.
        DB db = DBMaker.tempFileDB().concurrencyDisable().fileMmapEnable().fileMmapPreclearDisable().fileDeleteAfterClose().make();
        // Something to watch out for is endpoint security agents on Windows that lock MMIO storage at random points (when the 
        final NavigableSet<String> sentences = db.treeSet("sentences", Serializer.STRING).createOrOpen();
        for (String file : fileSpec) {
            Files.walk(Paths.get(file)).filter(Files::isRegularFile).filter(p -> {
                return p.toString().endsWith(".txt");
            }).forEach((Path p) -> {
                System.out.println(p.toString());
                try {
                    String body = IOUtils.toString(p.toUri(), "UTF-8");
                    for (String sentence : body.split("[!\\.?\"\\:][ \\t\\n]")) {
                        sentence = sentence.replaceAll("[^\\p{L}]+", " ");
                        sentence = sentence.replaceAll(" +", " ");
                        sentences.add(sentence.toLowerCase());
                    }
                } catch (IOException ex) {
                    Logger.getLogger(SentenceVectorizerTrainer.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        }
        SentenceIterator iter = new SentenceIterator() {
            Iterator<String> iter = sentences.iterator();
            SentencePreProcessor preProcessor = null;

            @Override
            public String nextSentence() {
                if (preProcessor == null) {
                    return iter.next();
                }
                return preProcessor.preProcess(iter.next());
            }

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public void reset() {
                iter = sentences.iterator();
            }

            @Override
            public void finish() {
                // NO-OP
            }

            @Override
            public SentencePreProcessor getPreProcessor() {
                return preProcessor;
            }

            @Override
            public void setPreProcessor(SentencePreProcessor spp) {
                preProcessor = spp;
            }
        };
        TokenizerFactory t = new DefaultTokenizerFactory();
        t.setTokenPreProcessor(new CommonPreprocessor());
        Logger.getLogger(SentenceVectorizerTrainer.class.getName()).log(Level.INFO, "Building model....");
        Word2Vec vec = new Word2Vec.Builder()
                .minWordFrequency(numOccurrences)
                .layerSize(numFeatures)
                .windowSize(windowSize)
                .seed(42L)
                .iterate(iter)
                .tokenizerFactory(t)
                .build();
        Logger.getLogger(SentenceVectorizerTrainer.class.getName()).log(Level.INFO, "Fitting Word2Vec model....");
        vec.fit();
        return vec;
    }

}
