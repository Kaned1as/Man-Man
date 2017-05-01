package com.adonai.manman;

import com.adonai.manman.parser.Man2Html;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.zip.GZIPInputStream;

/**
 * Created by adonai on 09.04.16.
 */
public class Man2HtmlTest {

    @Test
    public void testHtmlOutput() throws IOException {
        //FileInputStream fis = new FileInputStream("/usr/share/man/man1/systemctl.1.gz");
        //FileInputStream fis = new FileInputStream("/usr/share/man/man1/tar.1.gz");
        //FileInputStream fis = new FileInputStream("/usr/share/man/man8/sudo.8.gz");
        FileInputStream fis = new FileInputStream("/usr/share/man/man1/grep.1.gz");
        GZIPInputStream gis = new GZIPInputStream(fis);
        BufferedReader br = new BufferedReader(new InputStreamReader(gis));
        Man2Html m2h = new Man2Html(br);
        String result = m2h.getHtml();
        br.close();

        String homeDir = System.getProperty("user.home");
        FileOutputStream fos = new FileOutputStream(homeDir + File.separator + "test.html");
        OutputStreamWriter osw = new OutputStreamWriter(fos);
        osw.write(result);
        osw.close();

        Runtime.getRuntime().exec("xdg-open " + homeDir + File.separator + "test.html");
    }
}
