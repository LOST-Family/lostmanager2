package lostmanager.commands.coc.util.jsonutils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import lostmanager.util.F2PCheckJsonCache;
import lostmanager.util.ImageMapCache;

public class F2PCheckAlgorithm {

    public static class CheckResult {
        private final boolean isF2P;
        private final String reason;

        public CheckResult(boolean isF2P, String reason) {
            this.isF2P = isF2P;
            this.reason = reason;
        }

        public boolean isF2P() {
            return isF2P;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * Checks if a player is F2P based on their data using default rules from cache.
     * 
     * @param playerData Map of Item ID -> Count
     * @return CheckResult containing success status and failure reason if any
     */
    public static CheckResult check(Map<String, Integer> playerData) {
        JSONObject rules = F2PCheckJsonCache.getFullMap();
        return check(rules, playerData);
    }

    /**
     * Checks if a player is F2P based on their data and provided rules.
     * 
     * @param rules      The JSONObject containing the rules
     * @param playerData Map of Item ID -> Count
     * @return CheckResult containing success status and failure reason if any
     */
    public static CheckResult check(JSONObject rules, Map<String, Integer> playerData) {
        if (rules == null) {
            return new CheckResult(true, "Internal Error: Could not load F2P rules.");
        }

        // 1. Check StrictForbidden
        if (rules.has("StrictForbidden")) {
            JSONArray strictForbidden = rules.getJSONArray("StrictForbidden");
            for (int i = 0; i < strictForbidden.length(); i++) {
                String forbiddenId = String.valueOf(strictForbidden.get(i));
                if (playerData.containsKey(forbiddenId) && playerData.get(forbiddenId) > 0) {
                    String itemName = getItemName(forbiddenId);
                    return new CheckResult(false, "Besitzt streng verbotenes Item: " + itemName);
                }
            }
        }

        // 2. Check Conditions
        if (rules.has("Conditions")) {
            JSONArray conditions = rules.getJSONArray("Conditions");
            for (int i = 0; i < conditions.length(); i++) {
                JSONObject condition = conditions.getJSONObject(i);
                CheckResult result = evaluateGroup(condition, playerData);
                if (!result.isF2P()) {
                    return result; // Propagate failure
                }
            }
        }

        return new CheckResult(true, null);
    }

    private static CheckResult evaluateGroup(JSONObject group, Map<String, Integer> playerData) {
        boolean isStrict = group.has("CasesStrict");
        String arrayKey = isStrict ? "CasesStrict" : "Cases";

        if (!group.has(arrayKey)) {
            // If neither exists, and it's just a container (e.g. from ThenForbidden), it
            // passes if empty?
            // Or maybe it has "Cases" inside "Cases"? The prompt says "A Cases Array is
            // built up of many Cases".
            // But valid recursion should generally find either Cases or CasesStrict or be a
            // leaf.
            // If empty, we assume safely passed.
            return new CheckResult(true, null);
        }

        JSONArray cases = group.getJSONArray(arrayKey);
        int trueCases = 0;
        List<String> reasons = new ArrayList<>();

        for (int i = 0; i < cases.length(); i++) {
            JSONObject caseObj = cases.getJSONObject(i);
            CheckResult caseResult = evaluateCase(caseObj, playerData);

            // If caseResult.isF2P() is FALSE, it means the case is FLAGGED/BAD (True
            // condition met)
            // The prompt says "Cases is true if 2 or more of its cases returns true".
            // My evaluateCase returns isF2P=false (Flagged) if FLAGGED.
            // So if (!caseResult.isF2P()) -> FLAGGED.

            if (!caseResult.isF2P()) {
                trueCases++;
                String reason = caseResult.getReason();
                if (reason != null && !reason.isEmpty()) {
                    reasons.add(reason);
                }
            }
        }

        boolean groupIsFlagged;
        if (isStrict) {
            // Strict: True if >= 1
            groupIsFlagged = trueCases >= 1;
        } else {
            // Normal: True if >= 2
            groupIsFlagged = trueCases >= 2;
        }

        if (groupIsFlagged) {
            return new CheckResult(false, "Verstoß gegen Regelgruppe (" + (isStrict ? "Strict" : "Normal") + "): "
                    + String.join(", ", reasons));
        }

        return new CheckResult(true, null);
    }

    // Returns CheckResult(false, reason) if FLAGGED, or CheckResult(true, null) if
    // SAFE
    private static CheckResult evaluateCase(JSONObject caseObj, Map<String, Integer> playerData) {
        if (!caseObj.has("IfMoreThan"))
            return new CheckResult(true, null); // Safe

        Object ifMoreThanObj = caseObj.get("IfMoreThan");
        boolean conditionMet = false;

        if (ifMoreThanObj instanceof JSONArray) {
            JSONArray arr = (JSONArray) ifMoreThanObj;
            for (int i = 0; i < arr.length(); i++) {
                if (checkIfMoreThan(arr.getJSONObject(i), playerData)) {
                    conditionMet = true;
                    break;
                }
            }
        } else {
            conditionMet = checkIfMoreThan((JSONObject) ifMoreThanObj, playerData);
        }

        if (!conditionMet) {
            return new CheckResult(true, null); // Case is Safe
        }

        // IfMoreThan is True. Check ThenForbidden.
        if (caseObj.has("ThenForbidden")) {
            JSONObject thenForbidden = caseObj.getJSONObject("ThenForbidden");

            if (thenForbidden.isEmpty()) {
                // If effective empty and IfMoreThan met -> FLAGGED
                // We need a reason here.
                String name = caseObj.optString("name", "Unbekannte Regel");
                return new CheckResult(false, name);
            }

            CheckResult res = evaluateGroup(thenForbidden, playerData);
            if (!res.isF2P()) {
                // Nested check failed -> FLAGGED
                // Wrap reason?
                String name = caseObj.optString("name", "Unbekannte Regel");
                return new CheckResult(false, name + " -> " + res.getReason());
            } else {
                return new CheckResult(true, null); // Nested check passed -> SAFE
            }
        }

        // No ThenForbidden, so it defaults to True (Flagged).
        String name = caseObj.optString("name", "Unbekannte Regel");
        return new CheckResult(false, name);
    }

    private static boolean checkIfMoreThan(JSONObject ifObj, Map<String, Integer> playerData) {
        String type = ifObj.getString("type"); // "quantity" or "count"
        int allowed = ifObj.getInt("allowed");
        JSONArray elements = ifObj.getJSONArray("elements");

        int userValue = 0;

        Set<String> uniqueIds = new HashSet<>();

        for (int i = 0; i < elements.length(); i++) {
            JSONObject el = elements.getJSONObject(i);
            String idStr = String.valueOf(el.get("id")); // Can be string "queen skin" or int

            if (idStr.isEmpty())
                continue; // Skip empty ids if any

            if (playerData.containsKey(idStr)) {
                int count = playerData.get(idStr);
                if (count > 0) {
                    if (type.equals("quantity")) {
                        // Only count unique elements logic?
                        // "quantity: only count unique elements the player has (1+1+1=3)" ??
                        // Wait. The prompt says: "quantity, only count unique elements the player has.
                        // (1+1+1=3)"
                        // This example 1+1+1=3 for unique implies if I have 3 DIFFERENT items, I get 3.
                        // If I have 3 of the SAME item... does it count as 1 or 3?
                        // "quantity" usually means distinct kinds.
                        // "count" usually means total sum.
                        // Prompt: "If this is quantity, only count unique elements the player has.
                        // (1+1+1=3)" -> This is confusing phrasing.
                        // "If this is count, count all elements the player has including the count
                        // (2+2+2=6)"
                        // Interpretation:
                        // Quantity = Count how many of the LISTED IDs the player possesses at least 1
                        // of.
                        // Count = Sum of amounts of the LISTED IDs the player possesses.

                        // Example 1+1+1=3 for quantity...
                        // Maybe 1xID_A + 1xID_B + 1xID_C = 3?

                        // Let's assume:
                        // Quantity: Sum (1 if hasItem else 0) for each ID in list?
                        // But if list has ID_A and user has 5 ID_A... is it 1?
                        // Likely yes.

                        uniqueIds.add(idStr); // Just to be safe if json lists same id twice?
                    } else {
                        // Count
                        userValue += count;
                    }
                }
            }
        }

        if (type.equals("quantity")) {
            // Recalculate based on unique IDs user has
            for (String id : uniqueIds) {
                if (playerData.containsKey(id) && playerData.get(id) > 0) {
                    userValue++;
                }
            }
        }

        int finalValue = userValue;
        return finalValue > allowed;
    }

    private static String getItemName(String dataId) {
        String name = ImageMapCache.getName(dataId);
        if (name != null)
            return name;
        return "Item " + dataId;
    }
}
