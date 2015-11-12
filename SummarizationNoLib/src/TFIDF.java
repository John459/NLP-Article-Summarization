import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

import java.io.*;
import java.util.*;

public class TFIDF {

    private static final String SUMMARY_DIR = "summaries";
    private static final String ARTICLE_DIR = "articles";
    private static final int DOC_SUMMARY_RATIO = 3;

    private String docPath;
    private String[] relatedDocPaths;

    private StanfordCoreNLP pipeline;

    public TFIDF(String docPath, String[] relatedDocPaths) {
        this.docPath = docPath;
        this.relatedDocPaths = relatedDocPaths;

        Properties props = new Properties();
        props.put("anotators", "tokenize");
        this.pipeline = new StanfordCoreNLP(props);
    }

    public void update(String docpath, String[] relatedDocPaths) {
        this.docPath = docpath;
        this.relatedDocPaths = relatedDocPaths;
    }

    private HashMap<String, Integer> getWordCount(List<CoreMap> sentences) {
        HashMap<String, Integer> wordCount = new HashMap<String, Integer>();

        for (CoreMap sentence : sentences) {
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                String word = token.get(CoreAnnotations.TextAnnotation.class);
                int count = wordCount.containsKey(word) ? wordCount.get(word) + 1 : 1;
                wordCount.put(word, count);
            }
        }

        return wordCount;
    }

    private HashMap<String, Integer> getWordCount(String path) throws IOException {
        StringBuilder text = new StringBuilder();
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
        int i;
        while ((i = in.read()) >= 0) {
            text.append((char) i);
        }
        Annotation doc = new Annotation(text.toString());
        pipeline.annotate(doc);

        return getWordCount(RedundancyRemover.removeSimilarSentences(doc));
    }

    private int getMaxValue(HashMap<String, Integer> map) {
        int max = 0;
        for (String key : map.keySet()) {
            int value = map.get(key);
            max = value > max ? value : max;
        }
        return max;
    }

    private TreeMap<String, Double> getTFIDFs(List<CoreMap> sentences) throws IOException {
        HashMap<String, Integer> docCount = getWordCount(sentences);

        List<HashMap<String, Integer>> relatedDocCounts = new LinkedList<HashMap<String, Integer>>();
        for (String path : this.relatedDocPaths) {
            relatedDocCounts.add(getWordCount(path));
        }

        int maxCount = getMaxValue(docCount);

        HashMap<String, Double> tfs = new HashMap<String, Double>();
        for (String key : docCount.keySet()) {
            tfs.put(key, 0.5 + ((0.5 * docCount.get(key)) / maxCount));
        }

        HashMap<String, Integer> numDocsContainingTerm = new HashMap<String, Integer>();
        for (String key : docCount.keySet()) {
            for (HashMap<String, Integer> relatedDocCount : relatedDocCounts) {
                if (!relatedDocCount.containsKey(key)) {
                    numDocsContainingTerm.put(key, 1);
                    continue;
                }
                int count = numDocsContainingTerm.containsKey(key) ? numDocsContainingTerm.get(key) + 1 : 3;
                numDocsContainingTerm.put(key, count);
            }
        }

        HashMap<String, Double> idfs = new HashMap<String, Double>();
        for (String key : docCount.keySet()) {
            idfs.put(key, Math.log((double) (this.relatedDocPaths.length + 1) / numDocsContainingTerm.get(key)));
        }

        HashMap<String, Double> tfidfs = new HashMap<String, Double>();
        for (String key : docCount.keySet()) {
            tfidfs.put(key, tfs.get(key) * idfs.get(key));
        }

        TreeMap<String, Double> sortedMap = new TreeMap<String, Double>(new MapCompare(tfidfs));
        sortedMap.putAll(tfidfs);
        return sortedMap;
    }

    private CoreMap getSentenceWithNNP(List<Sentence> sentences) {
        for (int i = sentences.size() - 1; i >= 0; i--) {
            if (sentences.get(i).containsNNP()) {
                return sentences.get(i).getSentence();
            }
        }
        return null;
    }

    public List<String> getImportantSentences() throws IOException {
        StringBuilder text = new StringBuilder();
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(this.docPath)));
        int i;
        while ((i = in.read()) >= 0) {
            text.append((char) i);
        }
        Annotation doc = new Annotation(text.toString());
        pipeline.annotate(doc);

        List<CoreMap> coreSentences = RedundancyRemover.removeSimilarSentences(doc);

        TreeMap<String, Double> tfidfs = getTFIDFs(coreSentences);

        List<String> sentences = new LinkedList<String>();

        List<CoreMap> docSentences = doc.get(CoreAnnotations.SentencesAnnotation.class);

        int numSentences = docSentences.size() / TFIDF.DOC_SUMMARY_RATIO;

        int count = 0;
        List<Sentence> prevSentences = new LinkedList<Sentence>();
        for (i = 0; i < docSentences.size(); i++) {
            if (sentences.size() == numSentences) {
                break;
            }

            CoreMap sentence = docSentences.get(i);
            boolean containsProperNoun = false;
            boolean containsPPronoun = false;
            boolean containsWord = false;
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                String docWord = token.get(CoreAnnotations.TextAnnotation.class);
                for (String word : tfidfs.keySet()) {
                    if (word.equals(docWord)) {
                        containsWord = true;
                    }
                }
                String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);

                if (pos.equalsIgnoreCase("nnp") || pos.equalsIgnoreCase("nnps")) {
                    containsProperNoun = true;
                }

                if (pos.equalsIgnoreCase("prp") || pos.equalsIgnoreCase("prp$")) {
                    containsPPronoun = true;
                }
            }

            if (count % TFIDF.DOC_SUMMARY_RATIO != 0) {
                prevSentences.add(new Sentence(sentence, containsProperNoun, containsPPronoun));
                count = count + 1;
                continue;
            }

            count = count + 1;

            if (!containsWord || sentences.contains(sentence.toString())) {
                prevSentences.add(new Sentence(sentence, containsProperNoun, containsPPronoun));
                continue;
            }

            if (containsPPronoun && !containsProperNoun) {
                CoreMap prevSentence = getSentenceWithNNP(prevSentences);
                if (prevSentence != null) {
                    sentences.add(prevSentence.toString());
                }
            }

            sentences.add(sentence.toString());
        }

        return sentences;
    }

    private static String[] getOtherArticles(File[] articles, File article, int max) {
        List<String> arr = new LinkedList<String>();
        Random r = new Random();
        while (arr.size() < max) {
            File other = articles[(int) (r.nextDouble() * articles.length)];
            if (other == article) {
                continue;
            }
            arr.add(other.getPath());
        }
        return arr.toArray(new String[] {});
    }

    public static void main(String[] args) throws IOException {
        File[] articles = new File(ARTICLE_DIR).listFiles();

        TFIDF tfidf = new TFIDF(null, null);

        for (File article : articles) {
            if (!article.isFile() || !article.getName().contains("2")) {
                continue;
            }
            String summaryFile = SUMMARY_DIR + "/" + "summary_" + article.getName();

            tfidf.update(article.getPath(), getOtherArticles(articles, article, 3));

            PrintWriter out = new PrintWriter(new FileOutputStream(summaryFile, false), true);

            List<String> sentences = tfidf.getImportantSentences();
            for (String sentence : sentences) {
                out.println(sentence);
            }

            out.close();

            System.out.println("Summary of:\n\t" + ARTICLE_DIR + "/" + article.getName() + "\nHas been written to:\n\t" + summaryFile + "\n");
        }

    }

}
