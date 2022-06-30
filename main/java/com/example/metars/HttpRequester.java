package com.example.metars;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownServiceException;

public class HttpRequester implements Runnable {

    public Handler handler;
    public String urlStr;

    public String buffer = "";

    // passing the handler in order to post messages to the looper
    HttpRequester(Handler h, String u) {
        handler = h;
        urlStr = u;
    }

    // the run method is similar to doInBackground() for Async Threads
    @Override
    public void run() {
        URL url = null;
        HttpURLConnection httpURLConnection = null;

        InputStream inputStream = null;
        InputStreamReader inputStreamReader = null;

        try {
            url = new URL(urlStr);

        } catch (Exception ex) {
            Log.i("Application", "Error1");
            return;
        }

        try {
            httpURLConnection = (HttpURLConnection) url.openConnection();

            httpURLConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/102.0.0.0 Safari/537.36");
            httpURLConnection.setRequestProperty("Accept", "application/xml");

        } catch (Exception ex) {
            Log.i("Application", "Error2");
            return;
        }

        try {
            if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try {
                    inputStream = httpURLConnection.getInputStream();
                } catch (UnknownServiceException use) {
                    Log.i("Application", "Error3");
                    return;
                }
            }
        } catch (IOException ioe) {
            Log.i("Application", "Error4");
            return;
        }

        try {
            inputStreamReader = new InputStreamReader(inputStream);
        } catch (Exception ex) {
            Log.i("Application", "Error5");
            return;
        }

        buffer = "";

        try (BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                buffer += line;
            }
        } catch (Exception ex) {
            Log.i("Application", "Error6");
            return;
        }

        // Send a message here instead of the onPostExecute() method
        Message msg = new Message();
        msg.what = 2;
        handler.sendMessage(msg);
    }
}
