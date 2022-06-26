package com.example.shop_assistant;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.example.shop_assistant.databinding.ActivityBarcodeScanningBinding;
import com.example.shop_assistant.databinding.ActivityProductInfoBinding;
import com.google.android.gms.tasks.OnCompleteListener;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.InputStream;
import java.net.URI;
import java.util.Map;

public class ProductInfoActivity extends AppCompatActivity {

    private ActivityProductInfoBinding binding;
    private static final String TAG = ProductInfoActivity.class.getName();

    // Get reference to the database
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // binding the current activity for easy referencing its internal Views
        binding = ActivityProductInfoBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        // retrieving data from the BarcodeScanningActivity
        Intent intent = getIntent();
        String str = intent.getStringExtra("barcode");

        // getting data from the specific document ID under products collection
        db.collection("products").document("5900379137176")
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {

                            // getting a HashMap of data
                            Map results = task.getResult().getData();

                            // setting the product image from downloaded URL
                            new DownloadImageTask((ImageView) findViewById(R.id.imageView))
                                    .execute(results.get("imageurl").toString());

                            // setting the productName, productPrice and description values from firestore DB
                            binding.productNameView.setText(results.get("productname").toString());
                            binding.productPriceView.setText(results.get("price").toString());
                            binding.descriptionView.setText(results.get("description").toString());

                        } else {
                            Log.w(TAG, "Error getting documents.", task.getException());
                        }
                    }
                });
    }

    // The async task method for downloading the image from URL

    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        ImageView bmImage;

        public DownloadImageTask(ImageView bmImage) {
            this.bmImage = bmImage;
        }

        protected Bitmap doInBackground(String... urls) {
            String urldisplay = urls[0];
            Bitmap mIcon11 = null;
            try {
                InputStream in = new java.net.URL(urldisplay).openStream();
                mIcon11 = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Log.e("Error", e.getMessage());
                e.printStackTrace();
            }
            return mIcon11;
        }

        protected void onPostExecute(Bitmap result) {
            bmImage.setImageBitmap(result);
        }
    }

}