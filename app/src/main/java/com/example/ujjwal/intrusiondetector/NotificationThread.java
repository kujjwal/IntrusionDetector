package com.example.ujjwal.intrusiondetector;

/**
 * Name: Ujjwal Krishnamurthi
 * Program File: NotificationThread.java
 * Description: This class is a subclass of the Thread class, and is called to run by the Camera Activity class once the camera has
 * detected motion. Specifically, this runs in the background and posts to Firebase Database and Storage with the image and the
 * necessary descriptors for the backend to use. */

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.facebook.AccessToken;

import com.firebase.ui.auth.AuthUI;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class NotificationThread extends Thread {
    private Context context;
    private Activity activity;
    private final String TAG = "NotificationThread";
    private String choice;
    private Mat img;
    private GoogleSignInAccount account;
    private final String STORAGE_BUCKET = "gs://intrusiondetector-bbba9.appspot.com";
    private boolean createdFolder = false;
    private Task<DriveFolder> folder;
    private final String UUID = FirebaseAuth.getInstance().getCurrentUser().getUid();
    private boolean useDefault;
    private List<String> imgList = new ArrayList<>();
    private String downloadURL;

    NotificationThread(Activity activity, Context context, String choice, Mat img, boolean useDefault) {
        this.activity = activity;
        this.context = context;
        this.choice = choice;
        this.img = img;
        this.account = null;
        this.useDefault = useDefault;
    }

    NotificationThread(Activity activity, Context context, String choice, Mat img, @Nullable GoogleSignInAccount acct, boolean useDefault) {
        this.activity = activity;
        this.context = context;
        this.choice = choice;
        this.img = img;
        this.account = acct;
        this.useDefault = useDefault;
    }

    // Must be less than 2 seconds(Good practice in general), max should be two mins, but probs won't get that far.
    @Override
    public void run() {
        HashMap<String, Object> data;
        if(!useDefault) {
            switch(choice) {
                case AuthUI.EMAIL_PROVIDER:
                    Log.d(TAG, "Running Email");
                    data = AuthEmail();
                    defaultSaves(data);
                    break;
                case AuthUI.GOOGLE_PROVIDER:
                    Log.d(TAG, "Running Google");
                    data = AuthGoogle();
                    defaultSaves(data);
                    break;
                case AuthUI.FACEBOOK_PROVIDER:
                    Log.d(TAG, "Running FB");
                    data = AuthFacebook();
                    defaultSaves(data);
                    break;
                case AuthUI.PHONE_VERIFICATION_PROVIDER:
                    Log.d(TAG, "Running Phone");
                    data = AuthPhone();
                    defaultSaves(data);
                    break;
                default:
                    Log.d(TAG, "Running Default");
                    data = AuthDefault();
                    defaultSaves(data);
                    break;
            }
        } else {
            Log.d(TAG, "Running Default");
            data = AuthDefault();
            defaultSaves(data);
        }
    }
    private HashMap<String, Object> AuthEmail() {
        Log.d(TAG, "Email");
        // Send email to user containing images and intrusion
        HashMap<String, Object> data = serializeBitmap();
        //Toast.makeText(context, "When motion is detected, user will be notified via email", Toast.LENGTH_LONG).show();
        return data;
    }
    private HashMap<String, Object> AuthPhone() {
        // Text user the images
        Log.d(TAG, "Phone");
        HashMap<String, Object> data = serializeBitmap();
        //Toast.makeText(context, "When motion is detected, user will be notified via text and can reply for bot service answers", Toast.LENGTH_LONG).show();
        return data;
    }
    private HashMap<String, Object> AuthFacebook() {
        Log.d(TAG, "Facebook");
        HashMap<String, Object> data = serializeBitmap();
        Bitmap bitmap = Bitmap.createBitmap(img.width(), img.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(img, bitmap);
        AccessToken token = AccessToken.getCurrentAccessToken();
        //Toast.makeText(context, "When motion is detected, user will be notified on their timeline", Toast.LENGTH_LONG).show();
        return data;
    }
    private HashMap<String, Object> AuthGoogle() {
        Log.d(TAG, "Google");
        HashMap<String, Object> data = serializeBitmap();
        final byte[] serializedBitmap = (byte[]) data.get("Bitmap");
        final String date = (String) data.get("Date");
        //createFolderIfNecessaryAndAdd(serializedBitmap, date);
        //Toast.makeText(context, "When motion is detected, user will be notified via Google Drive and Email", Toast.LENGTH_LONG).show();
        return data;
    }
    private HashMap<String, Object> AuthDefault() {
        // Post to firebase and save to phone as backup copy
        Toast.makeText(context, "When motion is detected, data will be saved to phone and firebase", Toast.LENGTH_LONG).show();
        return serializeBitmap();
    }

    private void defaultSaves(HashMap<String, Object> data) {
        Log.d(TAG, "Starting defaultSaves");
        byte[] serializedBitmap = (byte[]) data.get("Bitmap");
        final String date = (String) data.get("Date");

        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference ref = storage.getReferenceFromUrl(STORAGE_BUCKET);
        String uri_path = "intrusions/" + UUID + "/" + date;
        final StorageReference imgRef = ref.child(uri_path);

        final UploadTask task = imgRef.putBytes(serializedBitmap);
        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                final Exception exception = e;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, "Image Failed to Upload to Firebase", Toast.LENGTH_LONG).show();
                        Log.d(TAG, "Failed to upload to firebase: " + exception.getMessage());
                    }
                });
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(final UploadTask.TaskSnapshot taskSnapshot) {
                downloadURL = taskSnapshot.getMetadata().getDownloadUrl().toString();
                imgList.add(downloadURL);
                updateDatabase(downloadURL, date);
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, "Image Uploaded Successfully to Firebase", Toast.LENGTH_LONG).show();
                        Log.d(TAG, "Image Successfully uploaded at url: " + taskSnapshot.getDownloadUrl().toString());
                    }
                });
            }
        }).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, "Image Uploading to Firebase", Toast.LENGTH_LONG).show();
                        Log.d(TAG, "In process of uploading");
                    }
                });
            }
        });
    }

    private void updateDatabase(final String URL, final String date) {
        Log.d(TAG, "Updating Database");
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        final DatabaseReference mainRef = database.getReference();
        final DatabaseReference curr_user = mainRef.child("users").child(UUID);
        //curr_user.setValue("User: " + UUID);
        curr_user.child("images").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG, "Value Change");
                String imgList = (String) dataSnapshot.getValue();
                List<String> imgs = new ArrayList<>();
                if(imgList != null) {
                    for (String img : imgList.split(",")) {
                        imgs.add(img.replace("[", "").replace("]", ""));
                    }
                }
                imgs.add(URL);
                curr_user.child("images").setValue(imgs.toString());
                curr_user.child("notify").setValue(true);
                curr_user.child("recentImg").setValue(URL);
                curr_user.child("lastImgUpload").setValue(date);
                if(FirebaseAuth.getInstance().getCurrentUser().getDisplayName() != null) {
                    curr_user.child("name").setValue(FirebaseAuth.getInstance().getCurrentUser().getDisplayName());
                } else {
                    curr_user.child("name").setValue("");
                }
                if(FirebaseAuth.getInstance().getCurrentUser().getEmail() != null) {
                    curr_user.child("email").setValue(FirebaseAuth.getInstance().getCurrentUser().getEmail());
                } else {
                    curr_user.child("email").setValue("");
                }
                if(FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber() != null) {
                    curr_user.child("phonenumber").setValue(FirebaseAuth.getInstance().getCurrentUser().getPhoneNumber());
                } else {
                    curr_user.child("phonenumber").setValue("");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private HashMap<String, Object> serializeBitmap() {
        String pattern = "dd-MM-yy-HH-mm-SS";
        String date = new SimpleDateFormat(pattern, Locale.US).format(new Date()); //Change so works internationally
        Bitmap bitmap = Bitmap.createBitmap(img.width(), img.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(img, bitmap);
        MediaStore.Images.Media.insertImage(context.getContentResolver(), bitmap, date + ".jpg", "Movement detected on " + date);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] serializedBitmap = baos.toByteArray();
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, "Image Saved to Phone", Toast.LENGTH_LONG).show();
            }
        });
        Log.d(TAG, "Saved img to phone with filename: " + date + ".jpg");
        HashMap<String, Object> data = new HashMap<>();
        data.put("Bitmap", serializedBitmap);
        data.put("Date", date);
        return data;
    }

    private void createFolderIfNecessaryAndAdd(final byte[] serializedBitmap, final String date) {
        final DriveResourceClient res_client = Drive.getDriveResourceClient(context, account);
        final Task<DriveFolder> rootFolder = res_client.getRootFolder();
        final Task<DriveContents> contentsTask = res_client.createContents();
        if(!createdFolder) {
            rootFolder.continueWithTask(new Continuation<DriveFolder, Task<DriveFolder>>() {
                @Override
                public Task<DriveFolder> then(@NonNull Task<DriveFolder> task) {
                    DriveFolder parent = task.getResult();
                    MetadataChangeSet changeSet = new MetadataChangeSet.Builder().setTitle("Intrusion Detector Images").setMimeType(DriveFolder.MIME_TYPE).setStarred(true).build();
                    folder = res_client.createFolder(parent, changeSet);
                    createdFolder = true;
                    return folder;
                }
            });
        } else {

            Tasks.whenAll(folder, contentsTask).continueWithTask(new Continuation<Void, Task<DriveFile>>() {
                @Override
                public Task<DriveFile> then(@NonNull Task<Void> task) {
                    DriveContents contents = contentsTask.getResult();
                    MetadataChangeSet mcs = new MetadataChangeSet.Builder().setTitle(date + ".jpg").setMimeType("image/jpeg").setStarred(true).build();
                    OutputStream os = contents.getOutputStream();
                    try {
                        os.write(serializedBitmap);
                    } catch (IOException ioe) {
                        Log.e(TAG, "Couldn't write File: I/O Error");
                    }
                    return res_client.createFile(folder.getResult(), mcs, contents);
                }
            });
        }
    }
}
