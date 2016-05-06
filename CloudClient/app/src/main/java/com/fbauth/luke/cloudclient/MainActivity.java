package com.fbauth.luke.cloudclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.microsoft.windowsazure.mobileservices.*;
import com.microsoft.windowsazure.mobileservices.authentication.MobileServiceAuthenticationProvider;
import com.microsoft.windowsazure.mobileservices.authentication.MobileServiceUser;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;
import com.microsoft.windowsazure.mobileservices.table.query.ExecutableJsonQuery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.widget.Toast;

import org.json.JSONObject;

import java.net.MalformedURLException;
import java.util.List;

import se.simbio.encryption.Encryption;

public class MainActivity extends Activity {

    // Create an object to connect to your mobile app service
    private MobileServiceClient mClient;
    public static final String SHAREDPREFFILE = "temp";
    public static final String USERIDPREF = "uid";
    public static final String TOKENPREF = "tkn";
    public static String Auth_Token = "";
    public static String Access_Token = "";
    public String jsonExp = "";
    //encryption library from https://github.com/simbiose/Encryption/blob/master/
    public byte[] iv = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,16,15};
    Encryption encryption = Encryption.getDefault("Lukesapp", "YourSalt", iv);

    // global variable to update a TextView control text
    TextView display;

    // simple stringbulder to store textual data retrieved from mobile app service table
    StringBuilder sb = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button logoutButton = (Button) findViewById(R.id.logoutButton);

        logoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                close();
            }
        });

        try {

            //my azure site
            mClient = new MobileServiceClient(
                    "https://lukefbauth.azurewebsites.net",
                    this
            );
            //only run if the user is connected to the internet
            if(isConnectedToInternet()){
                authenticate();
            }
            else{
                //warn user to connect to the internet and open the settings
                Toast.makeText(this, "Connect to the Internet!", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(this, MainActivity.class);
                Intent intent2 = new Intent(Settings.ACTION_SETTINGS);
                startActivity(intent);
                startActivity(intent2);
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    //check internet connection
    public boolean isConnectedToInternet() throws SecurityException{
        ConnectivityManager connectivity = (ConnectivityManager)getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null)
        {
            NetworkInfo[] info = connectivity.getAllNetworkInfo();
            if (info != null)
                for (int i = 0; i < info.length; i++)
                    if (info[i].getState() == NetworkInfo.State.CONNECTED)
                    {
                        return true;
                    }
        }
        return false;
    }

    public String encrypt(String msg){
        //encrypt the string
        String encrypted = encryption.encryptOrNull(msg);
        return encrypted;
    }

    public String decrypt(String msg){
        //decrypt the string
        String decrypted = encryption.decryptOrNull(msg);
        return decrypted;
    }

    private void authenticate() {
        //inspired by http://derekfoster.cloudapp.net/mc/workshopb2.htm
        if (loadUserTokenCache(mClient)) {
            new AsyncParseJson().execute();
        } else {

            ListenableFuture<MobileServiceUser> mLogin = mClient.login(MobileServiceAuthenticationProvider.Facebook);

            Futures.addCallback(mLogin, new FutureCallback<MobileServiceUser>() {
                @Override
                public void onFailure(Throwable exc) {
                    createAndShowDialog("You must log in using your Facebook credentials", "Failed to log in");
                }

                @Override
                public void onSuccess(MobileServiceUser user) {
                    createAndShowDialog(String.format(
                            //confirm log in
                            "You are now logged in - %1$2s",
                            user.getUserId()), "Success");

                    cacheUserToken(mClient.getCurrentUser());
                    Auth_Token = user.getAuthenticationToken();
                    new AsyncParseJson().execute();
                }

            });
        }
    }

    private void close(){
        finish();

    }

    private void createAndShowDialog(String message, String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message);
        builder.setTitle(title);
        builder.create().show();
    }

    private void cacheUserToken(MobileServiceUser user) {
        SharedPreferences prefs = getSharedPreferences(SHAREDPREFFILE, Context.MODE_PRIVATE);
        Editor editor = prefs.edit();
        editor.putString(USERIDPREF, user.getUserId());
        //cache the auth token and encrypt
        editor.putString(TOKENPREF, encrypt(user.getAuthenticationToken()));
        Toast.makeText(getApplicationContext(), "Cached and encrypted the login token", Toast.LENGTH_LONG).show();
        editor.commit();
    }

    private boolean loadUserTokenCache(MobileServiceClient client) {
        SharedPreferences prefs = getSharedPreferences(SHAREDPREFFILE, Context.MODE_PRIVATE);
        String userId = prefs.getString(USERIDPREF, "undefined");
        if (userId == "undefined")
            return false;
        //decrypt token after obtaining it from the cache
        String token = decrypt(prefs.getString(TOKENPREF, "undefined"));
        Toast.makeText(getApplicationContext(), "Decrypted the login token",Toast.LENGTH_LONG).show();
        if (token == "undefined")
            return false;

        MobileServiceUser user = new MobileServiceUser(userId);
        user.setAuthenticationToken(token);
        Auth_Token = user.getAuthenticationToken();
        client.setCurrentUser(user);

        return true;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public class fetcher {
        final String TAG = "JsonParser.java";
        String json = "";

        public String getJSONFromUrl(Context context, String url, String Auth_Token) {
            try {
                URL u = new URL(url);

                HttpURLConnection restConnection = (HttpURLConnection) u.openConnection();

                //request data from azure
                restConnection.setRequestMethod("GET");
                restConnection.setRequestProperty("X-ZUMO-AUTH", Auth_Token);
                restConnection.addRequestProperty("content-length", "0");
                restConnection.setUseCaches(false);
                restConnection.setAllowUserInteraction(false);
                restConnection.setConnectTimeout(10000);
                restConnection.setReadTimeout(10000);
                restConnection.connect();

                int status = restConnection.getResponseCode();

                switch (status) {
                    case 200:
                    case 201:
                        // live connection to  REST service is established here using getInputStream() method
                        BufferedReader br = new BufferedReader(new InputStreamReader(restConnection.getInputStream()));

                        // create a new string builder to store json data returned from the REST service
                        StringBuilder sb = new StringBuilder();
                        String line = "";

                        // loop through returned data line by line and append to stringbuffer 'sb' variable
                        while ((line = br.readLine()) != null) {
                            sb.append(line + "\n");
                        }
                        br.close();
                        //JSON returned as a JSONObject
                        try {
                            json = sb.toString();
                            json = json.substring(json.indexOf(":") + 1);
                            json = json.substring(0, json.indexOf(","));
                            json = json.replace("\"", "");
                            /*jsonExp = sb.toString();
                            jsonExp = jsonExp.substring(json.indexOf(":"));
                            jsonExp = jsonExp.substring(json.indexOf(":") + 1);
                            jsonExp = jsonExp.substring(0, json.indexOf(","));*/
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing data " + e.toString());
                            return null;
                        }

                        return json;
                }
                // HTTP 200 and 201 error handling from switch statement
            } catch (MalformedURLException ex) {
                Log.e(TAG, "Malformed URL ");
            } catch (IOException ex) {
                Log.e(TAG, "IO Exception ");
            }
            return json = "";
        }
    }

    public class FbProfile {
        // Inspired by "Create a java class for connecting to a REST service" http://derekfoster.cloudapp.net/mc/workshop4.htm

        final String TAG = "JsonParser.java";
        // where the returned json data from service will be stored when downloaded
        JSONObject fbdata  = new JSONObject();

        // Parses JSON
        public JSONObject getFBJSONFromUrl(Context context, String url, String token) {

            try {
                // this code block represents/configures a connection to your REST service
                // it also represents an HTTP 'GET' request to get data from the REST service, not POST!
                URL u = new URL(url);
                HttpURLConnection restConnection = (HttpURLConnection) u.openConnection();
                //Verify Credentials
                restConnection.setRequestMethod("GET");
                restConnection.addRequestProperty("content-length", "0");
                restConnection.setUseCaches(false);
                restConnection.setAllowUserInteraction(false);
                restConnection.setConnectTimeout(10000);
                restConnection.setReadTimeout(10000);
                restConnection.connect();
                int status = restConnection.getResponseCode();

                // switch statement to catch HTTP 200 and 201 errors
                switch (status) {
                    case 200:
                    case 201:
                        // live connection to  REST service is established here using getInputStream() method
                        BufferedReader br = new BufferedReader(new InputStreamReader(restConnection.getInputStream()));

                        // create a new string builder to store json data returned from the REST service
                        StringBuilder sb = new StringBuilder();
                        String line = "";

                        // loop through returned data line by line and append to stringbuffer 'sb' variable
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        br.close();
                        //JSON returned as a JSONObject
                        try {
                            fbdata  = new JSONObject(sb.toString());
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing data 2nd pull " + e.toString());

                            return null;
                        }

                        return fbdata;
                }
                // HTTP 200 and 201 error handling from switch statement
            } catch (MalformedURLException ex) {
                Log.e(TAG, "Malformed URL ");
            } catch (IOException ex) {
                Log.e(TAG, "IO Exception ");
            }
            return fbdata;
        }
    }


    //inspired by http://www.androidbegin.com/tutorial/android-download-image-from-url/
    private class DownloadImage extends AsyncTask<String, Void, Bitmap> {
        public ImageView Image; // Declare a variable to store the Imageview
        public DownloadImage(ImageView bmImage) {
            this.Image = bmImage;
        }

        protected Bitmap doInBackground(String... urls) {
            String urldisplay = urls[0];
            Bitmap Icon = null;
            try {
                InputStream in = new java.net.URL(urldisplay).openStream();
                Icon = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Log.e("Error", e.getMessage());
                e.printStackTrace();
            }
            return Icon;
        }

        protected void onPostExecute(Bitmap result) {
            Image.setImageBitmap(result);
        }
    }

    public class AsyncParseJson extends AsyncTask<String, String, String> {
        //get access token from the .auth/me of the azure service
        private static final String yourServiceUrl = "https://lukefbauth.azurewebsites.net/.auth/me";
        @Override
        protected void onPreExecute() {

        }

        @Override
        protected String doInBackground(String... arg0){
            try{
                fetcher jParser = new fetcher();
                //call the parser from within the fetcher class to retrieve facebook auth token
                Access_Token = jParser.getJSONFromUrl(MainActivity.this, yourServiceUrl, Auth_Token);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String temp) {
            new AsyncParseFbJson().execute();
            return;
        }
    }

    public class AsyncParseFbJson extends AsyncTask<String, String, String> {
        //call facebook graph api
        private final String yourServiceUrl =  ("https://graph.facebook.com/me?fields=id,name,gender,age_range&access_token="+Access_Token);
        JSONObject FBJSON = new JSONObject();

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected String doInBackground(String... arg0 ) {
            try {
                // create new instance of the Facebook fetcher class
                FbProfile jParser = new FbProfile();

                //call the facebook parser for the graph api using the authentication token from azure
                FBJSON = jParser.getFBJSONFromUrl(MainActivity.this, yourServiceUrl, Auth_Token);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            //get all the boxes to put data into
            TextView data1 = (TextView) findViewById(R.id.textView);
            TextView data2 = (TextView) findViewById(R.id.textView2);
            TextView data3 = (TextView) findViewById(R.id.textView3);
            ImageView image = (ImageView) findViewById(R.id.imageView);
            try {
                //extract details and place them in relevant areas
                data1.setText(FBJSON.getString("name"));
                data2.setText(FBJSON.getString("gender"));
                data3.setText(FBJSON.getString("age_range").substring(7,(FBJSON.getString("age_range").length()-1)));
                String id = FBJSON.getString("id");
                new DownloadImage(image).execute("https://graph.facebook.com/" + id + "/picture?type=large");
            }
            catch(Exception e)
            {
                Log.e("", "" + yourServiceUrl);
            }
            return;
        }
    }

}
