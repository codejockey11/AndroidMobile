using System;
using Android.App;
using Android.OS;
using Android.Runtime;
using Android.Views;
using AndroidX.AppCompat.Widget;
using AndroidX.AppCompat.App;

// floating action button
using Google.Android.Material.FloatingActionButton;
using Google.Android.Material.Snackbar;

// http requests
// be sure that within project properties the INTERNET option is checked under required permissions
// your http calls will fail if you are not using wifi and have the use mobile data option off in the device
// 
using System.Collections.Generic;
using System.Threading.Tasks;
using System.Net.Http;
using System.Xml;

namespace METARs
{
    [Activity(Label = "@string/app_name", Theme = "@style/AppTheme.NoActionBar", MainLauncher = true)]
    public class MainActivity : AppCompatActivity
    {
        private Android.Widget.EditText stationIdent;
        private Android.Widget.TextView textOut;

        private StationInfo stationInfo;

        private List<String> station;
        private List<String> metar;
        private List<String> taf;

        protected override void OnCreate(Bundle savedInstanceState)
        {
            base.OnCreate(savedInstanceState);
            Xamarin.Essentials.Platform.Init(this, savedInstanceState);
            SetContentView(Resource.Layout.activity_main);

            Toolbar toolbar = (Toolbar)FindViewById<View>(Resource.Id.toolbar);
            SetSupportActionBar(toolbar);

            stationIdent = (Android.Widget.EditText)FindViewById<View>(Resource.Id.stationIdent);
            // no need for ime actions in the layout as .Net triggers the editor action when enter is pressed on the soft keyboard
            stationIdent.EditorAction += StationIdentEditorAction;

            textOut = (Android.Widget.TextView)FindViewById<View>(Resource.Id.textOut);
        }

        public override bool OnCreateOptionsMenu(IMenu menu)
        {
            MenuInflater.Inflate(Resource.Menu.menu_main, menu);
            return true;
        }

        public override bool OnOptionsItemSelected(IMenuItem item)
        {
            int id = item.ItemId;
            if (id == Resource.Id.action_settings)
            {
                return true;
            }

            return base.OnOptionsItemSelected(item);
        }

        private void StationIdentEditorAction(object sender, EventArgs eventArgs)
        {
            FindViewById<View>(Resource.Id.stationIdent).Enabled = false;
            FindViewById<View>(Resource.Id.stationIdent).Enabled = true;

            stationIdent.Text = stationIdent.Text.ToUpper();

            textOut.Text = null;

            station = new List<String>();

            stationInfo = new StationInfo();

            // https is required with Android http calls
            AsyncHttpRequest(station, "https://www.aviationweather.gov/adds/dataserver_current/httpparam?dataSource=stations&requestType=retrieve&format=xml&hoursBeforeNow=1&stationString=" + stationIdent.Text);

            foreach (String s in station)
            {
                XmlDocument xmlDoc = new XmlDocument();
                xmlDoc.LoadXml(s);

                string xpath = "response/data/Station";
                var nodes = xmlDoc.SelectNodes(xpath);

                foreach (XmlNode node in nodes)
                {
                    stationInfo.station_id = node["station_id"].InnerText;
                    stationInfo.latitude = Convert.ToDouble(node["latitude"].InnerText);
                    stationInfo.longitude = Convert.ToDouble(node["longitude"].InnerText);
                    stationInfo.elevation_m = Convert.ToDouble(node["elevation_m"].InnerText);
                    stationInfo.site = node["site"].InnerText;
                    stationInfo.state = node["state"].InnerText;
                    stationInfo.country = node["country"].InnerText;
                    stationInfo.type = node["station_id"].InnerText;
                }
            }

            metar = new List<String>();

            AsyncHttpRequest(metar, "https://www.aviationweather.gov/adds/dataserver_current/httpparam?dataSource=metars&requestType=retrieve&format=xml&hoursBeforeNow=1&stationString=" + stationIdent.Text);

            foreach (String s in metar)
            {
                XmlDocument xmlDoc = new XmlDocument();
                xmlDoc.LoadXml(s);

                string xpath = "response/data/METAR";
                var nodes = xmlDoc.SelectNodes(xpath);

                foreach (XmlNode node in nodes)
                {
                    textOut.Text += FormatMetarEntry(node);
                }
            }

            taf = new List<String>();

            AsyncHttpRequest(taf, "https://www.aviationweather.gov/adds/dataserver_current/httpparam?dataSource=tafs&requestType=retrieve&format=xml&hoursBeforeNow=1&stationString=" + stationIdent.Text);

            foreach (String s in taf)
            {
                XmlDocument xmlDoc = new XmlDocument();
                xmlDoc.LoadXml(s);

                string xpath = "response/data/TAF";
                var nodes = xmlDoc.SelectNodes(xpath);

                foreach (XmlNode node in nodes)
                {
                    //textOut.Text += FormatTafEntry(node);
                }
            }
        }

