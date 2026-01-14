package com.pranav;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;

public class BM25SearchEngine {
    public MongoCollection<Document> collection;
    public MongoCollection<Document> urls;
    private TextProcessor textProcessor;

    // BM25 parameters
    private static final double K1 = 1.5;  // Term frequency saturation
    private static final double B = 0.75;  // Document length normalization

    // Boost parameters
    private static final double PROXIMITY_WEIGHT = 0.2;   // 20% weight to proximity
    private static final double POSITION_WEIGHT = 0.1;    // 10% weight to position
    private static final double BM25_WEIGHT = 0.7;        // 70% weight to BM25

    public BM25SearchEngine(MongoCollection<Document> collection, MongoCollection<Document> urls, TextProcessor textProcessor) {
        this.collection = collection;
        this.urls = urls;
        this.textProcessor = textProcessor;
    }

    public List<SearchResult> search(String query) {
        // Tokenize query
        List<String> queryTerms = textProcessor.tokenize(query);

        // Get total documents and average doc length
        long totalDocs = urls.countDocuments();
        double avgDocLength = getAverageDocumentLength();

        // Store scores for each document
        Map<ObjectId, DocumentScore> docScores = new HashMap<>();

        // For each query term
        for (int queryPos = 0; queryPos < queryTerms.size(); queryPos++) {
            String term = queryTerms.get(queryPos);

            // Get postings for this term
            Document termDoc = collection.find(eq("_id", term)).first();
            if (termDoc == null) continue;

            int df = termDoc.getInteger("df"); // document frequency
            List<Document> postings = termDoc.getList("postings", Document.class);

            // Calculate IDF (with BM25 variant)
            double idf = Math.log((totalDocs - df + 0.5) / (df + 0.5) + 1.0);

            // Process each document containing this term
            for (Document posting : postings) {
                ObjectId urlId = posting.getObjectId("urlId");
                int freq = posting.getInteger("freq");
                int docLength = posting.getInteger("tc");
                List<Integer> positions = posting.getList("positions", Integer.class);

                // Initialize document score if not exists
                docScores.putIfAbsent(urlId, new DocumentScore(urlId));
                DocumentScore docScore = docScores.get(urlId);

                // 1. Calculate BM25 score
                double bm25 = calculateBM25(freq, docLength, avgDocLength, idf);
                docScore.addBM25Score(bm25);

                // 2. Store positions for proximity calculation
                docScore.addTermPositions(queryPos, positions);

                // 3. Calculate position boost (early occurrence boost)
                double positionBoost = calculatePositionBoost(positions, docLength);
                docScore.addPositionBoost(positionBoost);
            }
        }

        // Calculate proximity boost for documents with multiple query terms
        for (DocumentScore docScore : docScores.values()) {
            if (queryTerms.size() > 1) {
                double proximityBoost = calculateProximityBoost(
                        docScore.termPositions,
                        queryTerms.size()
                );
                docScore.setProximityBoost(proximityBoost);
            }
        }

        // Combine all scores and rank
        List<SearchResult> results = docScores.values().stream()
                .map(ds -> {
                    double finalScore =
                            BM25_WEIGHT * ds.bm25Score +
                                    PROXIMITY_WEIGHT * ds.proximityBoost +
                                    POSITION_WEIGHT * ds.positionBoost;

                    return new SearchResult(ds.urlId, finalScore, ds);
                })
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .collect(Collectors.toList());

        return results;
    }

    /**
     * Calculate BM25 score for a term in a document
     */
    private double calculateBM25(int freq, int docLength, double avgDocLength, double idf) {
        // BM25 formula:
        // IDF * (freq * (k1 + 1)) / (freq + k1 * (1 - b + b * (docLength / avgDocLength)))

        double numerator = freq * (K1 + 1);
        double denominator = freq + K1 * (1 - B + B * (docLength / avgDocLength));

        return idf * (numerator / denominator);
    }

