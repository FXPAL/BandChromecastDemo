/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.castremotedisplay;

import com.google.android.gms.cast.CastPresentation;
import com.google.android.gms.cast.CastRemoteDisplayLocalService;
import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandException;
import com.microsoft.band.BandInfo;
import com.microsoft.band.ConnectionState;
import com.microsoft.band.UserConsent;
import com.microsoft.band.sensors.BandGsrEvent;
import com.microsoft.band.sensors.BandGsrEventListener;
import com.microsoft.band.sensors.BandHeartRateEvent;
import com.microsoft.band.sensors.BandHeartRateEventListener;
import com.microsoft.band.sensors.BandRRIntervalEvent;
import com.microsoft.band.sensors.BandRRIntervalEventListener;
import com.microsoft.band.sensors.BandSensorManager;
import com.microsoft.band.sensors.BandSkinTemperatureEvent;
import com.microsoft.band.sensors.BandSkinTemperatureEventListener;
import com.microsoft.band.sensors.HeartRateConsentListener;
import com.microsoft.band.sensors.HeartRateQuality;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.lang.ref.WeakReference;


/**
 * Service to keep the remote display running even when the app goes into the background
 */
public class PresentationService extends CastRemoteDisplayLocalService {

    private static final String TAG = "PresentationService";

    // First screen
    private CastPresentation mPresentation;
//    private MediaPlayer mMediaPlayer;



    private BandClient client = null;
    private Button btnStart, btnConsent;
    private TextView txtStatus, txtGsr, txtHeartRate, txtSkinTemp;

    @Override
    public void onCreate() {
        super.onCreate();
        // Audio
//        mMediaPlayer = MediaPlayer.create(this, R.raw.sound);
//        mMediaPlayer.setVolume((float) 0.1, (float) 0.1);
//        mMediaPlayer.setLooping(true);
    }

    @Override
    public void onCreatePresentation(Display display) {
        createPresentation(display);
    }

    @Override
    public void onDismissPresentation() {
        dismissPresentation();
    }

    private void dismissPresentation() {
        if (mPresentation != null) {
//            mMediaPlayer.stop();
            mPresentation.dismiss();
            mPresentation = null;
        }
    }

    private void createPresentation(Display display) {
        dismissPresentation();
        mPresentation = new FirstScreenPresentation(this, display);

        try {
            mPresentation.show();
//            mMediaPlayer.start();
        } catch (WindowManager.InvalidDisplayException ex) {
            Log.e(TAG, "Unable to show presentation, display was removed.", ex);
            dismissPresentation();
        }
    }

    public void startMonitoringBand() {

        txtStatus.setText("");
        new RRIntervalSubscriptionTask().execute();
    }

    public enum DATA_TYPE { STATUS, RR_INTERVAL, HEARTRATE, GSR, SKIN_TEMP}

    public void writeText(final String text, DATA_TYPE dt){
        TextView tv = txtStatus;
        switch (dt){
            case HEARTRATE:
                tv = txtHeartRate;
                break;
            case GSR:
                tv = txtGsr;
                break;
            case SKIN_TEMP:
                tv = txtSkinTemp;
                break;
            default:
        }

        final TextView textView = tv;

        presentationHandler.post(new Runnable() {
            @Override
            public void run() {
                textView.setText(text);
            }
        });
    }


    private Handler presentationHandler;

    /**
     * The presentation to show on the first screen (the TV).
     * <p>
     * Note that this display may have different metrics from the display on
     * which the main activity is showing so we must be careful to use the
     * presentation's own {@link Context} whenever we load resources.
     * </p>
     */
    private class FirstScreenPresentation extends CastPresentation {

        private final String TAG = "FirstScreenPresentation";

        public FirstScreenPresentation(Context context, Display display) {
            super(context, display);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            setContentView(R.layout.first_screen_layout);

            TextView titleTextView = (TextView) findViewById(R.id.title);
            // Use TrueType font to get best looking text on remote display
            Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/Roboto-Light.ttf");
            titleTextView.setTypeface(typeface);

            presentationHandler = new Handler();

            txtStatus = (TextView) findViewById(R.id.txtStatus);
            txtGsr = (TextView) findViewById(R.id.txtGsr);
            txtHeartRate = (TextView) findViewById(R.id.txtHeartRate);
            txtSkinTemp = (TextView) findViewById(R.id.txtSkinTemp);

//            btnStart = (Button) findViewById(R.id.btnStart);
//            btnStart.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    txtStatus.setText("");
//                    new RRIntervalSubscriptionTask().execute();
//                }
//            });

//            final WeakReference<Activity> reference = new WeakReference<>(getOwnerActivity());
//
//            btnConsent = (Button) findViewById(R.id.btnConsent);
//            btnConsent.setOnClickListener(new View.OnClickListener() {
//                @SuppressWarnings("unchecked")
//                @Override
//                public void onClick(View v) {
//                    new HeartRateConsentTask().execute(reference);
//                }
//            });
        }

    }

    private class RRIntervalSubscriptionTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (getConnectedBandClient()) {
                    int hardwareVersion = Integer.parseInt(client.getHardwareVersion().await());
                    if (hardwareVersion >= 20) {
                        BandSensorManager sensormanager = client.getSensorManager();
                        if (sensormanager.getCurrentHeartRateConsent() == UserConsent.GRANTED) {
                            sensormanager.registerRRIntervalEventListener(mRRIntervalEventListener);
                            sensormanager.registerGsrEventListener(mGsrEventListener);
                            sensormanager.registerHeartRateEventListener(mHeartRateListener);
                            sensormanager.registerSkinTemperatureEventListener(mSkinTempEventListener);
                        } else {
                            appendToUI("You have not given this application consent to access heart rate data yet."
                                    + " Please press the Heart Rate Consent button.\n", txtStatus);
                        }
                    } else {
                        appendToUI("The RR Interval sensor is not supported with your Band version. Microsoft Band 2 is required.\n", txtStatus);
                    }
                } else {
                    appendToUI("Band isn't connected. Please make sure bluetooth is on and the band is in range.\n", txtStatus);
                }
            } catch (BandException e) {
                String exceptionMessage="";
                switch (e.getErrorType()) {
                    case UNSUPPORTED_SDK_VERSION_ERROR:
                        exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.\n";
                        break;
                    case SERVICE_ERROR:
                        exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.\n";
                        break;
                    default:
                        exceptionMessage = "Unknown error occured: " + e.getMessage() + "\n";
                        break;
                }
                appendToUI(exceptionMessage, txtStatus);

            } catch (Exception e) {
                appendToUI(e.getMessage(), txtStatus);
            }
            return null;
        }
    }

    private class HeartRateConsentTask extends AsyncTask<WeakReference<Activity>, Void, Void> {
        @Override
        protected Void doInBackground(WeakReference<Activity>... params) {
            try {
                if (getConnectedBandClient()) {

                    if (params[0].get() != null) {
                        client.getSensorManager().requestHeartRateConsent(params[0].get(), new HeartRateConsentListener() {
                            @Override
                            public void userAccepted(boolean consentGiven) {
                            }
                        });
                    }
                } else {
                    appendToUI("Band isn't connected. Please make sure bluetooth is on and the band is in range.\n", txtStatus);
                }
            } catch (BandException e) {
                String exceptionMessage="";
                switch (e.getErrorType()) {
                    case UNSUPPORTED_SDK_VERSION_ERROR:
                        exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.\n";
                        break;
                    case SERVICE_ERROR:
                        exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.\n";
                        break;
                    default:
                        exceptionMessage = "Unknown error occured: " + e.getMessage() + "\n";
                        break;
                }
                appendToUI(exceptionMessage, txtStatus);

            } catch (Exception e) {
                appendToUI(e.getMessage(), txtStatus);
            }
            return null;
        }
    }

    private void appendToUI(final String string, final TextView textView) {
        presentationHandler.post(new Runnable() {
            @Override
            public void run() {
                textView.setText(string);
            }
        });
    }

    private boolean getConnectedBandClient() throws InterruptedException, BandException {
        if (client == null) {
            BandInfo[] devices = BandClientManager.getInstance().getPairedBands();
            if (devices.length == 0) {
                appendToUI("Band isn't paired with your phone.\n", txtStatus);
                return false;
            }
            client = BandClientManager.getInstance().create(getBaseContext(), devices[0]);
        } else if (ConnectionState.CONNECTED == client.getConnectionState()) {
            return true;
        }

        appendToUI("Band is connecting...\n", txtStatus);
        return ConnectionState.CONNECTED == client.connect().await();
    }


    private BandRRIntervalEventListener mRRIntervalEventListener = new BandRRIntervalEventListener() {
        @Override
        public void onBandRRIntervalChanged(final BandRRIntervalEvent event) {
            if (event != null) {
                appendToUI(String.format("RR Interval = %.3f s\n", event.getInterval()), txtStatus);
            }
        }
    };

    private BandGsrEventListener mGsrEventListener = new BandGsrEventListener() {
        @Override
        public void onBandGsrChanged(BandGsrEvent bandGsrEvent) {
            appendToUI(String.format("GSR = %d \n", bandGsrEvent.getResistance()), txtGsr);
        }
    };

    private BandHeartRateEventListener mHeartRateListener = new BandHeartRateEventListener() {
        @Override
        public void onBandHeartRateChanged(BandHeartRateEvent bandHeartRateEvent) {

            appendToUI(String.format("Heart Rate = %d bpm %s\n", bandHeartRateEvent.getHeartRate(), bandHeartRateEvent.getQuality()== HeartRateQuality.LOCKED?"Locked":"Acquiring"), txtHeartRate);
        }
    };

    private BandSkinTemperatureEventListener mSkinTempEventListener = new BandSkinTemperatureEventListener() {
        @Override
        public void onBandSkinTemperatureChanged(BandSkinTemperatureEvent bandSkinTemperatureEvent) {
            appendToUI(String.format("Skin Temperature = %.1f \u00B0C\n", bandSkinTemperatureEvent.getTemperature()), txtSkinTemp);
        }
    };

}
