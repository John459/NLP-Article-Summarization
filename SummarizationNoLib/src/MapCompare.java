import java.util.Comparator;
import java.util.Map;

/**
 * Created by john on 12/3/2014.
 */
public class MapCompare implements Comparator<String> {

    private Map<String, Double> map;

    public MapCompare(Map<String, Double> map) {
        this.map = map;
    }

    public int compare(String x, String y) {
        return map.get(x) > map.get(y) ? -1 : 1;
    }

}
