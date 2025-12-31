package com.pranav;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.*;

import static com.mongodb.client.model.Filters.*;

public class DBConn {
    public static MongoClient mongoClient;
    public static void createConn(){
        String uri = "mongodb://localhost:27017/";
        mongoClient = MongoClients.create(uri);
    }
    public static void ingestData(Map<String,TermFrequencyValue> terms,String url,int count){
        MongoDatabase database = mongoClient.getDatabase("crawler");
        database.createCollection("urls");

        MongoCollection<Document> urlCollection = database.getCollection("urls");
        Document urlDoc = new Document()
                .append("_id",new ObjectId())
                        .append("url",url);
        urlCollection.insertOne(urlDoc);
        database.createCollection("indexes");

        MongoCollection<Document> collection = database.getCollection("indexes");

        Iterator<Map.Entry<String,TermFrequencyValue>> iterator = terms.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<String, TermFrequencyValue> entry = iterator.next();
            String term = entry.getKey();
            TermFrequencyValue value = entry.getValue();
            Document toFind = collection.find(eq("_id",term)).first();
            if(toFind == null){
                List<Document> postings = new ArrayList<>();
                Document posting = new Document()
                        .append("urlId",urlDoc.get("_id"))
                        .append("freq",value.frequency)
                        .append("positions",value.positions)
                        .append("tc",count);
                postings.add(posting);
                Document newDoc = new Document()
                        .append("_id",term)
                        .append("df",1)
                        .append("cf",value.frequency)
                        .append("postings",postings);
                collection.insertOne(newDoc);
            }
            else{
                Document posting = new Document()
                        .append("urlId",urlDoc.get("_id"))
                        .append("freq",value.frequency)
                        .append("positions",value.positions)
                        .append("tc",count);
                collection.updateOne(
                        Filters.eq("_id", term),
                        Updates.combine(
                                Updates.push("postings", posting),
                                Updates.inc("df", 1),
                                Updates.inc("cf", posting.getInteger("freq"))
                        )
                );

            }
        }
    }
    public static Set<String> loadVisitedUrl(){
        MongoDatabase database = mongoClient.getDatabase("crawler");
        MongoCollection<Document> urls = database.getCollection("urls");
        List<Document> result = new ArrayList<>();
        urls.find().into(result);
        Set<String> hs = new HashSet<>();
        for(Document d : result){
            hs.add(d.get("url",String.class));
        }
        return hs;
    }
    static void main() {
        createConn();
        MongoDatabase database = mongoClient.getDatabase("crawler");
        MongoCollection<Document> collection = database.getCollection("indexes");
        MongoCollection<Document> urls = database.getCollection("urls");
        long n = urls.countDocuments();
//        List<Document> result = new ArrayList<>();

        Map<ObjectId,Double> hm = new HashMap<>();
        String term = "softwar";
        Document document = collection.find(eq("_id",term)).first();
        List<Document> postings = document.get("postings",List.class);
        for(int i=0;i<postings.size();i++){
            Document posting = postings.get(i);
            int tc = posting.get("tc",Integer.class);
            int freq = posting.get("freq",Integer.class); //
            float tf = (float)freq/tc;
            int df = document.get("df",Integer.class);
            double idf = Math.log((double)n/df);
            double score = tf*idf;
//            ObjectId id = posting.get("urlId",ObjectId.class);
//            System.out.println(id);
            hm.put(posting.getObjectId("urlId"),score);
        }

         term = "world";
         document = collection.find(eq("_id",term)).first();
         postings = document.get("postings",List.class);
        for(int i=0;i<postings.size();i++){
            Document posting = postings.get(i);
            int tc = posting.get("tc",Integer.class);
            int freq = posting.get("freq",Integer.class);
            float tf = (float)freq/tc;
            int df = document.get("df",Integer.class);
            double idf = Math.log((double)n/df);
            double score = tf*idf;
            ObjectId id = posting.getObjectId("urlId");
            hm.put(id,hm.getOrDefault(id,0D)+score);
        }
        List<Map.Entry<ObjectId, Double>> ranked =
                hm.entrySet()
                        .stream()
                        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                        .toList();
        System.out.println(ranked.size());
        for (int i = 0; i < ranked.size(); i++) {
            System.out.println(ranked.get(i).getValue());
            System.out.println(ranked.get(i).getKey());

            System.out.println(urls.find(eq("_id",ranked.get(i).getKey())).first().get("url",String.class));
        }
    }


}
