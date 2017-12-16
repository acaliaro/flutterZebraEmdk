package it.soluzione1.flutterzebraemdk.flutterzebraemdk;
import com.symbol.emdk.EMDKManager;
import com.symbol.emdk.EMDKManager.EMDKListener;
import com.symbol.emdk.EMDKResults;
import com.symbol.emdk.barcode.BarcodeManager;
import com.symbol.emdk.barcode.ScanDataCollection;
import com.symbol.emdk.barcode.Scanner;
import com.symbol.emdk.barcode.Scanner.DataListener;
import com.symbol.emdk.barcode.Scanner.StatusListener;
import com.symbol.emdk.barcode.ScannerConfig;
import com.symbol.emdk.barcode.ScannerException;
import com.symbol.emdk.barcode.ScannerInfo;
import com.symbol.emdk.barcode.ScannerResults;
import com.symbol.emdk.barcode.StatusData;

import android.content.BroadcastReceiver;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import io.flutter.app.FlutterActivity;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugins.GeneratedPluginRegistrant;

public class MainActivity extends FlutterActivity implements EMDKListener,
        StatusListener, DataListener, BarcodeManager.ScannerConnectionListener {

  // Declare a variable to store EMDKManager object
  private EMDKManager emdkManager = null;

  // Declare a variable to store Barcode Manager object
  private BarcodeManager barcodeManager = null;
  private boolean bContinuousMode = true;

  // Declare a variable to hold scanner device to scan
  private Scanner scanner = null;

  // List of supported scanner devices
  private List<ScannerInfo> deviceList;

  private static final String BARCODE_RECEIVED_CHANNEL = "samples.flutter.io/barcodereceived";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    GeneratedPluginRegistrant.registerWith(this);

    // The EMDKManager object will be created and returned in the callback.
    EMDKResults results = EMDKManager.getEMDKManager(
            getApplicationContext(), this);

    if (results.statusCode != EMDKResults.STATUS_CODE.SUCCESS) {
      Log.d("FLUTTERDEMO", "EMDKManager Request Failed");
    }

    new EventChannel(getFlutterView(), BARCODE_RECEIVED_CHANNEL).setStreamHandler(
            new StreamHandler() {

              private BroadcastReceiver barcodeBroadcastReceiver;

              @Override
              public void onListen(Object arguments, EventSink events) {
                Log.d("FLUTTERDEMO", "EventChannelOnListen");

                barcodeBroadcastReceiver = createBarcodeBroadcastReceiver(events);
                registerReceiver(
                        barcodeBroadcastReceiver, new IntentFilter("readBarcode"));
              }

              @Override
              public void onCancel(Object arguments) {
                Log.d("FLUTTERDEMO", "EventChannelOnCancel");

                unregisterReceiver(barcodeBroadcastReceiver);
                barcodeBroadcastReceiver = null;
              }
            }
    );

  }

  private BroadcastReceiver createBarcodeBroadcastReceiver(final EventSink events) {
    return new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();

        Log.d("FLUTTERDEMO", "createBarcodeBroadcastReceiver " + action);

        if(action.equals("readBarcode")){
          String barcode = intent.getStringExtra("barcode");
          String barcodetype = intent.getStringExtra("barcodetype");
          Log.d("FLUTTERDEMO", "createBarcodeBroadcastReceiver " + barcode);
          events.success(barcode);
        }
      }
    };
  }

  @Override
  public void onOpened(EMDKManager emdkManager) {

    Log.d("FLUTTERDEMO", "onOpened()");

    Toast.makeText(this,"Status: " + "EMDK open success!", Toast.LENGTH_LONG);

    this.emdkManager = emdkManager;

    // Acquire the barcode manager resources
    barcodeManager = (BarcodeManager) emdkManager.getInstance(EMDKManager.FEATURE_TYPE.BARCODE);

    // Add connection listener
    if (barcodeManager != null) {
      barcodeManager.addConnectionListener(this);
    }

    // Enumerate scanner devices
    enumerateScannerDevices();

    startScan();

  }

  private void enumerateScannerDevices() {

    Log.d("FLUTTERDEMO", "snumerateScannerDevices()");

    if (barcodeManager != null) {

      List<String> friendlyNameList = new ArrayList<String>();
      int spinnerIndex = 0;

      deviceList = barcodeManager.getSupportedDevicesInfo();

      if ((deviceList != null) && (deviceList.size() != 0)) {
/*
                Iterator<ScannerInfo> it = deviceList.iterator();
                while(it.hasNext()) {
                    ScannerInfo scnInfo = it.next();
                    friendlyNameList.add(scnInfo.getFriendlyName());
                    if(scnInfo.isDefaultScanner()) {
                        defaultIndex = spinnerIndex;
                    }
                    ++spinnerIndex;
                }
                */
      }
      else {
        Toast.makeText(this, "Status: " + "Failed to get the list of supported scanner devices! Please close and restart the application.", Toast.LENGTH_LONG);
      }

    }
  }

  @Override
  public void onClosed() {

    Log.d("FLUTTERDEMO", "onClosed()");

    if (emdkManager != null) {

      // Remove connection listener
      if (barcodeManager != null){
        barcodeManager.removeConnectionListener(this);
        barcodeManager = null;
      }

      // Release all the resources
      emdkManager.release();
      emdkManager = null;
    }
    Toast.makeText(this,"Status: " + "EMDK closed unexpectedly! Please close and restart the application.", Toast.LENGTH_LONG);
  }

  @Override
  public void onData(ScanDataCollection scanDataCollection) {

    Log.d("FLUTTERDEMO", "onData");

    if ((scanDataCollection != null) && (scanDataCollection.getResult() == ScannerResults.SUCCESS)) {
      ArrayList <ScanDataCollection.ScanData> scanData = scanDataCollection.getScanData();
      for(ScanDataCollection.ScanData data : scanData) {

        try {
          Log.d("FLUTTERDEMO", data.getLabelType() + "-" + data.getData());
          Intent intent = new Intent("readBarcode");
          intent.putExtra("barcode", data.getData());
          intent.putExtra("barcodetype", data.getLabelType());

          sendBroadcast(intent);

        }
        catch (Exception ex){
        }

      }
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    // The application is in background

    // De-initialize scanner
    deInitScanner();

    // Remove connection listener
    if (barcodeManager != null) {
      barcodeManager.removeConnectionListener(this);
      barcodeManager = null;
      deviceList = null;
    }

    // Release the barcode manager resources
    if (emdkManager != null) {
      emdkManager.release(EMDKManager.FEATURE_TYPE.BARCODE);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    // The application is in foreground

    // Acquire the barcode manager resources
    if (emdkManager != null) {
      barcodeManager = (BarcodeManager) emdkManager.getInstance(EMDKManager.FEATURE_TYPE.BARCODE);

      // Add connection listener
      if (barcodeManager != null) {
        barcodeManager.addConnectionListener(this);
      }

      // Enumerate scanner devices
      enumerateScannerDevices();

      // Set selected scanner
      //spinnerScannerDevices.setSelection(scannerIndex);

      // Initialize scanner
      initScanner();
      setTrigger();
      setDecoders();
    }
  }

  @Override
  public void onStatus(StatusData statusData) {

    Log.d("FLUTTERDEMO", "onStatus() " + statusData.getFriendlyName() );

    StatusData.ScannerStates state = statusData.getState();

    switch(state) {
      case IDLE:
        if (bContinuousMode) {
          try {
            // An attempt to use the scanner continuously and rapidly (with a delay < 100 ms between scans)
            // may cause the scanner to pause momentarily before resuming the scanning.
            // Hence add some delay (>= 100ms) before submitting the next read.
            try {
              Thread.sleep(100);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }

            scanner.read();
          } catch (ScannerException e) {

          }
        }
        break;
      case WAITING:
        break;
      case SCANNING:
        break;
      case DISABLED:
        break;
      case ERROR:
        break;
      default:
        break;
    }

  }

  private void setTrigger() {

    Log.d("FLUTTERDEMO", "setTrigger()");

    if (scanner == null) {
      initScanner();
    }
/*
        if (scanner != null) {
            switch (triggerIndex) {
                case 0: // Selected "HARD"
                    scanner.triggerType = Scanner.TriggerType.HARD;
                    break;
                case 1: // Selected "SOFT"
                    scanner.triggerType = Scanner.TriggerType.SOFT_ALWAYS;
                    break;
            }
        }
        */

    scanner.triggerType = Scanner.TriggerType.HARD;
  }

  private void setDecoders() {

    Log.d("FLUTTERDEMO", "setDecoders()");

    if (scanner == null) {
      initScanner();
    }

    if ((scanner != null) && (scanner.isEnabled())) {
      try {

        ScannerConfig config = scanner.getConfig();

        // Set EAN8
        // if(checkBoxEAN8.isChecked())
        config.decoderParams.ean8.enabled = true;
        //else
        //    config.decoderParams.ean8.enabled = false;

        // Set EAN13
        //if(checkBoxEAN13.isChecked())
        config.decoderParams.ean13.enabled = true;
        //else
        //    config.decoderParams.ean13.enabled = false;

        // Set Code39
        //if(checkBoxCode39.isChecked())
        config.decoderParams.code39.enabled = true;
        //else
        //    config.decoderParams.code39.enabled = false;

        //Set Code128
        //if(checkBoxCode128.isChecked())
        config.decoderParams.code128.enabled = true;
        //else
        //    config.decoderParams.code128.enabled = false;

        config.decoderParams.gs1Databar.enabled = true; // Ean13...



        scanner.setConfig(config);

      } catch (ScannerException e) {

        Toast.makeText(this, "Status: " + e.getMessage(), Toast.LENGTH_LONG);
      }
    }
  }

  private void startScan() {

    Log.d("FLUTTERDEMO", "startScan()");

    if(scanner == null) {
      initScanner();
    }

    if (scanner != null) {
      try {

        if(scanner.isEnabled())
        {
          // Submit a new read.
          scanner.read();

          //if (checkBoxContinuous.isChecked())
          //bContinuousMode = true;
          //else
          //    bContinuousMode = false;

        }
        else
        {
          Toast.makeText(this, "Status: Scanner is not enabled", Toast.LENGTH_LONG);
        }

      } catch (ScannerException e) {

        Toast.makeText(this, "Status: " + e.getMessage(), Toast.LENGTH_LONG);
      }
    }

  }

  private void stopScan() {

    Log.d("FLUTTERDEMO", "stopScan()");

    if (scanner != null) {

      try {

        // Reset continuous flag
        //bContinuousMode = false;

        // Cancel the pending read.
        scanner.cancelRead();


      } catch (ScannerException e) {
        Toast.makeText(this, "Status: " + e.getMessage(), Toast.LENGTH_LONG);
      }
    }
  }

  private void initScanner() {

    Log.d("FLUTTERDEMO", "initScanner()");

    if (scanner == null) {

      if ((deviceList != null) && (deviceList.size() != 0)) {
        scanner = barcodeManager.getDevice(deviceList.get(0));
      }
      else {
        Toast.makeText(this, "Status: " + "Failed to get the specified scanner device! Please close and restart the application.", Toast.LENGTH_LONG);
        return;
      }

      if (scanner != null) {

        scanner.addDataListener(this);
        scanner.addStatusListener(this);

        try {
          scanner.enable();
        } catch (ScannerException e) {

          Toast.makeText(this, "Status: " + e.getMessage(), Toast.LENGTH_LONG);
        }
      }else{
        Toast.makeText(this, "Status: " + "Failed to initialize the scanner device.", Toast.LENGTH_LONG);
      }
    }
  }

  private void deInitScanner() {

    Log.d("FLUTTERDEMO", "deInitScanner()");

    if (scanner != null) {

      try {

        scanner.cancelRead();
        scanner.disable();

      } catch (ScannerException e) {

        Toast.makeText(this, "Status: " + e.getMessage(), Toast.LENGTH_LONG);
      }
      scanner.removeDataListener(this);
      scanner.removeStatusListener(this);
      try{
        scanner.release();
      } catch (ScannerException e) {

        Toast.makeText(this, "Status: " + e.getMessage(), Toast.LENGTH_LONG);
      }

      scanner = null;
    }
  }

  @Override
  public void onConnectionChange(ScannerInfo scannerInfo, BarcodeManager.ConnectionState connectionState) {

    Log.d("FLUTTERDEMO", "onConnectionChange");

    String status;
    String scannerName = "";

    String statusExtScanner = connectionState.toString();
    String scannerNameExtScanner = scannerInfo.getFriendlyName();

    if (deviceList.size() != 0) {
      scannerName = deviceList.get(0).getFriendlyName();
    }

    if (scannerName.equalsIgnoreCase(scannerNameExtScanner)) {

      switch(connectionState) {
        case CONNECTED:
          deInitScanner();
          initScanner();
          setTrigger();
          setDecoders();
          break;
        case DISCONNECTED:
          deInitScanner();
          break;
      }

      status = scannerNameExtScanner + ":" + statusExtScanner;
    }
    else {
    }
  }


}