        public override void OnRequestPermissionsResult(int requestCode, string[] permissions, [GeneratedEnum] Android.Content.PM.Permission[] grantResults)
        {
            Xamarin.Essentials.Platform.OnRequestPermissionsResult(requestCode, permissions, grantResults);

            base.OnRequestPermissionsResult(requestCode, permissions, grantResults);
        }

        private readonly HttpClient _httpClient = new HttpClient(new Xamarin.Android.Net.AndroidClientHandler());

        // This method will run in its own thread for each occurance that is called
        // yet again .Net wins the battle with easier strategies for thread programming
        // all we really need here is the async semantic
        public async void AsyncHttpRequest(List<String> response, String url)
        {
            Uri uri = new Uri(string.Format(url, string.Empty));

            _httpClient.DefaultRequestHeaders.Add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/102.0.0.0 Safari/537.36");
            _httpClient.DefaultRequestHeaders.Add("Accept", "application/xml");

            var tasks = new List<Task<String>>();

            try
            {
                tasks.Add(_httpClient.GetStringAsync(uri));
            }
            catch (Exception ex)
            {
                textOut.Text = ex.Message;
                return;
            }

            try
            {
                Task.WaitAll(tasks.ToArray());
            }
            catch (Exception ex)
            {
                textOut.Text = ex.Message;
                return;
            }

            response.Clear();

            foreach (var task in tasks)
            {
                response.Add(await task);
            }
        }

