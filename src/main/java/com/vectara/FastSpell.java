package com.vectara;

import com.github.jfasttext.JFastText;
import dumonts.hunspell.Hunspell;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;


public class FastSpell {
    // Useful constants
    private final double THRESHOLD = 0.5;
    private final String PREFIX = "__label__";
    private final Pattern PUNCT_REGEX = Pattern.compile("\\p{Punct}+$|^\\p{Punct}+");

    // Configuration variables
    private String lang;
    private String mode;
    private String dictPath;
    private JFastText jft;
    private Map<String, String> configs;
    private Map<String, String> hunspellCodes;
    private Map<String, Hunspell> hunspellObjs;
    private Map<String, ArrayList<String>> similarLangs;
    private List<String[]> similar;


    /**
     * Constructor for FastSpell class
     */
    public FastSpell() {
        this.configs = loadConfigs("src/main/resources/config.json");   // Load configs
        this.lang = configs.get("lang");                                            // The default language code
        this.mode = configs.get("mode");                                            // "aggr" for aggressive or "cons" for conservative
        if (!(mode.equals("cons") || mode.equals("aggr"))) {
            throw new IllegalArgumentException("Unknown mode. Use 'aggr' for aggressive or 'cons' for conservative");
        }

        this.dictPath = "src/main/resources/fastspell_dictionaries";                // Dictionary path
        this.jft = new JFastText();
        this.jft.loadModel(configs.get("modelPath"));                               // Load FastText model
        this.similarLangs = loadSimilarLangs();                                     // Load similar languages
        this.hunspellCodes = loadHunspellCodes();                                   // Load Hunspell codes
        loadHunspellDicts();                                                        // Load Hunspell dictionaries
    }


