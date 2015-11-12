import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Created by john on 12/9/2014.
 */
public class RedundancyRemover {

    private static final String[] STOP_WORDS = {"a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "has",
            "he", "in", "is", "it", "its", "of", "on", "that", "the", "to", "was", "were", "will", "with"};

    private static final double MAX_PERCENT_SIMILAR = 0.3;

    private static boolean isStopWord(String word) {
        for (String stopWord : STOP_WORDS) {
            if (word.equalsIgnoreCase(stopWord)) {
                return true;
            }
        }
        return false;
    }

    private static double getSimilarity(CoreMap sentence, CoreMap sentence2) {
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        List<CoreLabel> tokens2 = sentence2.get(CoreAnnotations.TokensAnnotation.class);

        int matches = 0;
        int stops = 0;
        boolean stopChecking = false;
        for (CoreLabel token : tokens) {
            String word = token.get(CoreAnnotations.TextAnnotation.class);
            if (isStopWord(word)) {
                stops++;
                continue;
            }
            for (CoreLabel token2 : tokens2) {
                String word2 = token2.get(CoreAnnotations.TextAnnotation.class);
                if (isStopWord(word2) && !stopChecking) {
                    stops++;
                    continue;
                }
                if (word.equalsIgnoreCase(word2) || word2.contains(word) || word.contains(word2)) {
                    matches = matches + 1;
                }
            }
            stopChecking = true;
        }

        /*if(sentence.toString().contains("Boulder police") && sentence2.toString().contains("Boulder police")) {
            System.out.println(sentence.toString() + "\n" + sentence2.toString() + "\n" + matches + "\n" + stops +
            "\n" + tokens.size() + "\n" + tokens2.size() + "\n");
        }*/

        /*int i = 0;
        int j = 0;
        while (i < tokens.size() && j < tokens2.size()) {
            String word = tokens.get(i).get(CoreAnnotations.TextAnnotation.class);
            String word2 = tokens2.get(j).get(CoreAnnotations.TextAnnotation.class);

            if (isStopWord(word)) {
                i++;
                stops++;
                continue;
            }
            if (isStopWord(word2)) {
                j++;
                stops++;
                continue;
            }
            if (word.equalsIgnoreCase(word2) || word2.contains(word) || word.contains(word2)) {
                matches = matches + 1;
            }
            i++;
            j++;
        }*/

        return (double) (2 * matches) / (tokens.size() + tokens2.size() - stops);
    }

    public static List<CoreMap> removeSimilarSentences(Annotation doc) {
        List<CoreMap> diverseSentenceList = new LinkedList<CoreMap>();

        List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
        for (CoreMap sentence : sentences) {
            for (CoreMap sentence2 : sentences) {
                if (sentence == sentence2) {
                    continue;
                }

                double similarity = getSimilarity(sentence, sentence2);
                if (similarity > MAX_PERCENT_SIMILAR) {
                    if (!diverseSentenceList.contains(sentence) && !diverseSentenceList.contains(sentence2)) {
                        System.out.println(sentence.toString());
                        System.out.println(sentence2.toString());
                        diverseSentenceList.add(sentence.toString().length() > sentence2.toString().length() ? sentence : sentence2);
                    }
                    continue;
                }

                if (!diverseSentenceList.contains(sentence)) {
                    diverseSentenceList.add(sentence);
                }
                if (!diverseSentenceList.contains(sentence2)) {
                    diverseSentenceList.add(sentence2);
                }
            }
        }

        return diverseSentenceList;
    }

}
