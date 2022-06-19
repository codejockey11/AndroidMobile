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
            // no need for ime actions in the layout as .Net triggers the editor action when enter is pressed
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

            metar = new List<String>();

            AsyncHttpRequest(metar, "https://www.aviationweather.gov/adds/dataserver_current/httpparam?dataSource=metars&requestType=retrieve&format=xml&hoursBeforeNow=1&stationString=" + stationIdent.Text);

            foreach (String s in metar)
            {
                XmlDocument xmlDoc = new XmlDocument();
                xmlDoc.LoadXml(s);

                string xpath = "response/data/METAR";
                var nodes = xmlDoc.SelectNodes(xpath);

                foreach (XmlNode childrenNode in nodes)
                {
                    textOut.Text += childrenNode["raw_text"].InnerText + "\n";

                    textOut.Text += "\n";
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

                foreach (XmlNode childrenNode in nodes)
                {
                    textOut.Text += childrenNode["raw_text"].InnerText;

                    textOut.Text += "\n\n";
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
    }
}
