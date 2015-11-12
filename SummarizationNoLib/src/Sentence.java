import edu.stanford.nlp.util.CoreMap;

/**
 * Created by john on 12/16/2014.
 */
public class Sentence {

    private CoreMap sentence;
    private boolean nnp;
    private boolean prp;

    public Sentence(CoreMap sentence, boolean nnp, boolean prp) {
        this.sentence = sentence;
        this.nnp = nnp;
        this.prp = prp;
    }

    public CoreMap getSentence() {
        return sentence;
    }

    public boolean containsNNP() {
        return nnp;
    }

    public boolean containsPRP() {
        return prp;
    }

}
