package com.example.handlerlooper;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Message;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private TextView htmlResponse;

    private Button buttonNext;

    private HttpRequester httpRequester;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Setup the individual handler for the long running task
        Handler handler = SetupHandlerThreads();

        // Need to pass the handler to the threaded object so it can send messages to the looper
        httpRequester = new HttpRequester(handler, "https://www.google.com");

        htmlResponse = (TextView)findViewById(R.id.htmlResponse);

        buttonNext = (Button)findViewById(R.id.buttonNext);

        buttonNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void  onClick(View view) {
                // Post the HttpRequester with the handler object to the looper
                handler.post(httpRequester);
            }
        });
    }

    private Handler SetupHandlerThreads()
    {
        // A handler thread is required for each thread being executed
        // In an Async request all the thread handling is inherited
        // but here the actual thread handler needs to be set up

        // Make the new thread
        HandlerThread handlerThread = new HandlerThread("HttpRequesterHandler");
        // start it
        handlerThread.start();
        // Grab the newly started threads looper (aka. message queue)
        Looper looper = handlerThread.getLooper();

        // Set up the handler for the new looper's messages
        Handler handler = new Handler(looper) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                switch(msg.what)
                {
                    // pre-threading message
                    // case 0:

                    // during thread execution message
                    // case 1:

                    // on thread completion message
                    case 2:
                    {
                        // Since the message handling is defined as a new handler
                        // you have to post a message to the main UI thread that
                        // can call a method that is unique to your application.
                        // The SetupHandlerThreads() method cannot be encapsulated
                        // in another object for that reason.

                        // Getting the Activity (application) main view thread and posting to it
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                UpdateView();
                            }
                        });

                        break;
                    }
                }
            }
        };

        return handler;
    }

    // This is the unique method to your application.
    // At this point the variables in the threaded object can be referenced.
    // For ease-of-use the variables are public.
    private void UpdateView() {
        htmlResponse.setText(httpRequester.buffer);
    }
}