    /**
     * Load the configuration file.
     *
     * @param configPath The path to the configuration file.
     * @return A map of the configuration file.
     */
    public Map loadConfigs(String configPath) {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, String>>() {}.getType();

        try (FileReader reader = new FileReader(configPath)) {
            Map<String, String> map = gson.fromJson(reader, type);
            return map;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * Load the similar languages file.
     *
     * @return A map of the similar languages file.
     */
    public Map loadSimilarLangs() {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, ArrayList<String>>>() {}.getType();

        try (FileReader reader = new FileReader(configs.get("similarLangsPath"))) {
            Map<String, ArrayList<String>> map = gson.fromJson(reader, type);
            return map;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * Load the Hunspell codes file.
     *
     * @return A map of the Hunspell codes file.
     */
    public Map loadHunspellCodes() {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, String>>() {}.getType();

        try (FileReader reader = new FileReader(configs.get("hunspellCodesPath"))) {
            Map<String, String> map = gson.fromJson(reader, type);
            return map;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * Prepare a map of Hunspell spellcheckers for all similar languages that can be mistaken with the target language.
     */
    private void loadHunspellDicts() {
        this.similar = new ArrayList<>();
        this.hunspellObjs = new HashMap<>();

        // Obtain all possible lists of similar languages for the target language
        for (String simEntry : similarLangs.keySet()) {
            if (simEntry.split("_")[0].equals(lang)) {
                ArrayList<String> simValues = similarLangs.get(simEntry);
                String[] simArray = simValues.toArray(new String[simValues.size()]);
                String[] extendedSimArray = new String[simArray.length + 1];
                System.arraycopy(simArray, 0, extendedSimArray, 0, simArray.length);
                extendedSimArray[simArray.length] = simEntry;
                similar.add(extendedSimArray);
            }
        }

        // Load Hunspell dictionaries for all similar languages
        for (String[] similarList : similar) {
            for (String l : similarList) {
                if (hunspellObjs.containsKey(l)) {
                    continue;   // Skip if already loaded
                }
                hunspellObjs.put(l, searchHunspellDict(hunspellCodes.get(l)));
            }
        }
    }


    /**
     * Search for a Hunspell dictionary for a given language code.
     *
     * @param langCode The language code for which to search the dictionary.
     * @return A Hunspell object for the given language code.
     */
    private Hunspell searchHunspellDict(String langCode) {
        Path dicPath = Paths.get(dictPath, langCode + ".dic");
        Path affPath = Paths.get(dictPath, langCode + ".aff");

        File dicFile = new File(dicPath.toString());
        File affFile = new File(affPath.toString());

        if (dicFile.exists() && affFile.exists()) {
            try {
                Hunspell hs = new Hunspell(dicPath, affPath);
                return hs;
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed building Hunspell object for " + langCode, e);
            }
        } else {
            throw new RuntimeException(String.format("It does not exist any valid dictionary directory for %s in %s. Please, execute 'fastspell-download'.", langCode, dictPath));
        }
    }


    /**
     * Get the language of a given sentence. (THE MOST IMPORTANT METHOD)
     *
     * @param sent The sentence for which to detect the language.
     * @return The language code of the sentence.
     */
    public String getLang(String sent) {
        // Predict the language using FastText
        sent = sent.replace("\n", " ").trim();
        String prediction = jft.predict(sent.toLowerCase(), 1).get(0).substring(PREFIX.length());

        // If prediction does not specify the variant, replace it by any of the variants to trigger hunspell refinement
        if ("no".equals(prediction) && !"no".equals(lang)) {
            prediction = "nb";
        }
        if ("sh".equals(prediction)) {
            prediction = "sr";
        }
        if ("he".equals(prediction) && "iw".equals(lang)) {
            prediction = "iw";
        }

        String refinedPrediction = prediction;
        // If language is not mistakeable, return FastText prediction
        if (similar.isEmpty() || !hunspellObjs.containsKey(prediction)) {
            refinedPrediction = prediction;
        } else {
            // Else, obtain the list of similar languages to spellcheck
            String[] currentSimilar = null;
            for (String[] simList : similar) {
                List<String> simListArr = Arrays.asList(simList);
                if (simListArr.contains(prediction)) {
                    currentSimilar = simList;
                    break;
                }
            }

            // Spellcheck the sentence for all similar languages
            Map<String, Double> spellchecked = new HashMap<>();
            for (String l : currentSimilar) {
                String decSent = new String(sent.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
                List<String> rawToks = Arrays.asList(sent.trim().split(" "));
                List<String> toks = removeUnwantedWords(rawToks, lang);
                List<Boolean> correctList = new ArrayList<>();
                for (String token : toks) {
                    try {
                        correctList.add(hunspellObjs.get(l).spell(token));
                    } catch (Exception ex) {
                        correctList.add(false);
                    }
                }
                long corrects = correctList.stream().filter(Boolean::booleanValue).count();
                double errorRate = corrects > 0 ? 1 - (double) corrects / toks.size() : 1;
                // If the error rate is below the threshold, add it to the list
                if (errorRate <= THRESHOLD) {
                    spellchecked.put(l, errorRate);
                }
            }

            // If there is any mistake, choose the best language
            if (!spellchecked.isEmpty()) {
                double bestValue = Collections.min(spellchecked.values());
                List<String> bestKeys = new ArrayList<>();
                for (Map.Entry<String, Double> entry : spellchecked.entrySet()) {
                    if (entry.getValue() == bestValue) {
                        bestKeys.add(entry.getKey());
                    }
                }
                // If there is only one best language, return it
                if (bestKeys.size() == 1) {
                    refinedPrediction = bestKeys.get(0);
                } else {
                    // Else, choose the best language based on the mode
                    if ("aggr".equals(mode)) {
                        // Aggressive mode: return the target language
                        if (bestKeys.contains(lang)) {
                            refinedPrediction = lang;
                        } else if (bestKeys.contains(prediction)) {
                            refinedPrediction = prediction;
                        } else {
                            refinedPrediction = bestKeys.get(0);
                        }
                    }
                    if ("cons".equals(mode)) {
                        // Conservative mode: return unknown if target isn't the best
                        if (bestKeys.contains(lang) && bestValue == 0) {
                            refinedPrediction = lang;
                        } else {
                            refinedPrediction = "unk";
                        }
                    }
                }
            } else {
                // If there is no mistake, return the prediction
                if ("aggr".equals(mode)) {
                    refinedPrediction = prediction;
                } else {
                    refinedPrediction = "unk";
                }
            }
        }

        return refinedPrediction;
    }


    /**
     * Remove punctuations and proper nouns from the list of tokens because Hunspell has a high error rate and focuses only on "normal" words.
     *
     * @param rawToksb The list of tokens to be cleaned.
     * @param lang The language code of the sentence.
     * @return The list of tokens without unwanted words.
     */
    private List<String> removeUnwantedWords(List<String> rawToks, String lang) {
        List<String> newTokens = new ArrayList<>();
        boolean isFirstToken = true;

        for (String token : rawToks) {
            token = PUNCT_REGEX.matcher(token.trim()).replaceAll("").trim(); // Regex to remove punctuation
            if ("de".equals(lang)) {
                if (token.chars().anyMatch(Character::isAlphabetic)) {
                    newTokens.add(token);
                }
            } else {
                if (token.chars().anyMatch(Character::isAlphabetic) && (isFirstToken || Character.isLowerCase(token.charAt(0)))) {
                    newTokens.add(token.toLowerCase());
                }
            }
            isFirstToken = false;
        }
        return newTokens;
    }


    // /**
    //  * Predict the language of a given sentence. (wrapper for getLang())
    //  *
    //  * @param sent The sentence for which to predict the language.
    //  * @return The language code of the sentence.
    //  */
    // public String predict(String sent) {
    //     return getLang(sent);
    // }


    /**
     * Main method for the FastSpell class.
     *
     * @param args The command line arguments.
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        FastSpell fs = new FastSpell();
        System.out.println(fs.getLang(args[0]));
    }
}