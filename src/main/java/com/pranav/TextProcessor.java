package com.pranav;
import com.fasterxml.jackson.databind.ObjectMapper;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.tokenize.SimpleTokenizer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

public class TextProcessor {
//    private static final Logger log = LoggerFactory.getLogger(KafkaConsumer.class);
//    private static final Logger log = LoggerFactory.getLogger(KafkaProducer.class);
    public DBConn conn = DBConn.getInstance();
    public KafkaConsumer<String, String> consumer;
    public KafkaProducer<String,String> producer;
    private static final Set<String> STOP_WORDS = Set.of(
            "the","is","are","was","were","to","of","in","on","for","with",
            "as","by","an","a","at","from","that","this","it","be","or",
            "and","not","but","if","then","so","than","too","very","can",
            "will","just","into","about","over","after","before","between"
    );

    private final PorterStemmer stemmer = new PorterStemmer();
    private final SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;
    public List<String> tokenize(String text){
        String[] tokens = tokenizer.tokenize(text);
        List<String> validToken = new ArrayList<>();
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
            validToken.add(stemmed);
        }
        return validToken;
    }

    public void process(String text,String url,String title) {
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

        conn.ingestData(termFrequency,url,title,count);
        return ;
    }

    public void setKafkConsumer(){
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("group.id", "wiki-processors"); // Unique ID for this consumer group
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("TEXT_PROCESSING_MOCK"));

        Properties propsProducer = new Properties();
        propsProducer.put("bootstrap.servers","localhost:9092");
        propsProducer.put("key.serializer","org.apache.kafka.common.serialization.StringSerializer");
        propsProducer.put("value.serializer","org.apache.kafka.common.serialization.StringSerializer");

        producer = new KafkaProducer<>(propsProducer);
    }

    public void recieveData(){
        ObjectMapper mapper = new ObjectMapper();

        try {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
                    // Convert JSON string back to WebData object
                    WebData data = mapper.readValue(record.value(), WebData.class);
                    System.out.println("Title: "+data.title);
                    System.out.println("Processing URL: " + data.url);
                    System.out.println("Content: " + data.text.substring(1,15));
                    process(data.text,data.url,data.title);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendData(){
        ObjectMapper mapper = new ObjectMapper();
        try{

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void main() {
        TextProcessor textProcessor = new TextProcessor();
        textProcessor.setKafkConsumer();
        textProcessor.recieveData();

    }
}
