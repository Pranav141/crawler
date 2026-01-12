package com.pranav;

import org.bson.Document;

import java.util.List;

import static com.mongodb.client.model.Filters.eq;

public class SearchDemo {
    public static void main(String[] args) {
        DBConn dbConn = DBConn.getInstance();
        BM25SearchEngine searchEngine = new BM25SearchEngine(
                dbConn.mongoClient.getDatabase("crawler").getCollection("indexes"),
                dbConn.mongoClient.getDatabase("crawler").getCollection("urls"),
                new TextProcessor()
        );

        // Search
        String query = "romeo and Juliet";
        List<BM25SearchEngine.SearchResult> results = searchEngine.search(query);

        // Display results
        System.out.println("Top 10 results for: \"" + query + "\"");
        System.out.println("=" .repeat(80));

        for (int i = 0; i < Math.min(10, results.size()); i++) {
            BM25SearchEngine.SearchResult result = results.get(i);

            // Fetch URL from database
            Document urlDoc = searchEngine.urls.find(eq("_id", result.urlId)).first();
            String url = urlDoc.getString("url");

            System.out.printf("\n%d. %s\n", i + 1, url);
            System.out.printf("   Total Score: %.4f\n", result.score);
            System.out.printf("   - BM25: %.4f\n", result.details.bm25Score);
            System.out.printf("   - Proximity: %.4f\n", result.details.proximityBoost);
            System.out.printf("   - Position: %.4f\n", result.details.positionBoost);
        }
    }
}
