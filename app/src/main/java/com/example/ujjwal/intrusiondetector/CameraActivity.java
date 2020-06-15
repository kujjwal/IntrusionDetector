package com.example.ujjwal.intrusiondetector;

// Name: Ujjwal Krishnamurthi
// Program File: CameraActivity.java
// Description: This file is the activity which uses the camera to capture motion. This activity calls the Android Camera and
// uses opencv algorithms to alert the user to any possibility of motion.


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CameraActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2{

    private static final String TAG = "MainActivity";
    private CameraBridgeViewBase cameraView;
    private int width;
    private int height;
    private volatile boolean first = true;
    private volatile Mat firstFrame;
    private boolean googleSignIn;
    private String providerID = "";
    private GoogleSignInAccount acct = null;
    private int runNum = 1;
    private long timeStamp = System.currentTimeMillis();
    private long frameTimestamp;
    private boolean startingFrame = true;

    private boolean useDefault = false; //Can be put into menu option

    // This callback loads the OpenCV library
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    Log.i(TAG, "OpenCV loaded successfully");
                    cameraView.enableView();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    // This method is called when the activity is created, and automatically sets parameters to make the phone opencv-ready
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        Log.d(TAG, "Started onCreate");
        // Makes sure the app doesn't turn off while the camera is on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Loads the OpenCV library and C dependencies
        if (!OpenCVLoader.initDebug()) {
            Log.e(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), not working.");
        } else {
            Log.d(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), working.");
        }
        //Set the camera view listener and opens the camera to detect motion
        cameraView = findViewById(R.id.surfaceView);
        cameraView.setVisibility(SurfaceView.VISIBLE);
        cameraView.setCvCameraViewListener(this);
        cameraView.setMaxFrameSize(600, 600);

        //Firebase authentication, makes sure the user is still authenticated, or reroutes to the MainActivity
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        FirebaseAuth.getInstance().addAuthStateListener(new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                if(firebaseAuth.getCurrentUser() == null) {
                    Log.d(TAG, "Not signed in");
                    Intent intent = new Intent(CameraActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                }
            }
        });
        // Checks whether the user is still authenticated in another way(if the user object does not exist, and if the user had
        // already opened the activity, but the authentication token expired.)
        if(user == null) {
            Toast.makeText(getApplicationContext(), "User not signed in, restarting application", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(CameraActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
        // Sends message to user to tell user about the UUID.
        userMessage();
    }

    /*private void sendEmail() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("message/rfc822"); //To show the sender intent with only email client
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{EMAIL_ADDRESS});
        intent.putExtra(Intent.EXTRA_SUBJECT, "Bot Email");
        intent.putExtra(Intent.EXTRA_TEXT, "UUID: " + getIntent().getStringExtra("UUID"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(Intent.createChooser(intent, "Email Sender"));
        } catch (ActivityNotFoundException anfe) {
            Toast.makeText(this, "Email client needs to be signed in or installed, please install on your phone first.", Toast.LENGTH_LONG).show();
        }
    }*/

    private void userMessage() {
        // Creates an alert dialog to tell user(blocks other processes)
        useDefault = false;
        googleSignIn = !(getIntent().getParcelableExtra("Account") == null);
        providerID = getIntent().getStringExtra("Provider");
        AlertDialog.Builder builder = new AlertDialog.Builder(CameraActivity.this);
        builder.setMessage("To authenticate with the bot, your id has been copied to your clipboard, please paste it into your current phone to authenticate.")
                .setTitle("User Notification");
        // Sets an "Ok" button to the dialog so the user could paste the UUID into their other phone
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                // Adds the string to their clipboard(So they can paste it into texts or email to send to other phone)
                ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = ClipData.newPlainText("simple text", "UUID: " + getIntent().getStringExtra("UUID"));
                clipboardManager.setPrimaryClip(clipData);
                Snackbar.make(findViewById(R.id.cameraActivity), "Copied UUID to clipboard, copy this ID to other phone to authenticate; please paste it in exactly as it is formatted on your clipboard.", Snackbar.LENGTH_LONG).show();
            }
        });
        // If they say no, then the phone will only use the default authentication, not based on their ID
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Cannot authenticate with bot, add intent bundle parameter for Notification Thread
                useDefault = true;
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();

    }
    // Creates the option menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu); // May create a settings tab which we don't want
        Log.i(TAG, "Created options menu");
        MenuInflater menuInflater = getMenuInflater();
        // Inflates the XML for the menu written in the Menu layout
        menuInflater.inflate(R.menu.options_menu, menu);
        return true;
    }
    // This is called when an options item is selected
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Check whether the user signed out
        if(item.getItemId() == R.id.signout_menu) {
            // Sign out from firebase and reroute to MainActivity
            Log.d(TAG, "Signing Out");
            FirebaseAuth auth = FirebaseAuth.getInstance();
            FirebaseUser user = auth.getCurrentUser();
            if(user != null) {
                AuthUI.getInstance().signOut(this).addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        Intent intent = new Intent(CameraActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                    }
                });
            }
            return true;
        // Whether user selected the help menu
        } else if(item.getItemId() == R.id.help_menu) {
            Log.d(TAG, "Help Menu");
            // Creates an Alert Dialog to tell the user about the app
            AlertDialog.Builder builder = new AlertDialog.Builder(CameraActivity.this);
            builder.setMessage(R.string.help_menu);
            builder.setTitle("Help Menu");
            builder.setCancelable(true);
            AlertDialog dialog = builder.create();
            dialog.show();
            return true;
        }
        else {
            return super.onOptionsItemSelected(item);
        }
    }
    // When the app is started(from previous app exit) checks to see whether user is signed in
    @Override
    protected void onStart() {
        super.onStart();
        FirebaseAuth.getInstance().addAuthStateListener(new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                if(firebaseAuth.getCurrentUser() == null) {
                    Log.d(TAG, "Not signed in");
                    Intent intent = new Intent(CameraActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                }
            }
        });
    }
    // When the camera frames start coming for the algorithm to parse
    @Override
    public void onCameraViewStarted(int width, int height) {
        if(this.width != width || this.height != height) {
            this.height = height;
            this.width = width;
        }
    }
    // When the app is closed and when frames stop coming
    @Override
    public void onCameraViewStopped() {
        first = false;
        firstFrame = new Mat();
    }
    // Parses each camera frame separately
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        // If first frame, then use this frame as a reference point for future frames
        if(first) {
            firstFrame = inputFrame.gray();
            Imgproc.resize(firstFrame, firstFrame, new Size(500, firstFrame.height()));
            first = false;
            frameTimestamp = System.currentTimeMillis();
            timeStamp = System.currentTimeMillis();
        } else {
            // Process this frame, however run on a separate thread, in order to make sure that the main thread doesn't do too much work.
            Mat frame = inputFrame.gray();
            ProcessorThread processor = new ProcessorThread(frame, inputFrame);
            processor.start();
        }
        return inputFrame.rgba();
    }
    // When the app is paused, stop cameraview to prevent memory leakage
    @Override
    protected void onPause() {
        super.onPause();
        if(cameraView != null) {
            cameraView.disableView();
        }
    }
    // Check whether the OpenCV library has continued to load when the app resumes(After previous exit)
    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }
    // Once user leaves the app, destroy the camera view to prevent memory leakage
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraView != null)
            cameraView.disableView();
    }
    // This is the processor thread, which processes each frame independently
    private class ProcessorThread extends Thread {
        private final String TAG = "ProcessorThread";
        private Mat currFrame;
        private final double contSize = 500.0; //Default threshold for size of movement(Will detect for a human, but not for a small animal)
        private CameraBridgeViewBase.CvCameraViewFrame inputFrame;

        public ProcessorThread(Mat frame, CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
            currFrame = frame;
            this.inputFrame = inputFrame;
        }

        /**
         * Algorithm:
         * Start time
         * Resize frame to smaller scale and standardize
         * CVT to Grayscale as we don't need rgba for computations(Also quicker as this happens every frame)
         * Gaussian Blur to remove small amounts of noise
         * Background Subtraction from first frame
         * Perform Binary Threshold to make all differences between images clear
         * Dilate Thresholded img to fill in any holes or get rid of residual noise
         * Find contours in thresholded img
         * Select the biggest contour and if bigger than set size, then we have proof of motion
         * Send to FileThread to save file and upload to firebase storage securely
         * Else, average out the frame and set value to equal first frame, and end threading
         * End time, calculate time differential and log
         * */
        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            Mat currFrameImg = currFrame.clone();
            Imgproc.resize(currFrameImg, currFrameImg, new Size(500, currFrameImg.height()));
            // Make both firstFrame and currFrameImg same channels
            conversions(Arrays.asList(firstFrame, currFrameImg), CvType.CV_64F, CvType.CV_64F);
            Log.d(TAG, "firstFrame channels: " + firstFrame.type());
            Log.d(TAG, "currFrameImg channels: " + currFrameImg.type());

            Imgproc.accumulateWeighted(firstFrame, currFrameImg, 0.5);
            Mat gauss = new Mat();
            Imgproc.GaussianBlur(currFrameImg, gauss, new Size(21,21), 0);
            Mat change = new Mat();
            Core.absdiff(gauss, firstFrame, change);

            Mat threshold = new Mat();
            Imgproc.threshold(change, threshold, 5, 255, Imgproc.THRESH_BINARY);
            Imgproc.dilate(threshold, threshold, new Mat(), new Point(), 2);

            List<MatOfPoint> contours = new ArrayList<>();
            conversions(Arrays.asList(threshold), CvType.CV_8UC1);
            Imgproc.findContours(threshold, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            for(MatOfPoint contour : contours) {
                if(Imgproc.contourArea(contour) > contSize) {
                    long currTime = System.currentTimeMillis();
                    if(currTime - timeStamp >= 10000 || startingFrame) {
                        timeStamp = System.currentTimeMillis();
                        // Starts the notification thread to notify user about the image
                        if(googleSignIn) {
                            Log.d(TAG, "Running Thread");
                            NotificationThread nt = new NotificationThread(CameraActivity.this, getApplicationContext(), providerID, inputFrame.rgba(), acct, useDefault);
                            nt.start();
                        } else {
                            Log.d(TAG, "Running Thread");
                            NotificationThread nt = new NotificationThread(CameraActivity.this, getApplicationContext(), providerID, inputFrame.rgba(), useDefault);
                            nt.start();
                        }
                        if(startingFrame) startingFrame = false;
                    }
                    //Rect rect = Imgproc.boundingRect(contour);
                    //Imgproc.rectangle(inputFrame.rgba(), new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(255, 0, 0), 3);
                    break;
                }
            }
            long endTime = System.currentTimeMillis();
            long time = (endTime - startTime);
            Log.d(TAG, "Processing time for run #" + runNum + ": " + time);
            runNum++;
        }

        private void conversions(List<Mat> mats, int... types) {
            int i = 0;
            for(Mat m : mats) {
                m.convertTo(m, types[i]);
                i++;
            }
        }
    }
}