        public String FormatMetarEntry(XmlNode node)
        {
            var alt = stationInfo.elevation_m * 3.2808399;

            var tc = new Temperature('C', Convert.ToDouble(node["temp_c"].InnerText));

            var dc = new Temperature('C', Convert.ToDouble(node["dewpoint_c"].InnerText));

            var pa = PressureAltitude(Convert.ToDouble(node["altim_in_hg"].InnerText));

            var da = DensityAltitude(tc, Convert.ToDouble(node["altim_in_hg"].InnerText), dc);

            var rh = RelativeHumidity(Convert.ToDouble(node["dewpoint_c"].InnerText), Convert.ToDouble(node["temp_c"].InnerText));

            var cbagl = CloudBaseAGL(Convert.ToDouble(node["temp_c"].InnerText), Convert.ToDouble(node["dewpoint_c"].InnerText), 'C');

            String t = new String("ATMOSPHERE");

            t += String.Format("\nPressure Altitude:{0:F2}", (pa + alt));

            t += String.Format("\nDensity Altitude:{0:F2} ({1:F2})", da, ((stationInfo.elevation_m * 3.2808399) + da));

            t += String.Format("\nRelative Humidity:{0:F2}", rh);

            t += String.Format("\nCloud Base AGL:{0}", cbagl);

            t += "\n\nMETAR";

            t += String.Format("\nRaw Text:{0}", node["raw_text"].InnerText);

            t += String.Format("\nStation ID:{0}", node["station_id"].InnerText);

            t += String.Format("\nTime:{0}", FlipTimeDate(node["observation_time"].InnerText.Replace("T", " ")));

            t += String.Format("\nGPS:{0} {1}", node["latitude"].InnerText, node["longitude"].InnerText);

            t += String.Format("\nTemperature:{0:F2}  ({1:F2})", node["temp_c"].InnerText, tc.fValue);

            t += String.Format("\nDewpoint:{0:F2}  ({1:F2})", node["dewpoint_c"].InnerText, dc.fValue);

            t += String.Format("\nWinds:{0}/{1} ({2:F2})", node["wind_dir_degrees"].InnerText, node["wind_speed_kt"].InnerText, Convert.ToDouble(node["wind_speed_kt"].InnerText) * 1.15);

            if (node["wind_gust_kt"] != null)
            {
                t += String.Format("\nWind Gust:{0}", node["wind_gust_kt"].InnerText);
            }

            t += String.Format("\nVisibility:{0}", node["visibility_statute_mi"].InnerText);

            t += String.Format("\nAltimeter:{0:F2}", Convert.ToDouble(node["altim_in_hg"].InnerText));

            if (node["sea_level_pressure_mb"] != null)
            {
                t += String.Format("\nSealevel Pressure:{0}", node["sea_level_pressure_mb"].InnerText);
            }

            if (node["wx_string"] != null)
            {
                t += String.Format("\nWX:{0}", node["wx_string"].InnerText);
            }

            if (node["corrected"] != null)
            {
                t += String.Format("\nCorrected:{0}", node["corrected"].InnerText);
            }

            if (node["auto"] != null)
            {
                t += String.Format("\nAuto:{0}", node["auto"].InnerText);
            }


            if (node["quality_control_flags"] != null)
            {
                foreach (XmlNode n in node["quality_control_flags"])
                {
                    if (n.Name == "auto_station")
                    {
                        t += String.Format("\nAuto Station:{0}", n.InnerText);
                    }

                    if (n.Name == "maintenance_indicator_on")
                    {
                        t += String.Format("\nMaintenance:{0}", n.InnerText);
                    }
                }
            }


            if (node["no_signal"] != null)
            {
                t += String.Format("\nNo Signal:{0}", node["no_signal"].InnerText);
            }

            if (node["lightning_sensor_off"] != null)
            {
                t += String.Format("\nLightning Sensor Off:{0}", node["lightning_sensor_off"].InnerText);
            }

            if (node["freezing_rain_sensor_off"] != null)
            {
                t += String.Format("\nFreezing Rain Sensor Off:{0}", node["freezing_rain_sensor_off"].InnerText);
            }

            if (node["present_weather_sensor_off"] != null)
            {
                t += String.Format("\nPresent Weather Sensor Off:{0}", node["present_weather_sensor_off"].InnerText);
            }

            if (node["sky_condition"] != null)
            {
                t += String.Format("\nSky:");

                // use SelectNodes to grab elements of the same name
                XmlNodeList nl = node.SelectNodes("sky_condition");

                foreach (XmlNode n in nl)
                {
                    foreach (XmlAttribute a in n.Attributes)
                    {
                        t += a.InnerText + " ";
                    }
                }
            }

            t += String.Format("\nFlight Category:{0}", node["flight_category"].InnerText);

            if (node["maxT_c"] != null)
            {
                t += String.Format("\nMax Temp:{0}", node["maxT_c"].InnerText);
            }

            if (node["minT_c"] != null)
            {
                t += String.Format("\nMin Temp:{0}", node["minT_c"].InnerText);
            }

            if (node["maxT24hr_c"] != null)
            {
                t += String.Format("\nMax Temp 24hr:{0}", node["maxT24hr_c"].InnerText);
            }

            if (node["minT24hr_c"] != null)
            {
                t += String.Format("\nMin Temp 24hr:{0}", node["minT24hr_c"].InnerText);
            }

            if (node["precip_in"] != null)
            {
                t += String.Format("\nPrecipitation:{0}", node["precip_in"].InnerText);
            }

            if (node["pcp3hr_in"] != null)
            {
                t += String.Format("\nPrecipitation 3hr:{0}", node["pcp3hr_in"].InnerText);
            }

            if (node["pcp6hr_in"] != null)
            {
                t += String.Format("\nPrecipitation 6hr:{0}", node["pcp6hr_in"].InnerText);
            }

            if (node["pcp24hr_in"] != null)
            {
                t += String.Format("\nPrecipitation 24hr:{0}", node["pcp24hr_in"].InnerText);
            }

            if (node["snow_in"] != null)
            {
                t += String.Format("\nSnow:{0}", node["snow_in"].InnerText);
            }

            if (node["vert_vis_ft"] != null)
            {
                t += String.Format("\nVertical Visibility:{0}", node["vert_vis_ft"].InnerText);
            }

            if (node["metar_type"] != null)
            {
                t += String.Format("\nMetar Type:{0}", node["metar_type"].InnerText);
            }

            t += String.Format("\nElevation:{0:F2}  ({1:F2})\n\n", stationInfo.elevation_m, (stationInfo.elevation_m * 3.2808399));

            return t;
        }













