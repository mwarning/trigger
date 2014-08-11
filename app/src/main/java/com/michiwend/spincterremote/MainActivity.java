package com.michiwend.spincterremote;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;


public class MainActivity extends Activity implements OnTaskCompleted{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // FIXME: get WIFI state (am I connected to OpenLab WiFi?)

        final MainActivity parent = this;

        Button button_open = (Button) findViewById(R.id.button_open);

        button_open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                new SphincterRequestHandler(parent).execute(Action.open_door);

            }
        });

        Button button_close = (Button) findViewById(R.id.button_close);

        button_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                new SphincterRequestHandler(parent).execute(Action.close_door);
            }
        });

    }

    @Override
    public void onTaskCompleted(String result) {

        Log.i("[GET RESULT]", result);

        ImageView stateIcon = (ImageView) findViewById(R.id.stateIcon);

        if( result.equals("UNLOCKED") ) {
            // Door unlocked
            stateIcon.setImageResource(R.drawable.labstate_open);
        }
        else if ( result.equals("LOCKED") ) {
            // Door locked
            stateIcon.setImageResource(R.drawable.labstate_closed);
        }
        else {
            stateIcon.setImageResource(R.drawable.labstate_unknown);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // FIXME: get WIFI state (am I connected to OpenLab WiFi?) --> update state icon
        new SphincterRequestHandler(this).execute(Action.update_state);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


}
