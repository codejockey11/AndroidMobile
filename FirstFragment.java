package com.example.execution;

import android.os.Bundle;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.execution.databinding.FragmentFirstBinding;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.widget.TextView;

// add android:configChanges="orientation|screenSize" to the manifest
// so the view doesn't restart after rotation

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;

    private HttpRequester httpRequester;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Setup the individual handler for the long running task
        Handler handler = SetupHandlerThreads();

        // Need to pass the handler to the threaded object so it can send messages to the looper
        httpRequester = new HttpRequester(handler, "https://www.aviationweather.gov/adds/dataserver_current/httpparam?dataSource=metars&requestType=retrieve&format=xml&hoursBeforeNow=1&stationString=KPKB");

        binding.buttonFirst.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Post the HttpRequester with the handler object to the looper
                handler.post(httpRequester);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private Handler SetupHandlerThreads()
    {
        // A handler thread is required for each thread being executed
        // In an Async request all the thread handling is inherited
        // but here the actual thread handler needs to be set up

        // Grabbing FirstFragment's Looper (aka message queue)
        //Handler uiThread = new Handler(Looper.getMainLooper());

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

                        // Getting FirstFragment's (this object) main view thread and posting to it
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
        binding.textviewFirst.setText(httpRequester.buffer);
    }
}