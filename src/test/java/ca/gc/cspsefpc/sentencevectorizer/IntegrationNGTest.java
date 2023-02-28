/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/EmptyTestNGTest.java to edit this template
 */
package ca.gc.cspsefpc.sentencevectorizer;

import java.io.File;
import java.util.Collection;
import org.deeplearning4j.models.word2vec.Word2Vec;
import static org.testng.Assert.*;

/**
 *
 * @author JTurner
 */
public class IntegrationNGTest {

    public IntegrationNGTest() {
    }

    /**
     * Test of buildModel method, of class SentenceVectorizerTrainer.
     */
    @org.testng.annotations.Test
    public void testCycle() throws Exception {
        System.out.println("Integration Test");
        String[] fileSpec = new String[]{new File(this.getClass().getResource("/corpus.txt").toURI()).getAbsolutePath()};
        int numOccurrences = 3;
        int numFeatures = 200;
        int windowSize = 5;
        SentenceVectorizerTrainer instance = new SentenceVectorizerTrainer();
        Word2Vec buildModel = instance.buildModel(fileSpec, numOccurrences, numFeatures, windowSize);
        Collection<String> wordsNearest = buildModel.wordsNearest("creative", 10);
        assertTrue(wordsNearest.contains("mountie"));
    }

}
