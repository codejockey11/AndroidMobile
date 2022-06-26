package com.example.metars;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Message;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import android.widget.EditText;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    // making three handler requests on the looper thread
    private HttpRequester stationHttpRequester;
    private HttpRequester metarHttpRequester;
    private HttpRequester tafHttpRequester;

    // counting each request as it comes back
    private int handlerWaitCount = 0;
    final int maxWaitHandlers = 3;

    private StationInfo station;
    private StringBuilder metarBuilder;
    private StringBuilder tafBuilder;

    private double temp;
    private double dewpoint;
    private double altimeter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        stationIdent.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_GO){
                    textView.setText(textView.getText().toString().toUpperCase());

                    hideSoftKeyboard();

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
            }
        });
    }

    public void hideSoftKeyboard() {
        if(getCurrentFocus()!=null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }
    public void showSoftKeyboard(@NonNull View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        view.requestFocus();
        inputMethodManager.showSoftInput(view, 0);
    }

    @NonNull
    private Handler setupStationHandler(Looper looper)
    {
        return new Handler(looper) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                if (msg.what == 2)
                {
                    // posting to the MainActivity's thread looper
                    // can't contain main threads looper in an object
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            parseStationInfo();
                            handlerWaitCount++;
                            updateView(station, metarBuilder, tafBuilder);
                        }
                    });
                }
            }
        };
    }

    @NonNull
    private Handler setupMetarHandler(Looper looper)
    {
        return new Handler(looper) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                if (msg.what == 2)
                {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            metarBuilder = parseMetarInfo();
                            handlerWaitCount++;
                            updateView(station, metarBuilder, tafBuilder);
                        }
                    });
                }
            }
        };
    }

    @NonNull
    private Handler setupTafHandler(Looper looper)
    {
        return new Handler(looper) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                if (msg.what == 2)
                {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            tafBuilder = parseTafInfo();
                            handlerWaitCount++;
                            updateView(station, metarBuilder, tafBuilder);
                        }
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
            xpp.setInput( new StringReader( stationHttpRequester.buffer ) );
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
            switch(eventType)
            {
                case XmlPullParser.START_DOCUMENT: {
                    break;
                }
                case  XmlPullParser.START_TAG:{
                    name = xpp.getName();

                    break;
                }
                case  XmlPullParser.TEXT:{
                    try {
                        if (!xpp.isWhitespace()) {
                            if (Objects.equals(name, "station_id")){
                                station.station_id = xpp.getText();
                            }
                            if (Objects.equals(name, "latitude")){
                                station.latitude = Double.parseDouble(xpp.getText());
                            }
                            if (Objects.equals(name, "longitude")){
                                station.longitude = Double.parseDouble(xpp.getText());
                            }
                            if (Objects.equals(name, "elevation_m")){
                                station.elevation_m = Double.parseDouble(xpp.getText());
                            }
                            if (Objects.equals(name, "site")){
                                station.site = xpp.getText();
                            }
                            if (Objects.equals(name, "state")){
                                station.state = xpp.getText();
                            }
                            if (Objects.equals(name, "country")){
                                station.country = xpp.getText();
                            }
                        }
                    } catch (XmlPullParserException e) {
                        e.printStackTrace();
                        return;
                    }
                    break;
                }
                case XmlPullParser.END_TAG:{
                    name = xpp.getName();
                    if (Objects.equals(name, "METAR")){
                        station.type += "METAR";
                    }
                    if (Objects.equals(name, "TAF")){
                        station.type += "/TAF";
                    }
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
            xpp.setInput( new StringReader( metarHttpRequester.buffer ) );
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
            switch(eventType)
            {
                case XmlPullParser.START_DOCUMENT:
                case XmlPullParser.END_TAG: {

                    break;
                }
                case  XmlPullParser.START_TAG: {
                    name = xpp.getName();

                    if (Objects.equals(name, "METAR")) {
                        isSkyCondition = true;
                    }

                    if (Objects.equals(name, "sky_condition")) {
                        if (xpp.getAttributeCount() > 0) {
                            if(isSkyCondition) {
                                isSkyCondition = false;
                                sb.append("\nSky:");
                            }
                            for (int i = 0; i < xpp.getAttributeCount(); i++) {
                                sb.append(xpp.getAttributeValue(i) + " ");
                            }
                        }
                    }

                    break;
                }
                case  XmlPullParser.TEXT: {
                    try {
                        if (!xpp.isWhitespace()) {
                            if (Objects.equals(name, "raw_text")) {
                                sb.append("\nRaw Text:" + xpp.getText());
                            }
                            if (Objects.equals(name, "observation_time")) {
                                sb.append("\nTime:" + FlipTimeDate(xpp.getText().replace("T", " ")));
                            }
                            if (Objects.equals(name, "latitude")) {
                                sb.append("\nGPS:" + xpp.getText());
                            }
                            if (Objects.equals(name, "longitude")) {
                                sb.append(", " + xpp.getText());
                            }
                            if (Objects.equals(name, "temp_c")) {
                                temp = Double.parseDouble(xpp.getText());
                                Temperature tc = new Temperature("C", temp);

                                sb.append("\nTemperature:" + xpp.getText() + " (" + String.format("%.2f", tc.fValue) + ")");
                            }
                            if (Objects.equals(name, "dewpoint_c")) {
                                dewpoint = Double.parseDouble(xpp.getText());

                                Temperature dc = new Temperature("C", dewpoint);

                                sb.append("\nDewpoint:" + xpp.getText() + " (" + String.format("%.2f", dc.fValue) + ")");
                            }
                            if (Objects.equals(name, "wind_dir_degrees")) {
                                sb.append("\nWinds:" + xpp.getText());
                            }
                            if (Objects.equals(name, "wind_speed_kt")) {
                                sb.append("/" + xpp.getText());
                            }
                            if (Objects.equals(name, "wind_gust_kt")) {
                                sb.append("\nWind Gust:" + xpp.getText());
                            }
                            if (Objects.equals(name, "visibility_statute_mi")) {
                                sb.append("\nVisibility:" + xpp.getText());
                            }
                            if (Objects.equals(name, "altim_in_hg")) {
                                altimeter = Double.parseDouble(xpp.getText());
                                sb.append("\nAltimeter:" + String.format("%.2f", Double.parseDouble(xpp.getText())));
                            }
                            if (Objects.equals(name, "sea_level_pressure_mb")) {
                                sb.append("\nSealevel Pressure:" + xpp.getText());
                            }
                            if (Objects.equals(name, "wx_string")) {
                                sb.append("\nWX:" + xpp.getText());
                            }
                            if (Objects.equals(name, "corrected")) {
                                sb.append("\nCorrected:" + xpp.getText());
                            }
                            if (Objects.equals(name, "auto")) {
                                sb.append("\nAuto:" + xpp.getText());
                            }
                            if (Objects.equals(name, "auto_station")) {
                                sb.append("\nAuto Station:" + xpp.getText());
                            }
                            if (Objects.equals(name, "maintenance_indicator_on")) {
                                sb.append("\nMaintenance:" + xpp.getText());
                            }
                            if (Objects.equals(name, "no_signal")) {
                                sb.append("\nNo Signal:" + xpp.getText());
                            }
                            if (Objects.equals(name, "lightning_sensor_off")) {
                                sb.append("\nLightning Sensor Off:" + xpp.getText());
                            }
                            if (Objects.equals(name, "freezing_rain_sensor_off")) {
                                sb.append("\nFreezing Rain Sensor Off:" + xpp.getText());
                            }
                            if (Objects.equals(name, "present_weather_sensor_off")) {
                                sb.append("\nPresent Weather Sensor Off:" + xpp.getText());
                            }
                            if (Objects.equals(name, "flight_category")) {
                                sb.append("\nFlight Category:" + xpp.getText());
                            }
                            if (Objects.equals(name, "maxT_c")) {
                                sb.append("\nMax Temp:" + xpp.getText());
                            }
                            if (Objects.equals(name, "minT_c")) {
                                sb.append("\nMin Temp:" + xpp.getText());
                            }
                            if (Objects.equals(name, "maxT24hr_c")) {
                                sb.append("\nMax Temp 24hr:" + xpp.getText());
                            }
                            if (Objects.equals(name, "minT24hr_c")) {
                                sb.append("\nMin Temp 24hr:" + xpp.getText());
                            }
                            if (Objects.equals(name, "precip_in")) {
                                sb.append("\nPrecipitation:" + xpp.getText());
                            }
                            if (Objects.equals(name, "pcp3hr_in")) {
                                sb.append("\nPrecipitation 3hr:" + xpp.getText());
                            }
                            if (Objects.equals(name, "pcp6hr_in")) {
                                sb.append("\nPrecipitation 6hr:" + xpp.getText());
                            }
                            if (Objects.equals(name, "pcp24hr_in")) {
                                sb.append("\nPrecipitation 24hr:" + xpp.getText());
                            }
                            if (Objects.equals(name, "snow_in")) {
                                sb.append("\nSnow:" + xpp.getText());
                            }
                            if (Objects.equals(name, "vert_vis_ft")) {
                                sb.append("\nVertical Visibility:" + xpp.getText());
                            }
                            if (Objects.equals(name, "metar_type")) {
                                sb.append("\nMetar Type:" + xpp.getText());
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
            xpp.setInput( new StringReader( tafHttpRequester.buffer ) );
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
            switch(eventType)
            {
                case XmlPullParser.START_DOCUMENT:
                case XmlPullParser.END_TAG: {

                    break;
                }
                case  XmlPullParser.START_TAG: {
                    name = xpp.getName();

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
                                sb.append(xpp.getAttributeValue(i) + " ");
                            }
                        }
                    }

                    break;
                }
                case  XmlPullParser.TEXT: {
                    try {
                        if (!xpp.isWhitespace()) {
                            if (Objects.equals(name, "raw_text")) {
                                sb.append("\nRaw Text:" + xpp.getText().replace("FM", "\nFM"));
                            }
                            if (Objects.equals(name, "issue_time")) {
                                sb.append("\nIssue:" + FlipTimeDate(xpp.getText().replace("T", " ")));
                            }
                            if (Objects.equals(name, "bulletin_time")) {
                                sb.append("\nBulletin:" + FlipTimeDate(xpp.getText().replace("T", " ")));
                            }
                            if (Objects.equals(name, "valid_time_from")) {
                                sb.append("\nValid From:" + FlipTimeDate(xpp.getText().replace("T", " ")));
                            }
                            if (Objects.equals(name, "valid_time_to")) {
                                sb.append(" To:" + FlipTimeDate(xpp.getText().replace("T", " ")));
                            }
                            if (Objects.equals(name, "fcst_time_from")) {
                                sb.append("\nForecast From:" + FlipTimeDate(xpp.getText().replace("T", " ")));
                            }
                            if (Objects.equals(name, "fcst_time_to")) {
                                sb.append(" To:" + FlipTimeDate(xpp.getText().replace("T", " ")));
                            }
                            if (Objects.equals(name, "change_indicator")) {
                                sb.append("\nChange:" + xpp.getText());
                            }
                            if (Objects.equals(name, "probability")) {
                                sb.append("\nProbability:" + xpp.getText());
                            }
                            if (Objects.equals(name, "remarks")) {
                                sb.append("\nRemarks:" + xpp.getText());
                            }
                            if (Objects.equals(name, "wind_dir_degrees")) {
                                sb.append("\nWinds:" + xpp.getText());
                            }
                            if (Objects.equals(name, "wind_speed_kt")) {
                                sb.append("/" + xpp.getText());
                            }
                            if (Objects.equals(name, "wx_string")) {
                                sb.append("\nWX:" + xpp.getText());
                            }
                            if (Objects.equals(name, "visibility_statute_mi")) {
                                sb.append("\nVisibility:" + xpp.getText());
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

    private void updateView(StationInfo s, StringBuilder m, StringBuilder t){
        // making sure that all requests have completed before updating the UI
        if (handlerWaitCount != maxWaitHandlers) {
            return;
        }

        EditText textOut = findViewById(R.id.textOut);

        double alt = s.elevation_m * 3.2808399;

        Temperature tc = new Temperature("C", temp);

        Temperature dc = new Temperature("C", dewpoint);

        double pa = PressureAltitude(altimeter);

        double da = DensityAltitude(tc, altimeter, dc);

        double rh = RelativeHumidity(dewpoint, temp);

        double cbagl = CloudBaseAGL(temp, dewpoint, "C");


        String str = station.site + " " + station.state + " " + station.type;

        str += ("\n\nATMOSPHERE");

        str += "\nPressure Altitude:" + String.format("%.2f", pa + alt);

        str += "\nDensity Altitude:" + String.format("%.2f", da) + " (" + String.format("%.2f", (station.elevation_m * 3.2808399) + da) + ")";

        str += "\nRelative Humidity:" + String.format("%.2f", rh);

        str += "\nCloud Base AGL:" + String.format("%.0f", cbagl);

        str += "\n\nMETAR";
        str += m.toString();

        str += "\n\nTAF";
        str += t.toString();

        str += "\n\n";

        textOut.setText(str);
    }

    public double PressureAltitude(double p)
    {
        return 145366.45 * (1 - Math.pow(((33.8639 * p) / 1013.25), 0.190284));
    }

    public double CloudBaseAGL(double t, double d, @NonNull String tp)
    {
        // 2.5 for Celsius 4.4 for Fahrenheit
        if (tp.equals("C")) {
            return ((t - d) / 2.5) * 1000.00;
        }
        else {
            return ((t - d) / 4.4) * 1000.00;
        }
    }

    public double DensityAltitude(Temperature tc, double pressureHg, Temperature dc)
    {
        // Find virtual temperature using temperature in kelvin and dewpoint in celcius
        double Tv = VirtualTemperature(tc, pressureHg, dc);

        // passing virtual temperature in Kelvin
        return CalcDensityAltitude(pressureHg, Tv);
    }

    public double VirtualTemperature(Temperature tc, double pressureHg, @NonNull Temperature dc)
    {
        // vapor pressure uses celcius
        double vp = 6.11 * Math.pow(10, ((7.5 * dc.cValue) / (237.7 + dc.cValue)));

        double mbpressure = 33.8639 * pressureHg;

        // use temperature in Kelvin
        if (mbpressure != 0) {
            return tc.kValue / (1.0 - (vp / mbpressure) * (1.0 - 0.622));
        }

        return 0.0;
    }

    public double CalcDensityAltitude(double pressureHg, double tv)
    {
        // virtual temperature comes in as Kelvin
        Temperature tk = new Temperature("K", tv);

        // use virtual temperature as Rankine
        double p = (17.326 * pressureHg) / tk.rValue;

        // weather.gov and seems to be the most used
        return 145366.0 * (1.0 - (Math.pow(p, 0.235)));

        // NWS
        //return 145442.16 * (1.0 - (Math.Pow(p, 0.235)));
    }

    public double RelativeHumidity(double d, double t)
    {
        // Temperatures are celcius
        // =100*(EXP((17.625*TD)/(243.04+TD))/EXP((17.625*T)/(243.04+T)))
        return 100 * (Math.exp((17.625 * d) / (243.04 + d)) / Math.exp((17.625 * t) / (243.04 + t)));
    }

    public String FlipTimeDate(@NonNull String str)
    {
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
}