    /**
     * Calculate proximity boost based on how close query terms appear
     */
    private double calculateProximityBoost(Map<Integer, List<Integer>> termPositions,
                                           int numQueryTerms) {
        if (termPositions.size() < 2) {
            return 0.0; // Need at least 2 terms for proximity
        }

        double maxProximityScore = 0.0;

        // Get all term positions sorted by query order
        List<List<Integer>> allPositions = new ArrayList<>();
        for (int i = 0; i < numQueryTerms; i++) {
            if (termPositions.containsKey(i)) {
                allPositions.add(termPositions.get(i));
            }
        }

        if (allPositions.size() < 2) return 0.0;

        // Find minimum span containing all query terms
        int minSpan = Integer.MAX_VALUE;

        // For each position of first term
        for (int pos1 : allPositions.get(0)) {
            // Find closest positions of other terms
            int maxDistance = 0;
            boolean foundAll = true;

            for (int i = 1; i < allPositions.size(); i++) {
                // Find closest position in this term's list
                int closestDist = findClosestDistance(pos1, allPositions.get(i));
                if (closestDist == Integer.MAX_VALUE) {
                    foundAll = false;
                    break;
                }
                maxDistance = Math.max(maxDistance, Math.abs(closestDist));
            }

            if (foundAll && maxDistance < minSpan) {
                minSpan = maxDistance;
            }
        }

        if (minSpan == Integer.MAX_VALUE) {
            return 0.0;
        }

        // Convert span to score (closer = higher score)
        // Perfect match (adjacent words) = 1.0
        // Words 10 positions apart = ~0.5
        // Words 50+ positions apart = ~0.1
        double proximityScore = 1.0 / (1.0 + Math.log(minSpan + 1));

        return proximityScore;
    }

    /**
     * Find closest distance from target to any position in list
     */
    private int findClosestDistance(int target, List<Integer> positions) {
        int minDist = Integer.MAX_VALUE;
        for (int pos : positions) {
            minDist = Math.min(minDist, Math.abs(pos - target));
        }
        return minDist;
    }

    /**
     * Calculate position boost (early occurrences get higher scores)
     */
    private double calculatePositionBoost(List<Integer> positions, int docLength) {
        if (positions.isEmpty()) return 0.0;

        // Take the earliest position
        int earliestPos = positions.stream().min(Integer::compare).orElse(docLength);

        // Boost formula: terms in first 10% of doc get max boost
        // Terms at end get minimal boost
        double relativePosition = (double) earliestPos / docLength;

        // Exponential decay: first 10% = 1.0, middle = 0.5, end = 0.1
        return Math.exp(-3 * relativePosition);
    }

    /**
     * Calculate average document length
     */
    private double getAverageDocumentLength() {
        long totalWords = 0;
        long docCount = 0;

        for (Document doc : urls.find()) {
            totalWords += doc.getInteger("tc", 0);
            docCount++;
        }

        return docCount > 0 ? (double) totalWords / docCount : 0;
    }

    /**
     * Helper class to accumulate scores for a document
     */
    static class DocumentScore {
        ObjectId urlId;
        double bm25Score = 0.0;
        double proximityBoost = 0.0;
        double positionBoost = 0.0;
        Map<Integer, List<Integer>> termPositions = new HashMap<>(); // queryPos -> positions

        DocumentScore(ObjectId urlId) {
            this.urlId = urlId;
        }

        void addBM25Score(double score) {
            this.bm25Score += score;
        }

        void addTermPositions(int queryPos, List<Integer> positions) {
            this.termPositions.put(queryPos, positions);
        }

        void addPositionBoost(double boost) {
            this.positionBoost = Math.max(this.positionBoost, boost);
        }

        void setProximityBoost(double boost) {
            this.proximityBoost = boost;
        }
    }

    /**
     * Search result with URL and score
     */
    static class SearchResult {
        ObjectId urlId;
        double score;
        DocumentScore details;

        SearchResult(ObjectId urlId, double score, DocumentScore details) {
            this.urlId = urlId;
            this.score = score;
            this.details = details;
        }

        @Override
        public String toString() {
            return String.format("URL: %s, Score: %.4f (BM25: %.4f, Proximity: %.4f, Position: %.4f)",
                    urlId, score,
                    details.bm25Score, details.proximityBoost, details.positionBoost);
        }
    }
}
