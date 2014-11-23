package com.adonai.manman.misc;

import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import java.io.IOException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

/**
 * Workaround for SSLException <a href="https://jira.appcelerator.org/browse/TIMOB-16468">bug</a>,
 * it seems, mankier.com deployed another virtual host on the same hostname
 * Accept all the hosts for now...
 *
 * @author Adonai
 */
public class HttpClientFactory {

    private static HttpParams defaultHttpParams;

    static {
        defaultHttpParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(defaultHttpParams, 3000);
        HttpConnectionParams.setSoTimeout(defaultHttpParams, 10000);
    }

    private static final X509HostnameVerifier delegate = new X509HostnameVerifier() {
        @Override
        public boolean verify(String s, SSLSession sslSession) {
            return false;
        }

        @Override
        public void verify(String s, SSLSocket sslSocket) throws IOException {

        }

        @Override
        public void verify(String s, X509Certificate x509Certificate) throws SSLException {

        }

        @Override
        public void verify(String s, String[] strings, String[] strings2) throws SSLException {

        }
    };


    public static DefaultHttpClient getTolerantClient() {
        DefaultHttpClient client = new DefaultHttpClient(defaultHttpParams);

        SSLSocketFactory sslSocketFactory = (SSLSocketFactory) client
                .getConnectionManager().getSchemeRegistry().getScheme("https").getSocketFactory();
        sslSocketFactory.setHostnameVerifier(delegate);
        return client;
    }
}
