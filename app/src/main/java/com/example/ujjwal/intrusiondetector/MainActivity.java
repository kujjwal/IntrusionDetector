package com.example.ujjwal.intrusiondetector;

/**
 * Name: Ujjwal Krishnamurthi
 * Program File: MainActivity.java
 * Description: This Activity is the first activity which is opened, and provides all of the authentication to the user, so the user
 * can sign in any way they choose and through any process they choose. This activity handles all that action, and also creates a unique
 * user id for any user which signs in.*/

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.facebook.CallbackManager;
import com.facebook.FacebookSdk;
import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.auth.UserInfo;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private String TAG = "MainActivity";
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks verificationCallback;
    private FirebaseAuth auth;
    private FirebaseUser user;
    private List<AuthUI.IdpConfig> providers = Arrays.asList(
            new AuthUI.IdpConfig.GoogleBuilder().build(),
            new AuthUI.IdpConfig.FacebookBuilder().build(),
            new AuthUI.IdpConfig.EmailBuilder().build(),
            new AuthUI.IdpConfig.PhoneBuilder().build()
    );
    private static final int REQUEST_CODE = 456;
    private static final String FIREBASE_URL = "intrusiondetector-bbba9.firebaseapp.com";
    private CallbackManager callbackManager;
    private final String PREFS_FILE = "prefs";
    private SharedPreferences settings;


    // This is the onCreate method, which is called when the activity is first created
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // If there was a previous instantiation of the activity, I restore it here
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            onRestoreInstanceState(savedInstanceState);
        }
        // Since the app requires permissions for the camera and authentication, I must ask for them here
        askPermissions();
        // I get a default settings file, which persists and saves on the phone even after the app is closed
        // This allows me to save the UUID and check when the user last used the app–customizing the experience
        settings = getApplicationContext().getSharedPreferences(PREFS_FILE, 0);

        // Setting preferences for the user ID
        if(settings.getBoolean("first_time", true)) {
            // First time, generate UUID(User ID)
            Log.d(TAG, "First Time");
            settings.edit().putBoolean("first_time", false).apply();
            settings.edit().putString("UUID", generateID()).apply();
        }
        // Creating the firebase authentication objects–I am checking to see if there is already a user signed in to speed up the authentication process
        auth = FirebaseAuth.getInstance();
        user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            // Already signed in, move to next activity, which is the Camera Activity
            Intent intent = new Intent(MainActivity.this, CameraActivity.class);
            String providerID = "";
            for (UserInfo userInfo : user.getProviderData()) {
                providerID = userInfo.getProviderId();
            }
            // Here, I send some data to the next activity to make it easier to do some background authentication and camera work
            intent.putExtra("Provider", providerID);
            intent.putExtra("UUID", settings.getString("UUID", ""));
            startActivity(intent);
        }
        // Since you can also sign into facebook on the app, I must initialize this here
        callbackManager = CallbackManager.Factory.create();
        FacebookSdk.sdkInitialize(this);
        // This method starts the authentication process, which outputs to the onActivityResult() method
        startActivityForResult(AuthUI.getInstance().createSignInIntentBuilder().setAvailableProviders(providers).build(), REQUEST_CODE);
    }
    // This method asks for permissions that the app may need to use
    @TargetApi(23)
    protected void askPermissions() {
        String[] permissions = {
                "android.permission.GET_ACCOUNTS",
                "android.permission.READ_PROFILE",
                "android.permission.READ_CONTACTS",
                "android.permission.INTERNET",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.CAMERA"
        };
        int requestCode = 200;
        requestPermissions(permissions, requestCode);
    }

    // This is called after the authentication process, and signifies the end of the activity's lifecycle
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Check if the authentication went successfully
        if (resultCode == RESULT_OK) {
            // This returns the current user
            user = auth.getCurrentUser();
            String providerID = "";
            for (UserInfo userInfo : user.getProviderData()) {
                // This gets the provider ID, so I can see how the user signed in(via phone, or email, or through Google etc.)
                providerID = userInfo.getProviderId();
            }
            // Check whether they signed in through phone
            if(providerID.equals(AuthUI.PHONE_VERIFICATION_PROVIDER)) {
                // This is a verification callback, and is necessary for two factor authentication
                // The app sends a text message with a certain code to your phone, just to set it up, and this is the callback for when the code is put back into the phone
                // This allows an extra layer of security, and after this callback, is the same as the other processes
                verificationCallback = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential phoneAuthCredential) {
                        // Then are signed in now–we can go to next activity
                        Log.d(TAG, "Phone Authentication");
                        Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                        intent.putExtra("Provider", AuthUI.PHONE_VERIFICATION_PROVIDER);
                        intent.putExtra("UUID", settings.getString("UUID", ""));
                        startActivity(intent);
                    }

                    @Override
                    public void onVerificationFailed(FirebaseException e) {
                        Toast.makeText(getApplicationContext(), "Phone Verification incomplete or unable to process. Try again later", Toast.LENGTH_LONG).show();
                    }
                };
            // Signed in through Google(Google is special as we need to tweak a few of the parameters)
            } else if (providerID.equals(AuthUI.GOOGLE_PROVIDER)) {
                Log.d(TAG, "Google Authentication");
                // We need to add a googlesigninresult as google requires some additional authentication in order to use its services
                GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                // Passing data to the next activity to make it easier to do background authentication
                intent.putExtra("Provider", AuthUI.PHONE_VERIFICATION_PROVIDER);
                intent.putExtra("UUID", settings.getString("UUID", ""));
                if(result != null) {
                    // Writes the google account to the next activity
                    GoogleSignInAccount acct = result.getSignInAccount();
                    intent.putExtra("Account", acct);
                }
                startActivity(intent);
            //Signed in through other authentication
            } else {
                Log.d(TAG, "Other Authentication");
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                // Passing data to the next activity to make it easier to do background authentication
                intent.putExtra("Provider", providerID);
                intent.putExtra("UUID", settings.getString("UUID", ""));
                startActivity(intent);
            }
        }
    }

    // This generates a unique user ID (UUID) based on the user's firebase credentials, so this wouldn't change even if the user signed in on a different device
    @NonNull
    private String generateID() {
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }
}
