package com.dthfish.eggachedispalyview;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.dthfish.eggachedisplayview.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final EggacheDisplayView edv = findViewById(R.id.edv);
        edv.setListStrategy(new TopListStrategy());
        edv.postDelayed(new Runnable() {
            @Override
            public void run() {
                edv.collapse();
            }
        }, 2000);
    }
}
