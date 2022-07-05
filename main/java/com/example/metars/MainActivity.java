package com.example.metars;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.metars.databinding.ActivityMainBinding;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    // counting each request as it comes back
    private int handlerWaitCount = 0;
    private final int maxWaitHandlers = 3;
    private final double feetInMeters = 3.2808399;

    // making three handler requests on the looper thread
    private HttpRequester stationHttpRequester;
    private HttpRequester metarHttpRequester;
    private HttpRequester tafHttpRequester;

    private StationInfo station;
    private StringBuilder metarBuilder;
    private StringBuilder tafBuilder;

    private double temp;
    private double dewpoint;
    private double altimeter;

    private boolean displayFormatted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        // setting up the main looper for the three separate handlers
        // one looper for many handlers
        HandlerThread handlerThread = new HandlerThread("HttpRequesterHandler");
        handlerThread.start();
        Looper looper = handlerThread.getLooper();

        // handler to handle each unique request on same looper thread
        // each handler has its own handleMessage()
        Handler stationHandler = setupStationHandler(looper);
        Handler metarHandler = setupMetarHandler(looper);
        Handler tafHandler = setupTafHandler(looper);

        EditText stationIdent = findViewById(R.id.stationIdent);

        stationIdent.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_GO) {
                textView.setText(textView.getText().toString().toUpperCase());

                InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);

                handlerWaitCount = 0;

                // post the three long running tasks identified by their own handlers
                stationHttpRequester = new HttpRequester(stationHandler, "https://www.aviationweather.gov/adds/dataserver_current/httpparam?dataSource=stations&requestType=retrieve&format=xml&stationString=" + textView.getText());
                stationHandler.post(stationHttpRequester);

                metarHttpRequester = new HttpRequester(metarHandler, "https://www.aviationweather.gov/adds/dataserver_current/httpparam?dataSource=metars&requestType=retrieve&format=xml&hoursBeforeNow=1&stationString=" + textView.getText());
                metarHandler.post(metarHttpRequester);

                tafHttpRequester = new HttpRequester(tafHandler, "https://www.aviationweather.gov/adds/dataserver_current/httpparam?dataSource=tafs&requestType=retrieve&format=xml&hoursBeforeNow=1&stationString=" + textView.getText());
                tafHandler.post(tafHttpRequester);

                return true;
            }

            return false;
        });

        SharedPreferences settings = getSharedPreferences("UserInfo", 0);
        displayFormatted = settings.getBoolean("displayFormatted", false);
    }

    @NonNull
    private Handler setupStationHandler(Looper looper) {
        return new Handler(looper) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                if (msg.what == 2) {
                    parseStationInfo();
                    handlerWaitCount++;

                    // Need to post to the MainActivity's thread looper
                    // when the items created in that activity are being accessed
                    // (i.e. TextView or an EditText)
                    new Handler(Looper.getMainLooper()).post(() -> {
                        updateView();
                    });
                }
            }
        };
    }

    @NonNull
    private Handler setupMetarHandler(Looper looper) {
        return new Handler(looper) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                if (msg.what == 2) {
                    metarBuilder = parseMetarInfo();
                    handlerWaitCount++;

                    new Handler(Looper.getMainLooper()).post(() -> {
                        updateView();
                    });
                }
            }
        };
    }

    @NonNull
    private Handler setupTafHandler(Looper looper) {
        return new Handler(looper) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                if (msg.what == 2) {
                    tafBuilder = parseTafInfo();
                    handlerWaitCount++;

                    new Handler(Looper.getMainLooper()).post(() -> {
                        updateView();
                    });
                }
            }
        };
    }

    private void parseStationInfo() {
        station = new StationInfo();

        XmlPullParserFactory factory;
        try {
            factory = XmlPullParserFactory.newInstance();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            return;
        }

        factory.setNamespaceAware(true);
        XmlPullParser xpp;

        try {
            xpp = factory.newPullParser();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            return;
        }

        try {
            xpp.setInput(new StringReader(stationHttpRequester.buffer));
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            return;
        }

        String name = null;

        int eventType;

        try {
            eventType = xpp.getEventType();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            return;
        }

        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_DOCUMENT: {
                    break;
                }
                case XmlPullParser.START_TAG: {
                    name = xpp.getName();

                    break;
                }
                case XmlPullParser.TEXT: {
                    try {
                        if (!xpp.isWhitespace()) {
                            if (Objects.equals(name, "station_id")) {
                                station.station_id = xpp.getText();

                                break;
                            }
                            if (Objects.equals(name, "latitude")) {
                                station.latitude = Double.parseDouble(xpp.getText());

                                break;
                            }
                            if (Objects.equals(name, "longitude")) {
                                station.longitude = Double.parseDouble(xpp.getText());

                                break;
                            }
                            if (Objects.equals(name, "elevation_m")) {
                                station.elevation_m = Double.parseDouble(xpp.getText());

                                break;
                            }
                            if (Objects.equals(name, "site")) {
                                station.site = xpp.getText();

                                break;
                            }
                            if (Objects.equals(name, "state")) {
                                station.state = xpp.getText();

                                break;
                            }
                            if (Objects.equals(name, "country")) {
                                station.country = xpp.getText();

                                break;
                            }
                        }
                    } catch (XmlPullParserException e) {
                        e.printStackTrace();
                        return;
                    }
                    break;
                }
                case XmlPullParser.END_TAG: {
                    name = xpp.getName();
                    if (Objects.equals(name, "METAR")) {
                        station.type.concat("METAR");

                        break;
                    }
                    if (Objects.equals(name, "TAF")) {
                        station.type.concat("/TAF");
                    }

                    break;
                }
            }
            try {
                eventType = xpp.next();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (XmlPullParserException e) {
                e.printStackTrace();
                return;
            }
        }
    }

    @Nullable
    private StringBuilder parseMetarInfo() {
        StringBuilder sb = new StringBuilder();

        XmlPullParserFactory factory;
        try {
            factory = XmlPullParserFactory.newInstance();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            return null;
        }

        factory.setNamespaceAware(true);

        XmlPullParser xpp;
        try {
            xpp = factory.newPullParser();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            return null;
        }

        try {
            xpp.setInput(new StringReader(metarHttpRequester.buffer));
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            return null;
        }

        int eventType;

        try {
            eventType = xpp.getEventType();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            return null;
        }

        String name = null;

        boolean isSkyCondition = true;

        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_DOCUMENT:
                case XmlPullParser.END_TAG: {

                    break;
                }
                case XmlPullParser.START_TAG: {
                    name = xpp.getName();

                    if (displayFormatted) {
                        if (Objects.equals(name, "METAR")) {
                            isSkyCondition = true;
                        }

                        if (Objects.equals(name, "sky_condition")) {
                            if (xpp.getAttributeCount() > 0) {
                                if (isSkyCondition) {
                                    isSkyCondition = false;
                                    sb.append("\nSky:");
                                }
                                for (int i = 0; i < xpp.getAttributeCount(); i++) {
                                    sb.append(xpp.getAttributeValue(i)).append(" ");
                                }
                            }
                        }
                    }

                    break;
                }
                case XmlPullParser.TEXT: {
                    try {
                        if (!xpp.isWhitespace()) {
                            if (Objects.equals(name, "raw_text")) {
                                if (displayFormatted) {
                                    sb.append("\n\nRaw Text:").append(xpp.getText());
                                } else {
                                    sb.append("\n\n").append(xpp.getText());
                                }

                                break;
                            }

                            if (displayFormatted) {
                                if (Objects.equals(name, "observation_time")) {
                                    sb.append("\nTime:").append(FlipTimeDate(xpp.getText().replace("T", " ")));

                                    break;
                                }
                                if (Objects.equals(name, "latitude")) {
                                    sb.append("\nGPS:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "longitude")) {
                                    sb.append(", ").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "wind_dir_degrees")) {
                                    sb.append("\nWinds:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "wind_speed_kt")) {
                                    sb.append("/").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "wind_gust_kt")) {
                                    sb.append("\nWind Gust:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "visibility_statute_mi")) {
                                    sb.append("\nVisibility:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "sea_level_pressure_mb")) {
                                    sb.append("\nSea level Pressure:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "wx_string")) {
                                    sb.append("\nWX:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "corrected")) {
                                    sb.append("\nCorrected:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "auto")) {
                                    sb.append("\nAuto:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "auto_station")) {
                                    sb.append("\nAuto Station:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "maintenance_indicator_on")) {
                                    sb.append("\nMaintenance:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "no_signal")) {
                                    sb.append("\nNo Signal:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "lightning_sensor_off")) {
                                    sb.append("\nLightning Sensor Off:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "freezing_rain_sensor_off")) {
                                    sb.append("\nFreezing Rain Sensor Off:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "present_weather_sensor_off")) {
                                    sb.append("\nPresent Weather Sensor Off:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "flight_category")) {
                                    sb.append("\nFlight Category:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "maxT_c")) {
                                    sb.append("\nMax Temp:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "minT_c")) {
                                    sb.append("\nMin Temp:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "maxT24hr_c")) {
                                    sb.append("\nMax Temp 24hr:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "minT24hr_c")) {
                                    sb.append("\nMin Temp 24hr:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "precip_in")) {
                                    sb.append("\nPrecipitation:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "pcp3hr_in")) {
                                    sb.append("\nPrecipitation 3hr:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "pcp6hr_in")) {
                                    sb.append("\nPrecipitation 6hr:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "pcp24hr_in")) {
                                    sb.append("\nPrecipitation 24hr:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "snow_in")) {
                                    sb.append("\nSnow:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "vert_vis_ft")) {
                                    sb.append("\nVertical Visibility:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "metar_type")) {
                                    sb.append("\nMetar Type:").append(xpp.getText());

                                    break;
                                }
                            }

                            if (Objects.equals(name, "temp_c")) {
                                temp = Double.parseDouble(xpp.getText());

                                Temperature tc = new Temperature("C", temp);

                                if (displayFormatted) {
                                    sb.append("\nTemperature:").append(xpp.getText()).append(" (").append(String.format(Locale.ENGLISH, "%.2f", tc.fValue)).append(")");
                                }

                                break;
                            }
                            if (Objects.equals(name, "dewpoint_c")) {
                                dewpoint = Double.parseDouble(xpp.getText());

                                Temperature dc = new Temperature("C", dewpoint);

                                if (displayFormatted) {
                                    sb.append("\nDewpoint:").append(xpp.getText()).append(" (").append(String.format(Locale.ENGLISH, "%.2f", dc.fValue)).append(")");
                                }

                                break;
                            }
                            if (Objects.equals(name, "altim_in_hg")) {
                                altimeter = Double.parseDouble(xpp.getText());

                                if (displayFormatted) {
                                    sb.append("\nAltimeter:").append(String.format(Locale.ENGLISH, "%.2f", Double.parseDouble(xpp.getText())));
                                }

                                break;
                            }
                        }
                    } catch (XmlPullParserException e) {
                        e.printStackTrace();
                        return null;
                    }

                    break;
                }
            }
            try {
                eventType = xpp.next();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (XmlPullParserException e) {
                e.printStackTrace();
                return null;
            }
        }

        return sb;
    }

    @NonNull
    private StringBuilder parseTafInfo() {
        StringBuilder sb = new StringBuilder();

        XmlPullParserFactory factory;
        try {
            factory = XmlPullParserFactory.newInstance();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            return sb;
        }

        factory.setNamespaceAware(true);
        XmlPullParser xpp;

        try {
            xpp = factory.newPullParser();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            return sb;
        }

        try {
            xpp.setInput(new StringReader(tafHttpRequester.buffer));
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            return sb;
        }

        int eventType;

        try {
            eventType = xpp.getEventType();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            return sb;
        }

        String name = null;

        boolean isSkyCondition = true;

        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_DOCUMENT:
                case XmlPullParser.END_TAG: {

                    break;
                }
                case XmlPullParser.START_TAG: {
                    name = xpp.getName();

                    if (displayFormatted) {
                        if (Objects.equals(name, "forecast")) {
                            isSkyCondition = true;
                        }

                        if (Objects.equals(name, "sky_condition")) {
                            if (xpp.getAttributeCount() > 0) {
                                if (isSkyCondition) {
                                    isSkyCondition = false;
                                    sb.append("\nSky:");
                                }
                                for (int i = 0; i < xpp.getAttributeCount(); i++) {
                                    sb.append(xpp.getAttributeValue(i)).append(" ");
                                }
                            }
                        }
                    }

                    break;
                }
                case XmlPullParser.TEXT: {
                    try {
                        if (!xpp.isWhitespace()) {
                            if (Objects.equals(name, "raw_text")) {
                                if (displayFormatted) {
                                    sb.append("\n\nRaw Text:").append(xpp.getText().replace("FM", "\nFM"));
                                } else {
                                    sb.append("\n\n").append(xpp.getText().replace("FM", "\nFM"));
                                }

                                break;
                            }

                            if (displayFormatted) {
                                if (Objects.equals(name, "issue_time")) {
                                    sb.append("\nIssue:").append(FlipTimeDate(xpp.getText().replace("T", " ")));

                                    break;
                                }
                                if (Objects.equals(name, "bulletin_time")) {
                                    sb.append("\nBulletin:").append(FlipTimeDate(xpp.getText().replace("T", " ")));

                                    break;
                                }
                                if (Objects.equals(name, "valid_time_from")) {
                                    sb.append("\nValid From:").append(FlipTimeDate(xpp.getText().replace("T", " ")));

                                    break;
                                }
                                if (Objects.equals(name, "valid_time_to")) {
                                    sb.append("\nValid To:").append(FlipTimeDate(xpp.getText().replace("T", " ")));

                                    break;
                                }
                                if (Objects.equals(name, "fcst_time_from")) {
                                    sb.append("\n\nForecast\nFrom:").append(FlipTimeDate(xpp.getText().replace("T", " ")));

                                    break;
                                }
                                if (Objects.equals(name, "fcst_time_to")) {
                                    sb.append("\nTo:").append(FlipTimeDate(xpp.getText().replace("T", " ")));

                                    break;
                                }
                                if (Objects.equals(name, "change_indicator")) {
                                    sb.append("\nChange:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "probability")) {
                                    sb.append("\nProbability:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "remarks")) {
                                    sb.append("\nRemarks:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "wind_dir_degrees")) {
                                    sb.append("\nWinds:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "wind_speed_kt")) {
                                    sb.append("/").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "wx_string")) {
                                    sb.append("\nWX:").append(xpp.getText());

                                    break;
                                }
                                if (Objects.equals(name, "visibility_statute_mi")) {
                                    sb.append("\nVisibility:").append(xpp.getText());

                                    break;
                                }
                            }
                        }
                    } catch (XmlPullParserException e) {
                        e.printStackTrace();
                    }

                    break;
                }
            }
            try {
                eventType = xpp.next();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (XmlPullParserException e) {
                e.printStackTrace();
                return sb;
            }
        }

        return sb;
    }

    private void updateView() {
        // making sure that all requests have completed before updating the UI
        if (handlerWaitCount != maxWaitHandlers) {
            return;
        }

        TextView textOut = findViewById(R.id.textOut);

        if (Objects.equals(station.station_id, "")) {
            textOut.setText(R.string.stationNotFound);

            return;
        }

        String str = "";

        if (displayFormatted) {
            str = "Location:" + station.site + " " + station.state;

            str += ("\n\nATMOSPHERE");

            str += String.format(Locale.ENGLISH, "\n\nAltitude:%.2f", station.elevation_m * feetInMeters);

            if (metarBuilder.toString().length() > 0) {
                str += FormatAtmosphereData();
            }

            str += "\n\nMETAR";
        } else {
            str += ("ATMOSPHERE");

            str += String.format(Locale.ENGLISH, "\n\nAltitude:%.2f", station.elevation_m * feetInMeters);

            if (metarBuilder.toString().length() > 0) {
                str += FormatAtmosphereData();
            }
        }

        str += metarBuilder.toString();

        if (displayFormatted) {
            str += "\n\nTAF";
        }

        str += tafBuilder.toString();

        str += "\n\n\n";

        textOut.setText(str);
    }

    @NonNull
    private String FormatAtmosphereData()
    {
        double alt = station.elevation_m * feetInMeters;

        Temperature tc = new Temperature("C", temp);

        Temperature dc = new Temperature("C", dewpoint);

        double pa = PressureAltitude(altimeter);

        double da = DensityAltitude(tc, altimeter, dc);

        double rh = RelativeHumidity(dewpoint, temp);

        double cbagl = CloudBaseAGL(temp, dewpoint);

        String str = "\nPressure Altitude:" + String.format(Locale.ENGLISH, "%.2f", pa + alt);

        str += "\nDensity Altitude:" + String.format(Locale.ENGLISH, "%.2f", da) +
                " (" + String.format(Locale.ENGLISH, "%.2f", (station.elevation_m * feetInMeters) + da) + ")";

        str += "\nRelative Humidity:" + String.format(Locale.ENGLISH, "%.2f", rh);

        str += "\nCloud Base AGL:" + String.format(Locale.ENGLISH, "%.0f", cbagl);

        return str;
    }

    private double PressureAltitude(double p) {
        return 145366.45 * (1.0 - Math.pow(((33.8639 * p) / 1013.25), 0.190284));
    }

    // temperatures are in celcius
    private double CloudBaseAGL(double t, double d) {
            return ((t - d) / 2.5) * 1000.00;
    }

    private double DensityAltitude(Temperature tc, double pressureHg, Temperature dc) {
        // Find virtual temperature using temperature in kelvin and dewpoint in celcius
        double Tv = VirtualTemperature(tc, pressureHg, dc);

        // passing virtual temperature in Kelvin
        return CalcDensityAltitude(pressureHg, Tv);
    }

    private double VirtualTemperature(Temperature tc, double pressureHg, @NonNull Temperature dc) {
        // vapor pressure uses celcius
        double vp = 6.11 * Math.pow(10.0, ((7.5 * dc.cValue) / (237.7 + dc.cValue)));

        double mbpressure = 33.8639 * pressureHg;

        // use temperature in Kelvin
        if (mbpressure != 0) {
            return tc.kValue / (1.0 - (vp / mbpressure) * (1.0 - 0.622));
        }

        return 0.0;
    }

    private double CalcDensityAltitude(double pressureHg, double tv) {
        // virtual temperature comes in as Kelvin
        Temperature tk = new Temperature("K", tv);

        // use virtual temperature as Rankine
        double p = (17.326 * pressureHg) / tk.rValue;

        // weather.gov and seems to be the most used
        return 145366.0 * (1.0 - (Math.pow(p, 0.235)));

        // NWS
        //return 145442.16 * (1.0 - (Math.Pow(p, 0.235)));
    }

    private double RelativeHumidity(double d, double t) {
        // Temperatures are celcius
        // =100*(EXP((17.625*TD)/(243.04+TD))/EXP((17.625*T)/(243.04+T)))
        return 100.0 * (Math.exp((17.625 * d) / (243.04 + d)) / Math.exp((17.625 * t) / (243.04 + t)));
    }

    @NonNull
    private String FlipTimeDate(@NonNull String str) {
        String[] stra = str.split(" ");

        String[] part = stra[0].split("-");

        String rstr = part[1];
        rstr += "-";

        rstr += part[2];
        rstr += "-";

        rstr += part[0];
        rstr += " ";

        rstr += stra[1];

        return rstr;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        EditText stationIdent = findViewById(R.id.stationIdent);

        if (id == R.id.type_raw) {
            displayFormatted = false;

            saveUserPreferences();

            if (stationIdent.getText().length() != 0) {
                metarBuilder = parseMetarInfo();
                tafBuilder = parseTafInfo();

                updateView();
            }

            return true;
        }

        if (id == R.id.type_formatted) {
            displayFormatted = true;

            saveUserPreferences();

            if (stationIdent.getText().length() != 0) {
                metarBuilder = parseMetarInfo();
                tafBuilder = parseTafInfo();

                updateView();
            }

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void saveUserPreferences()
    {
        SharedPreferences settings = getSharedPreferences("UserInfo", 0);
        SharedPreferences.Editor editor = settings.edit();

        editor.putBoolean("displayFormatted", displayFormatted);

        editor.apply();
    }
}