package com.pranav;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.tokenize.SimpleTokenizer;

import java.util.*;

public class TextProcessor {

    private static final Set<String> STOP_WORDS = Set.of(
            "the","is","are","was","were","to","of","in","on","for","with",
            "as","by","an","a","at","from","that","this","it","be","or",
            "and","not","but","if","then","so","than","too","very","can",
            "will","just","into","about","over","after","before","between"
    );

    private final PorterStemmer stemmer = new PorterStemmer();
    private final SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;
    public void process(String text,String url) {
        int count = 0;
        Map<String,TermFrequencyValue> termFrequency = new HashMap<>();
        if (text == null || text.isEmpty()) {
            return ;
        }

        /*
        *
        * url , list of positions, frequency in that document,
        *
        * */
        // 1. Tokenization (split on non-alphanumeric)
        String[] tokens = tokenizer.tokenize(text);

        for (int i=0;i<tokens.length;i++) {

            String token = tokens[i];
            // 2. Normalization
            token = token.toLowerCase();

            // 3. Remove short tokens
            if (token.length() < 2) continue;


            // 4. Stopword removal
            if (STOP_WORDS.contains(token)) continue;

            // 5. Stemming
            String stemmed = stemmer.stem(token);

            if (stemmed.isEmpty()) continue;

            // 6. Term Frequency
            if(termFrequency.containsKey(stemmed)){
                //update if present
                TermFrequencyValue value = termFrequency.get(stemmed);
                value.frequency = value.frequency+1;
                value.positions.add(i);
                termFrequency.put(stemmed,value);
            }
            else{
                //create if not present
                TermFrequencyValue value = new TermFrequencyValue(url);
                value.frequency = 1;
                value.positions.add(i);
                termFrequency.put(stemmed,value);
            }
            count+=1;
        }
        DBConn.ingestData(termFrequency,url,count);
        return ;
    }
}