        public Double PressureAltitude(Double p)
        {
            return 145366.45 * (1 - Math.Pow(((33.8639 * p) / 1013.25), 0.190284));
        }

        public Double CloudBaseAGL(Double t, Double d, Char tp)
        {
            // 2.5 for celcius 4.4 for farenheit
            if (tp == 'C')
            {
                return ((t - d) / 2.5) * 1000.00;
            }

            else
            {
                return ((t - d) / 4.4) * 1000.00;
            }
        }

        public Double DensityAltitude(Temperature tc, Double pressureHg, Temperature dc)
        {
            // Find virtual temperature using temperature in kelvin and dewpoint in celcius
            var Tv = VirtualTemperature(tc, pressureHg, dc);

            // passing virtual temperature in Kelvin
            var Da = CalcDensityAltitude(pressureHg, Tv);

            return Da;
        }

        public Double VirtualTemperature(Temperature tc, Double pressureHg, Temperature dc)
        {
            // vapor pressure uses celcius
            var vp = 6.11 * Math.Pow(10, ((7.5 * dc.cValue) / (237.7 + dc.cValue)));

            var mbpressure = 33.8639 * pressureHg;

            // use temperature in Kelvin
            if (mbpressure != 0)
            {
                return tc.kValue / (1.0 - (vp / mbpressure) * (1.0 - 0.622));
            }

            return 0;
        }

        public Double CalcDensityAltitude(Double pressureHg, Double tv)
        {
            // virtual temperature comes in as Kelvin
            var tk = new Temperature('K', tv);

            // use virtual temperature as Rankine
            var p = (17.326 * pressureHg) / tk.rValue;

            // weather.gov and seems to be the most used
            return 145366.0 * (1.0 - (Math.Pow(p, 0.235)));

            // NWS
            //return 145442.16 * (1.0 - (Math.Pow(p, 0.235)));
        }

        public Double RelativeHumidity(Double d, Double t)
        {
            // Temperatures are celcius
            // =100*(EXP((17.625*TD)/(243.04+TD))/EXP((17.625*T)/(243.04+T)))
            return 100 * (Math.Exp((17.625 * d) / (243.04 + d)) / Math.Exp((17.625 * t) / (243.04 + t)));
        }

        public String FlipTimeDate(String str)
        {
            String[] stra = str.Split(" ");

            String[] part = stra[0].Split("-");

            var rstr = part[1];
            rstr += "-";

            rstr += part[2];
            rstr += "-";

            rstr += part[0];
            rstr += " ";

            rstr += stra[1];

            return rstr;
        }
    }

    public class StationInfo
    {
        public String station_id;
        public Double latitude;
        public Double longitude;
        public Double elevation_m;
        public String site;
        public String state;
        public String country;
        public String type;
    }

    public class Temperature
    {
        public Double fValue;
        public Double cValue;
        public Double kValue;
        public Double rValue;
        public Double vValue;

        public Temperature(Char tp, Double t)
        {
            switch (tp)
            {
            case 'C':
            {
                cValue = t;
                fValue = ConvertCtoF(cValue);
                kValue = ConvertCtoK(cValue);
                rValue = ConvertKtoR(kValue);

                break;
            }

            case 'F':
            {
                fValue = t;
                cValue = ConvertFtoC(fValue);
                kValue = ConvertCtoK(cValue);
                rValue = ConvertKtoR(kValue);

                break;
            }

            case 'K':
            {
                kValue = t;
                cValue = ConvertKtoC(kValue);
                fValue = ConvertCtoF(cValue);
                rValue = ConvertKtoR(kValue);

                break;
            }
            }
        }

        public Double ConvertCtoF(Double t)
        {
            return (t * (9 / 5)) + 32;
        }

        public Double ConvertCtoK(Double t)
        {
            return t + 273.15;
        }

        public Double ConvertFtoC(Double t)
        {
            return (t - 32) * (5 / 9);
        }

        public Double ConvertKtoR(Double t)
        {
            var tc = ConvertKtoC(t);

            var tf = ConvertCtoF(tc);

            return tf + 459.69;
        }

        public Double ConvertKtoC(Double t)
        {
            return t - 273.15;
        }
    }

}
