package com.pranav;



import opennlp.tools.langdetect.Language;
import opennlp.tools.langdetect.LanguageDetector;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.langdetect.LanguageDetectorModel;

import java.io.IOException;
import java.io.InputStream;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    static void main() throws IOException {
        //TIP Press <shortcut actionId="ShowIntentionActions"/> with your caret at the highlighted text
        // to see how IntelliJ IDEA suggests fixing it.
//    Crawler.crawl("https://en.wikipedia.org/");
        InputStream modelIn = Main.class.getClassLoader()
                .getResourceAsStream("langdetect-183.bin");
        if (modelIn == null) {
            System.err.println("Language detection model file not found in classpath.");
            return;
        }

        LanguageDetectorModel langModel = new LanguageDetectorModel(modelIn);

        // 2. Instantiate the LanguageDetectorME class
        LanguageDetector langDetector = new LanguageDetectorME(langModel);

        // 3. Predict the language
        String inputText = "ꠍꠤꠟꠐꠤ";
        Language bestLanguage = langDetector.predictLanguage(inputText);
        Language[] languages = langDetector.predictLanguages(inputText);
        for (int i = 0; i < languages.length; i++) {
            System.out.println(languages[i].getLang());
        }
    }
}
