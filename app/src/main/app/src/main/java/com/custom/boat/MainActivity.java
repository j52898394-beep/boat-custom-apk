package com.custom.boat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Virtualizer;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    private Equalizer eq;
    private BassBoost bass;
    private Virtualizer spatial;
    private BluetoothGatt bleGatt;
    private TextView batteryText;
    private TextView statusText;
    private SharedPreferences prefs;

    private static final UUID SERVICE_UUID = UUID.fromString("0000ae00-0000-1000-8000-00805f9b34fb");
    private static final UUID WRITE_UUID = UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb");
    private static final UUID NOTIFY_UUID = UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("BoatPrefs", MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0F0F14"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(45, 60, 45, 60);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("boAt Airdopes 800 Pro");
        title.setTextSize(24f);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        statusText = new TextView(this);
        statusText.setText("JieLi RCSP Protocol • Persistent Audio Engine");
        statusText.setTextSize(13f);
        statusText.setTextColor(Color.parseColor("#888899"));
        statusText.setPadding(0, 8, 0, 40);
        root.addView(statusText);

        // Battery Card
        LinearLayout battCard = createCard();
        batteryText = new TextView(this);
        batteryText.setText("Left: --%   |   Right: --%   |   Case: --%");
        batteryText.setTextSize(16f);
        batteryText.setTextColor(Color.parseColor("#00E676"));
        batteryText.setTypeface(null, Typeface.BOLD);
        battCard.addView(batteryText);

        Button connectBtn = new Button(this);
        connectBtn.setText("Scan & Read Case Battery (BLE)");
        connectBtn.setOnClickListener(v -> connectToEarbuds());
        battCard.addView(connectBtn);
        root.addView(battCard);

        // Audio Controls Card
        LinearLayout audioCard = createCard();
        TextView audioHeader = new TextView(this);
        audioHeader.setText("Audio & Knowles EQ (No Reset)");
        audioHeader.setTextSize(18f);
        audioHeader.setTextColor(Color.WHITE);
        audioHeader.setTypeface(null, Typeface.BOLD);
        audioHeader.setPadding(0, 0, 0, 24);
        audioCard.addView(audioHeader);

        boolean savedEq = prefs.getBoolean("eq_on", false);
        Switch eqSwitch = new Switch(this);
        eqSwitch.setText("Knowles Signature Tuning");
        eqSwitch.setTextColor(Color.WHITE);
        eqSwitch.setTextSize(16f);
        eqSwitch.setChecked(savedEq);
        eqSwitch.setPadding(0, 20, 0, 20);
        eqSwitch.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.edit().putBoolean("eq_on", isChecked).apply();
            applyKnowlesEq(isChecked);
        });
        audioCard.addView(eqSwitch);

        boolean savedSpatial = prefs.getBoolean("spatial_on", false);
        Switch spatialSwitch = new Switch(this);
        spatialSwitch.setText("3D Spatial Audio Virtualizer");
        spatialSwitch.setTextColor(Color.WHITE);
        spatialSwitch.setTextSize(16f);
        spatialSwitch.setChecked(savedSpatial);
        spatialSwitch.setPadding(0, 20, 0, 20);
        spatialSwitch.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.edit().putBoolean("spatial_on", isChecked).apply();
            applySpatial(isChecked);
        });
        audioCard.addView(spatialSwitch);

        TextView bassLabel = new TextView(this);
        bassLabel.setText("Sub-Bass Boost");
        bassLabel.setTextColor(Color.WHITE);
        bassLabel.setPadding(0, 24, 0, 12);
        audioCard.addView(bassLabel);

        SeekBar bassSeek = new SeekBar(this);
        bassSeek.setMax(1000);
        bassSeek.setProgress(prefs.getInt("bass_val", 0));
        bassSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) {
                prefs.edit().putInt("bass_val", p).apply();
                applyBass((short) p);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        audioCard.addView(bassSeek);
        root.addView(audioCard);

        // Touch Gestures Card
        LinearLayout touchCard = createCard();
        TextView touchHeader = new TextView(this);
        touchHeader.setText("Custom Touch Controls");
        touchHeader.setTextSize(18f);
        touchHeader.setTextColor(Color.WHITE);
        touchHeader.setTypeface(null, Typeface.BOLD);
        touchHeader.setPadding(0, 0, 0, 20);
        touchCard.addView(touchHeader);

        Button btnDoubleTap = new Button(this);
        btnDoubleTap.setText("Set Double Tap -> Play/Pause");
        btnDoubleTap.setOnClickListener(v -> sendGestureCommand((byte) 0x01, (byte) 0x01, (byte) 0x01));
        touchCard.addView(btnDoubleTap);

        Button btnTripleTap = new Button(this);
        btnTripleTap.setText("Set Triple Tap -> Next Track");
        btnTripleTap.setOnClickListener(v -> sendGestureCommand((byte) 0x02, (byte) 0x02, (byte) 0x02));
        touchCard.addView(btnTripleTap);
        root.addView(touchCard);

        setContentView(scroll);

        if (savedEq) applyKnowlesEq(true);
        if (savedSpatial) applySpatial(true);
        applyBass((short) prefs.getInt("bass_val", 0));
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(36, 36, 36, 36);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.parseColor("#1C1C26"));
        shape.setCornerRadius(28f);
        card.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 36);
        card.setLayoutParams(params);
        return card;
    }

    private void applyKnowlesEq(boolean enable) {
        try {
            if (eq == null) eq = new Equalizer(100, 0);
            eq.setEnabled(enable);
            if (enable) {
                short bands = eq.getNumberOfBands();
                short min = eq.getBandLevelRange()[0];
                short max = eq.getBandLevelRange()[1];
                float[] curve = {0.22f, 0.08f, 0.25f, 0.45f, 0.40f};
                for (short i = 0; i < Math.min(bands, curve.length); i++) {
                    short target = (short) (min + (max - min) * curve[i]);
                    eq.setBandLevel(i, target);
                }
                Toast.makeText(this, "Knowles EQ Locked", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Audio Session Active", Toast.LENGTH_SHORT).show();
        }
    }

    private void applySpatial(boolean enable) {
        try {
            if (spatial == null) spatial = new Virtualizer(100, 0);
            spatial.setEnabled(enable);
            if (enable && spatial.getStrengthSupported()) {
                spatial.setStrength((short) 850);
            }
        } catch (Exception ignored) {}
    }

    private void applyBass(short level) {
        try {
            if (bass == null) {
                bass = new BassBoost(100, 0);
                bass.setEnabled(true);
            }
            if (bass.getStrengthSupported()) {
                bass.setStrength(level);
            }
        } catch (Exception ignored) {}
    }

    @SuppressLint("MissingPermission")
    private void connectToEarbuds() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, 101);
                return;
            }
        }
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager != null ? manager.getAdapter() : null;
        if (adapter == null || !adapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth On Karein", Toast.LENGTH_SHORT).show();
            return;
        }
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        for (BluetoothDevice device : bonded) {
            String name = device.getName();
            if (name != null && (name.toLowerCase().contains("airdopes") || name.toLowerCase().contains("boat"))) {
                statusText.setText("Connecting BLE to: " + name);
                bleGatt = device.connectGatt(this, false, gattCallback);
                return;
            }
        }
        Toast.makeText(this, "Pehle Bluetooth me Airdopes pair karein", Toast.LENGTH_SHORT).show();
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic notifyChar = service.getCharacteristic(NOTIFY_UUID);
                if (notifyChar != null) {
                    gatt.setCharacteristicNotification(notifyChar, true);
                    for (BluetoothGattDescriptor desc : notifyChar.getDescriptors()) {
                        desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(desc);
                    }
                }
                // Request JieLi Device Config / Battery Info
                BluetoothGattCharacteristic writeChar = service.getCharacteristic(WRITE_UUID);
                if (writeChar != null) {
                    writeChar.setValue(new byte[]{(byte) 0xFE, (byte) 0xDC, (byte) 0xBA, (byte) 0xC0, 0x06, 0x00, 0x02, 0x01, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xEF});
                    gatt.writeCharacteristic(writeChar);
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            if (data != null && data.length >= 7) {
                final int left = data[data.length - 3] & 0x7F;
                final int right = data[data.length - 2] & 0x7F;
                final int caseBatt = data[data.length - 1] & 0x7F;
                runOnUiThread(() -> batteryText.setText("Left: " + left + "%   |   Right: " + right + "%   |   Case: " + caseBatt + "%"));
            }
        }
    };

    @SuppressLint("MissingPermission")
    private void sendGestureCommand(byte side, byte keyType, byte action) {
        if (bleGatt == null) {
            Toast.makeText(this, "Pehle Scan & Connect button dabayein", Toast.LENGTH_SHORT).show();
            return;
        }
        BluetoothGattService service = bleGatt.getService(SERVICE_UUID);
        if (service != null) {
            BluetoothGattCharacteristic writeChar = service.getCharacteristic(WRITE_UUID);
            if (writeChar != null) {
                byte[] cmd = new byte[]{(byte) 0xFE, (byte) 0xDC, (byte) 0xBA, (byte) 0xC0, 0x08, 0x00, 0x04, 0x01, side, keyType, action, (byte) 0xEF};
                writeChar.setValue(cmd);
                bleGatt.writeCharacteristic(writeChar);
                Toast.makeText(this, "Gesture Command Sent!", Toast.LENGTH_SHORT).show();
            }
        }
    }
                                   }
