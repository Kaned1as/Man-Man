package com.adonai.manman;


import com.adonai.manman.parser.Man2Html;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by adonai on 24.11.14.
 */
public class HtmlFormatterTest {

    public void testHtmlOutput() throws IOException {
        FileInputStream fis = new FileInputStream("/home/adonai/Рабочий Стол/yaourt.8");
        BufferedReader br = new BufferedReader(new InputStreamReader(fis));
        Man2Html m2h = new Man2Html(br);
        String result = m2h.getHtml();
        System.out.print(result);
    }

}
