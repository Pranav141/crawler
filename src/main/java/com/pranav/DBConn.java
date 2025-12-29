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

import java.util.Arrays;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;

public class DBConn {
    public static MongoClient mongoClient;
    public static void createConn(){
        String uri = "mongodb://localhost:27017/";
        mongoClient = MongoClients.create(uri);

    }
    static void main() {
        MongoDatabase database = mongoClient.getDatabase("crawler");
        database.createCollection("indexes");
        MongoCollection<Document> collection = database.getCollection("indexes");
        Document newDoc = new Document()
                .append("positions",Arrays.asList(1,2,3,4))
                        .append("freq",4)
                                .append("url","wiki1");
//        List<Document> postings = List.of(newDoc);
//        collection.insertOne(new Document()
//                .append("_id", "term")
//                .append("df", 1)
//                        .append("cf",4)
//                        .append("postings",postings));
//        Document d = collection.find(eq("_id","term")).first();


        collection.updateOne(
                Filters.eq("_id", "term"),
                Updates.combine(
                        Updates.push("postings", newDoc),
                        Updates.inc("df", 1),
                        Updates.inc("cf", newDoc.getInteger("freq"))
                )
        );

//        d.put("df",df);
//        d.put("cf",cf);
//        d.put("postings",postings);


    }


}
