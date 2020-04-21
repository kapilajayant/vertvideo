package com.jayant.pureatyvideo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.SnapHelper;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.jayant.pureatyvideo.models.MediaObject;
import com.jayant.pureatyvideo.util.Resources;
import com.jayant.pureatyvideo.util.VerticalSpacingItemDecorator;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "urlVideo";
    private VideoPlayerRecyclerView mRecyclerView;
    private static final int REQUEST_VIDEO_CAPTURE = 1;
    private StorageReference mStorageRef;
    ArrayList<MediaObject> mediaObjects;
    VideoPlayerRecyclerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mStorageRef = FirebaseStorage.getInstance().getReference();
        updateRecyclerView();

        // findViewById(R.id.floatingActionButton).setOnClickListener(new View.OnClickListener() {
        //     @Override
        //     public void onClick(View view) {
        //         Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        //         if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
        //             startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
        //         }
        //     }
        // });

        mRecyclerView = findViewById(R.id.recycler_view);
        initRecyclerView();

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == REQUEST_VIDEO_CAPTURE && resultCode == RESULT_OK) {
            Uri videoUri = intent.getData();
            String videoUrl = uploadToFirebase(videoUri);
//            addUrlToDatabase(videoUrl);
        }
    }

    private void addUrlToDatabase(String videoUrl) {
        // Write a message to the database
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        database.setPersistenceEnabled(true);
        DatabaseReference myRef = database.getReference("videos");
        String s_id = myRef.push().getKey();
        if (s_id != null) {
            myRef.child(s_id).setValue(videoUrl);
            Toast.makeText(this, "Saved Successfully", Toast.LENGTH_SHORT).show();
        }
        else {
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        }
    }

    private String uploadToFirebase(Uri videoUri) {

        final String[] videoUrl = new String[1];
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Uploading...");
        progressDialog.show();
        final StorageReference videosRef = mStorageRef.child("videos/hui--"+ UUID.randomUUID().toString());

        UploadTask uploadTask = (UploadTask) videosRef.putFile(videoUri)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        // Get a URL to the uploaded content
                        progressDialog.dismiss();
                        Toast.makeText(MainActivity.this, "Uploaded", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        // Handle unsuccessful uploads
                        // ...
                        Toast.makeText(MainActivity.this, exception.toString(), Toast.LENGTH_LONG).show();
                        progressDialog.dismiss();
                    }
                });
        Task<Uri> urlTask = uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
            @Override
            public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                if (!task.isSuccessful()) {
                    throw task.getException();
                }

                // Continue with the task to get the download URL
                return videosRef.getDownloadUrl();
            }
        }).addOnCompleteListener(new OnCompleteListener<Uri>() {
            @Override
            public void onComplete(@NonNull Task<Uri> task) {
                if (task.isSuccessful()) {
                    Uri downloadUri = task.getResult();
                    videoUrl[0] = downloadUri.toString();
                    FirebaseDatabase database = FirebaseDatabase.getInstance();
                    DatabaseReference myRef = database.getReference();
                    String s_id = myRef.push().getKey();
                    myRef.child(s_id).setValue(videoUrl[0]);
                    startActivity(new Intent(MainActivity.this, MainActivity.class));
                    finish();
//                    Log.i("downloadUrl", videoUrl[0]);
//                    updateRecyclerView();
//                    Toast.makeText(MainActivity.this, downloadUri.toString(), Toast.LENGTH_LONG).show();
                } else {
                    // Handle failures
                    // ...
                }
            }
        });

        return videoUrl[0];

    }

    private void initRecyclerView(){
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(layoutManager);
        VerticalSpacingItemDecorator itemDecorator = new VerticalSpacingItemDecorator(10);
        mRecyclerView.addItemDecoration(itemDecorator);

        SnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(mRecyclerView);

        mediaObjects = new ArrayList<MediaObject>(Arrays.asList(Resources.MEDIA_OBJECTS));
        mRecyclerView.setMediaObjects(mediaObjects);
        adapter = new VideoPlayerRecyclerAdapter(mediaObjects, initGlide());
        mRecyclerView.setAdapter(adapter);
    }

    private void updateRecyclerView()
    {
        List<String> keysList = new ArrayList<>();
        final FirebaseDatabase database = FirebaseDatabase.getInstance();
        final DatabaseReference myRef = database.getReference();
        myRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildKey) {
                Log.i("keyss", dataSnapshot.getKey());
                getVideoUrl(dataSnapshot.getKey(), myRef);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void getVideoUrl(String key, DatabaseReference myRef) {

        myRef.child(key).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.i("keyss", dataSnapshot.getValue(String.class));
                mediaObjects.add(new MediaObject("New Video", dataSnapshot.getValue(String.class), "null", "New Video Uploaded"));
                adapter.notifyItemInserted(mediaObjects.size());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    private RequestManager initGlide(){
        RequestOptions options = new RequestOptions()
                .placeholder(R.drawable.white_background)
                .error(R.drawable.white_background);

        return Glide.with(this)
                .setDefaultRequestOptions(options);
    }

    @Override
    protected void onDestroy() {
        if(mRecyclerView!=null)
            mRecyclerView.releasePlayer();
        super.onDestroy();
    }

}
