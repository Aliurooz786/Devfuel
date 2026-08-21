package com.devfuel.weight;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic body-weight extractor. No clock, no LLM.
 * Requires a weight cue ({@code weight}/{@code vajan}/{@code wajan}/{@code kg}); never a bare number.
 */
public class WeightParser {

    private static final Pattern WEIGHT_THEN_NUMBER = Pattern.compile(
            "\\b(weight|vajan|wajan|wt)\\b\\s*(?:is|hai|tha|thi|the|:|=)?\\s*(\\d{1,3}(?:\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern NUMBER_THEN_WEIGHT = Pattern.compile(
            "(\\d{1,3}(?:\\.\\d{1,2})?)\\s*(?:kgs?\\s*)?(?:hai\\s+|tha\\s+|thi\\s+)?\\b(weight|vajan|wajan|wt)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern NUMBER_THEN_KG = Pattern.compile(
            "(\\d{1,3}(?:\\.\\d{1,2})?)\\s*kgs?\\b(?:\\s+(\\p{L}+))?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LB = Pattern.compile("\\b(lbs?|pounds?)\\b", Pattern.CASE_INSENSITIVE);

    private static final Set<String> KG_TRAILING_OK = Set.of(
            "hai", "tha", "thi", "the", "ho", "gaya", "gayi", "aaj", "kal", "parso",
            "today", "yesterday", "only", "hoga", "hogaya"
    );

    private static final Set<String> FOOD_WORDS = Set.of(
            "rice", "chawal", "paneer", "chicken", "dal", "daal", "roti", "bread", "atta",
            "flour", "aloo", "potato", "potatoes", "samosa", "samosas", "milk", "doodh",
            "ghee", "oil", "sugar", "cheeni", "meat", "mutton", "beef", "fish", "egg",
            "eggs", "whey", "protein", "sabzi", "biryani", "tahri", "fruit", "fruits",
            "veg", "vegetable", "vegetables", "anda", "gosht"
    );

    public Optional<WeightValue> parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Optional.empty();
        }
        String text = rawText.trim();
        if (LB.matcher(text).find()) {
            return Optional.empty();
        }

        Optional<WeightValue> fromWeightWord = firstInRange(WEIGHT_THEN_NUMBER, text, 2);
        if (fromWeightWord.isPresent()) {
            return fromWeightWord;
        }
        Optional<WeightValue> fromNumberThenWeight = firstInRange(NUMBER_THEN_WEIGHT, text, 1);
        if (fromNumberThenWeight.isPresent()) {
            return fromNumberThenWeight;
        }
        return parseKilogramQuantity(text);
    }

    private Optional<WeightValue> parseKilogramQuantity(String text) {
        if (containsFoodWord(text)) {
            return Optional.empty();
        }
        Matcher matcher = NUMBER_THEN_KG.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String trailing = matcher.group(2);
        if (trailing != null && !KG_TRAILING_OK.contains(trailing.toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        return WeightValue.ofKilograms(matcher.group(1));
    }

    private static Optional<WeightValue> firstInRange(Pattern pattern, String text, int numberGroup) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            Optional<WeightValue> value = WeightValue.ofKilograms(matcher.group(numberGroup));
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private static boolean containsFoodWord(String text) {
        String[] tokens = text.toLowerCase(Locale.ROOT).split("[^\\p{L}]+");
        for (String token : tokens) {
            if (FOOD_WORDS.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
