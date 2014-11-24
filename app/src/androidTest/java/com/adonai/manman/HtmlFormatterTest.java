package com.adonai.manman;

import org.junit.Test;
import org.netbeans.modules.cnd.completion.doxygensupport.Man2Html;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;

/**
 * Created by adonai on 24.11.14.
 */
public class HtmlFormatterTest {

    @Test
    public void testHtmlOutput() throws FileNotFoundException {
        FileInputStream fis = new FileInputStream("/home/adonai/Рабочий Стол/yaourt.8");
        BufferedReader br = new BufferedReader(new InputStreamReader(fis));
        Man2Html m2h = new Man2Html(br);
        String result = m2h.getHtml();
        System.out.print(result);
    }

}
