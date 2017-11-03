package com.demo.juliet;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {
    private final static String DEBUG_TAG = "MainActivity";

    private final static String SERVER_ADDRESS_PREF_KEY = "ServerAddressPrefKey";

    private ExecutorService mExecutorService = Executors.newFixedThreadPool(1);
    private Button mTakePictureButton;
    private TextView mStatusTextView;
    private EditText mServerAddressEditText;
    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;

        mStatusTextView = (TextView) findViewById(R.id.statusTextView);
        mServerAddressEditText = (EditText) findViewById(R.id.serverAddressEditText);
        mServerAddressEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {

                    SharedPreferences pref = getApplicationContext().getSharedPreferences("MyPref", 0); // 0 - for private mode
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putString(SERVER_ADDRESS_PREF_KEY,
                            mServerAddressEditText.getText().toString()); // Storing server address
                    editor.commit();
                    Log.d(DEBUG_TAG, "Server address saved: " + mServerAddressEditText.getText().toString());

                    // Hide keyboard
                    InputMethodManager inputManager = (InputMethodManager)
                            mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.toggleSoftInput(0, 0);
                    return true;
                }
                return false;
            }
        });

        mTakePictureButton = (Button) findViewById(R.id.takePictureButton);
        mTakePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Log.d(DEBUG_TAG, "onClick called");
                String serverAddress = mServerAddressEditText.getText().toString();
                if (serverAddress.length() > 0) {
                    sendTakePictureRequest(serverAddress);
                } else {
                    updateStatus("Enter valid IP address");
                }
            }
        });
    }

    @Override
    public void onResume(){
        super.onResume();

        SharedPreferences pref = getApplicationContext().getSharedPreferences("MyPref", 0); // 0 - for private mode
        String serverAddress = pref.getString(SERVER_ADDRESS_PREF_KEY, ""); // getting ServerAddress
        mServerAddressEditText.setText(serverAddress);
    }

    private void updateStatus(final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatusTextView.setText(status);
            }
        });
    }

    private void sendTakePictureRequest(final String targetAddress) {

        mExecutorService.execute(new Runnable() {
            public void run() {
                try {
                    updateStatus("Send TakePicture request");
                    URL url = new URL("http://" + targetAddress + ":55555");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
//                    conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
//                    conn.setRequestProperty("Accept","application/json");
//                    conn.setDoOutput(true);
//                    conn.setDoInput(true);


                    DataOutputStream os = new DataOutputStream(conn.getOutputStream());

                    os.flush();
                    os.close();

                    Log.i(DEBUG_TAG, String.valueOf(conn.getResponseCode()));
                    Log.i(DEBUG_TAG , conn.getResponseMessage());

                    if (conn.getResponseCode() == 204) {
                        updateStatus("Request succeeded");
                    } else {
                        updateStatus("Request failed. Status code: " + conn.getResponseCode());
                    }

                    conn.disconnect();
                } catch (Exception e) {
                    updateStatus("Failed to send request");

                    e.printStackTrace();
                }
            }
        });
    }
}
