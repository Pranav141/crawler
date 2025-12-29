package com.pranav;

import java.util.ArrayList;
import java.util.List;

public class TermFrequencyValue {
    public String url;
    public List<Integer> positions;
    public int frequency;

    public TermFrequencyValue(String url) {
        this.url = url;
        this.positions = new ArrayList<>();
        this.frequency = 0;
    }

}
