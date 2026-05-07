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
        private final List<String> reasons;

        public CheckResult(boolean isF2P, List<String> reasons) {
            this.isF2P = isF2P;
            this.reasons = reasons != null ? reasons : new ArrayList<>();
        }

        public CheckResult(boolean isF2P, String reason) {
            this.isF2P = isF2P;
            this.reasons = new ArrayList<>();
            if (reason != null && !reason.isEmpty()) {
                this.reasons.add(reason);
            }
        }

        public boolean isF2P() {
            return isF2P;
        }

        public String getReason() {
            return String.join("\n", reasons);
        }

        public List<String> getReasons() {
            return reasons;
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
        System.out.println("DEBUG: Starting F2P check with " + playerData.size() + " items.");
        if (rules == null) {
            return new CheckResult(false, "Internal Error: Could not load F2P rules.");
        }

        List<String> allReasons = new ArrayList<>();
        boolean isF2P = true;

        // 1. Check StrictForbidden
        if (rules.has("StrictForbidden")) {
            JSONArray strictForbidden = rules.getJSONArray("StrictForbidden");
            for (int i = 0; i < strictForbidden.length(); i++) {
                String forbiddenId = String.valueOf(strictForbidden.get(i));
                if (playerData.containsKey(forbiddenId) && playerData.get(forbiddenId) > 0) {
                    String itemName = getItemName(forbiddenId);
                    allReasons.add("Besitzt streng verbotenes Item: " + itemName);
                    isF2P = false;
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
                    allReasons.addAll(result.getReasons());
                    isF2P = false;
                }
            }
        }

        return new CheckResult(isF2P, allReasons);
    }

    private static CheckResult evaluateGroup(JSONObject group, Map<String, Integer> playerData) {
        boolean isStrict = group.has("CasesStrict");
        String key = isStrict ? "CasesStrict" : "Cases";

        if (!group.has(key)) {
            return new CheckResult(true, (List<String>) null);
        }

        Object val = group.get(key);
        String groupName = null;
        if (group.has("name")) {
            groupName = group.getString("name");
        }
        JSONArray cases = null;

        if (val instanceof JSONObject) {
            JSONObject valObj = (JSONObject) val;
            return evaluateGroup(valObj, playerData);
        } else if (val instanceof JSONArray) {
            cases = (JSONArray) val;
        } else {
            return new CheckResult(true, (List<String>) null);
        }

        String logName = (groupName != null ? groupName : key);
        System.out.println("DEBUG: Evaluating Group (" + logName + ")");

        int trueCases = 0;
        List<String> reasons = new ArrayList<>();

        for (int i = 0; i < cases.length(); i++) {
            JSONObject caseObj = cases.getJSONObject(i);
            System.out.println("DEBUG: Processing Case index " + i + " in " + logName);
            CheckResult caseResult = evaluateCase(caseObj, playerData);

            if (!caseResult.isF2P()) {
                trueCases++;
                reasons.addAll(caseResult.getReasons());
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
            System.out
                    .println("DEBUG: Group FLAGGED (Failed F2P check). Strict=" + isStrict + " TrueCases=" + trueCases);
            String label = (groupName != null && !groupName.isEmpty()) ? groupName : (isStrict ? "Strict" : "Normal");
            String combinedReason = "Verstoß gegen Regelgruppe (" + label + "): " + String.join(", ", reasons);
            return new CheckResult(false, combinedReason);
        }

        System.out.println("DEBUG: Group PASSED (Safe). Strict=" + isStrict + " TrueCases=" + trueCases);
        return new CheckResult(true, (List<String>) null);
    }

    // Returns CheckResult(false, reason) if FLAGGED, or CheckResult(true, null) if
    // SAFE
    private static CheckResult evaluateCase(JSONObject caseObj, Map<String, Integer> playerData) {
        String debugName = caseObj.optString("name", "Unknown Case");
        System.out.println("DEBUG: Start Case: " + debugName);
        if (!caseObj.has("IfMoreThan"))
            return new CheckResult(true, (List<String>) null); // Safe

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
            return new CheckResult(true, (List<String>) null); // Case is Safe
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
                return new CheckResult(true, (List<String>) null); // Nested check passed -> SAFE
            }
        }

        // No ThenForbidden, so it defaults to True (Flagged).
        String name = caseObj.optString("name", "Unbekannte Regel");
        return new CheckResult(false, name);
    }

    private static boolean checkIfMoreThan(JSONObject ifObj, Map<String, Integer> playerData) {
        String type = ifObj.getString("type"); // "quantity" or "count"
        int allowed = ifObj.getInt("allowed");
        System.out.println("DEBUG: CheckIfMoreThan type=" + type + " allowed=" + allowed);
        JSONArray elements = ifObj.getJSONArray("elements");

        int userValue = 0;

        Set<String> uniqueIds = new HashSet<>();

        for (int i = 0; i < elements.length(); i++) {
            JSONObject el = elements.getJSONObject(i);
            String idStr = String.valueOf(el.get("id")); // Can be string "queen skin" or int
            String name = el.optString("name", "Unknown Item");

            if (idStr.isEmpty())
                continue; // Skip empty ids if any

            if (playerData.containsKey(idStr)) {
                int count = playerData.get(idStr);
                if (count > 0) {
                    System.out.println("DEBUG:   -> Found " + name + " (ID: " + idStr + ") Count: " + count);
                    if (type.equals("quantity")) {
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
        System.out
                .println("DEBUG: FinalValue=" + finalValue + " > Allowed=" + allowed + " -> " + (finalValue > allowed));
        return finalValue > allowed;
    }

    private static String getItemName(String dataId) {
        String name = ImageMapCache.getName(dataId);
        if (name != null)
            return name;
        return "Item " + dataId;
    }
}
