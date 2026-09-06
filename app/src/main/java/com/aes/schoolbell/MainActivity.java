package com.aes.schoolbell;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String PASS = "12345678";
    private static final String DEFAULT_IP = "192.168.4.1";
    private static final String[] SSIDS = {"AES/SB/WIFI/S-01", "AES/SB/WIFI/S-02", "AES/SB/WIFI/S-03", "Custom SSID / IP"};
    private static final int PICK_FIRMWARE = 501;

    private WebView web;
    private LinearLayout launcher;
    private Spinner machine;
    private EditText ip;
    private TextView status;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback wifiCallback;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        showLauncher();
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(Color.DKGRAY);
        t.setPadding(8, 10, 8, 10); return t;
    }

    private Button button(String label) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setTextSize(15); return b;
    }

    private void showLauncher() {
        launcher = new LinearLayout(this); launcher.setOrientation(LinearLayout.VERTICAL);
        launcher.setPadding(26, 24, 26, 22); launcher.setBackgroundColor(Color.rgb(241,245,249));

        TextView title = text("ATUL ELECTRONICS", 27); title.setTextColor(Color.rgb(37,99,235));
        title.setGravity(Gravity.CENTER); title.setTypeface(null, 1); launcher.addView(title);
        TextView sub = text("SMART SCHOOL BELL", 18); sub.setGravity(Gravity.CENTER); sub.setTypeface(null, 1); launcher.addView(sub);
        TextView info = text("ESP32 LOCAL DASHBOARD APP", 13); info.setGravity(Gravity.CENTER); launcher.addView(info);

        launcher.addView(text("SELECT SCHOOL BELL MACHINE", 13));
        machine = new Spinner(this);
        machine.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, SSIDS));
        launcher.addView(machine, new LinearLayout.LayoutParams(-1, 55));

        ip = new EditText(this); ip.setHint("ESP32 IP (default 192.168.4.1)"); ip.setText(DEFAULT_IP);
        ip.setSingleLine(true); launcher.addView(ip);

        Button connect = button("CONNECT TO SCHOOL BELL"); launcher.addView(connect, new LinearLayout.LayoutParams(-1, 58));
        Button wifi = button("OPEN WI-FI SETTINGS"); launcher.addView(wifi, new LinearLayout.LayoutParams(-1, 58));
        Button ota = button("FIRMWARE UPDATE (OTA)"); launcher.addView(ota, new LinearLayout.LayoutParams(-1, 58));
        status = text("Select S-01 / S-02 / S-03. The app will request Android permission to connect to the selected ESP32 Wi-Fi.", 12);
        status.setTextColor(Color.rgb(71,85,105)); launcher.addView(status);

        machine.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (pos < 3) status.setText("Selected: " + SSIDS[pos] + "\nTap CONNECT. Android may show a Wi-Fi connection approval dialog.");
                else status.setText("Custom mode: enter the ESP32 IP above. Connect the phone to its Wi-Fi manually if needed.");
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        connect.setOnClickListener(v -> connectSelected());
        wifi.setOnClickListener(v -> openWifiSettings());
        ota.setOnClickListener(v -> chooseFirmware());
        setContentView(launcher);
    }

    private String selectedSsid() {
        int p = machine.getSelectedItemPosition();
        return p >= 0 && p < 3 ? SSIDS[p] : "";
    }

    private void connectSelected() {
        String ssid = selectedSsid();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !ssid.isEmpty()) {
            requestWifi(ssid);
        } else {
            openDashboard();
        }
    }

    private void requestWifi(String ssid) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.NEARBY_WIFI_DEVICES}, 101); return;
        }
        if (Build.VERSION.SDK_INT <= 32 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 102); return;
        }

        if (wifiCallback != null) { try { connectivityManager.unregisterNetworkCallback(wifiCallback); } catch (Exception ignored) {} }
        status.setText("Connecting to " + ssid + "…");

        WifiNetworkSpecifier spec = new WifiNetworkSpecifier.Builder().setSsid(ssid).setWpa2Passphrase(PASS).build();
        NetworkRequest request = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(spec).build();
        wifiCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                try { connectivityManager.bindProcessToNetwork(network); } catch (Exception ignored) {}
                runOnUiThread(() -> { status.setText("Wi-Fi connected: " + ssid); openDashboard(); });
            }
            @Override public void onUnavailable() { runOnUiThread(() -> { status.setText("Wi-Fi connection was not approved or failed. Open Wi-Fi Settings and connect manually."); Toast.makeText(MainActivity.this, "ESP32 Wi-Fi connect failed", Toast.LENGTH_LONG).show(); }); }
            @Override public void onLost(Network network) { runOnUiThread(() -> Toast.makeText(MainActivity.this, "ESP32 Wi-Fi disconnected", Toast.LENGTH_SHORT).show()); }
        };
        connectivityManager.requestNetwork(request, wifiCallback, 15000);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if ((requestCode == 101 || requestCode == 102) && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) connectSelected();
        else status.setText("Wi-Fi permission is required for automatic ESP32 connection. You can also connect manually from Wi-Fi Settings.");
    }

    private void openDashboard() {
        String host = ip.getText().toString().trim();
        if (host.isEmpty()) host = DEFAULT_IP;
        host = host.replace("https://", "").replace("http://", "");
        if (host.endsWith("/")) host = host.substring(0, host.length()-1);
        String url = "http://" + host + "/";

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setSupportZoom(false); s.setBuiltInZoomControls(false); s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(false); s.setUseWideViewPort(false);
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        web.setBackgroundColor(Color.WHITE);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { v.loadUrl(r.getUrl().toString()); return true; }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String u) { v.loadUrl(u); return true; }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) Toast.makeText(MainActivity.this, "ESP32 dashboard not reachable. Check Wi-Fi/IP.", Toast.LENGTH_LONG).show();
            }
        });
        web.setOnLongClickListener(v -> true);
        setContentView(web); web.loadUrl(url);
    }


    private String hostAddress() {
        String host = ip.getText().toString().trim();
        if (host.isEmpty()) host = DEFAULT_IP;
        host = host.replace("https://", "").replace("http://", "");
        while (host.endsWith("/")) host = host.substring(0, host.length()-1);
        return host;
    }

    private void chooseFirmware() {
        new AlertDialog.Builder(this)
            .setTitle("ESP32 FIRMWARE UPDATE")
            .setMessage("Select the new ESP32 .bin firmware file. The phone must be connected to the selected AES/SB/WIFI machine.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SELECT .BIN", (d, w) -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/octet-stream");
                startActivityForResult(i, PICK_FIRMWARE);
            }).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FIRMWARE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            new AlertDialog.Builder(this)
                .setTitle("CONFIRM OTA UPDATE")
                .setMessage("Firmware will be uploaded to " + hostAddress() + " and the ESP32 will restart automatically. Continue?")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("UPDATE", (d, w) -> startFirmwareUpload(uri))
                .show();
        }
    }

    private void startFirmwareUpload(Uri uri) {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("ESP32 FIRMWARE UPDATE");
        pd.setMessage("Preparing...");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setIndeterminate(false);
        pd.setMax(100);
        pd.setCancelable(false);
        pd.show();

        final String host = hostAddress();
        new Thread(() -> {
            HttpURLConnection login = null, up = null;
            try {
                // The ESP32 keeps authentication as a controller-side session flag.
                // Login once, then stream the selected .bin file to /ota_upload.
                URL loginUrl = new URL("http://" + host + "/login_check?user=admin&pass=9999");
                login = (HttpURLConnection) loginUrl.openConnection();
                login.setConnectTimeout(5000); login.setReadTimeout(8000);
                login.setInstanceFollowRedirects(false);
                login.setRequestMethod("GET");
                login.getResponseCode();
                login.disconnect(); login = null;

                long size = -1;
                try { size = getContentLength(uri); } catch (Exception ignored) {}
                if (size <= 0) throw new Exception("Could not read firmware file size");

                URL upUrl = new URL("http://" + host + "/ota_upload");
                up = (HttpURLConnection) upUrl.openConnection();
                up.setConnectTimeout(8000); up.setReadTimeout(40000);
                up.setDoOutput(true); up.setRequestMethod("POST");
                up.setFixedLengthStreamingMode(size);
                up.setRequestProperty("Content-Type", "application/octet-stream");
                up.setRequestProperty("Content-Length", Long.toString(size));

                try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = up.getOutputStream()) {
                    if (in == null) throw new Exception("Cannot open firmware file");
                    byte[] buf = new byte[8192];
                    long sent = 0; int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n); sent += n;
                        final int pct = (int)Math.min(99, (sent * 100L) / size);
                        runOnUiThread(() -> { pd.setProgress(pct); pd.setMessage("Uploading firmware... " + pct + "%"); });
                    }
                    out.flush();
                }
                int code = up.getResponseCode();
                InputStream rs = code >= 400 ? up.getErrorStream() : up.getInputStream();
                StringBuilder body = new StringBuilder();
                if (rs != null) { byte[] b = new byte[512]; int n; while ((n=rs.read(b))!=-1) body.append(new String(b,0,n)); rs.close(); }
                if (code == 200 && body.toString().contains("\"ok\":true")) {
                    runOnUiThread(() -> { pd.dismiss(); new AlertDialog.Builder(this).setTitle("UPDATE STARTED").setMessage("Firmware uploaded successfully. ESP32 is restarting. Wait about 10-20 seconds, then reconnect to the machine.").setPositiveButton("OK", null).show(); });
                } else {
                    throw new Exception("ESP32 returned HTTP " + code + "\n" + body);
                }
            } catch (Exception ex) {
                final String msg = ex.getMessage() == null ? "OTA update failed" : ex.getMessage();
                runOnUiThread(() -> { pd.dismiss(); new AlertDialog.Builder(this).setTitle("UPDATE FAILED").setMessage(msg + "\n\nCheck Wi-Fi connection, IP address and .bin file, then try again.").setPositiveButton("OK", null).show(); });
            } finally {
                if (login != null) login.disconnect();
                if (up != null) up.disconnect();
            }
        }).start();
    }

    private long getContentLength(Uri uri) throws Exception {
        android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
        if (c != null) {
            try { int idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE); if (c.moveToFirst() && idx >= 0 && !c.isNull(idx)) return c.getLong(idx); } finally { c.close(); }
        }
        throw new Exception("Unknown firmware file size");
    }

    private void openWifiSettings() {
        try { startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
        catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else { web = null; showLauncher(); }
    }

    @Override protected void onDestroy() {
        if (wifiCallback != null && connectivityManager != null) { try { connectivityManager.unregisterNetworkCallback(wifiCallback); } catch (Exception ignored) {} }
        super.onDestroy();
    }
}
