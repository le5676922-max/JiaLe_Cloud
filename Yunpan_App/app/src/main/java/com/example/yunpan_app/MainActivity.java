package com.example.yunpan_app;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

public class MainActivity extends AppCompatActivity {
    private SharedPreferences prefs;
    private TextView tabStatus, tabFiles, tabSettings;
    private View headerDot;
    private TextView headerStatusText;
    private AlertDialog activeBindDialog;
    private final ActivityResultLauncher<ScanOptions> bindScanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() == null) {
                    Toast.makeText(this, "已取消扫码", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    String payload = BindScanParser.parseScannedBindPayload(result.getContents());
                    bindAndStart(payload, activeBindDialog);
                } catch (IllegalArgumentException e) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("yunpan", MODE_PRIVATE);
        headerDot = findViewById(R.id.header_dot);
        headerStatusText = findViewById(R.id.header_status_text);
        tabStatus = findViewById(R.id.tab_status);
        tabFiles = findViewById(R.id.tab_files);
        tabSettings = findViewById(R.id.tab_settings);

        tabStatus.setOnClickListener(v -> selectTab(0));
        tabFiles.setOnClickListener(v -> selectTab(1));
        tabSettings.setOnClickListener(v -> selectTab(2));

        String deepLink = getIntent() == null || getIntent().getData() == null
                ? "" : getIntent().getDataString();
        if (deepLink != null && deepLink.startsWith("yunpan://bind")) {
            bindAndStart(deepLink, null);
        } else if (prefs.getString("node_token", "").isEmpty()) {
            showSetupDialog();
        } else {
            ContextCompat.startForegroundService(this, new Intent(this, NodeService.class));
        }
        selectTab(0);
    }

    private void selectTab(int idx) {
        tabStatus.setTextColor(idx == 0 ? 0xFF1C1C1E : 0xFF8E8E93);
        tabFiles.setTextColor(idx == 1 ? 0xFF1C1C1E : 0xFF8E8E93);
        tabSettings.setTextColor(idx == 2 ? 0xFF1C1C1E : 0xFF8E8E93);
        Fragment f = idx == 0 ? new StatusFragment() : idx == 1 ? new FilesFragment() : new SettingsFragment();
        getSupportFragmentManager().beginTransaction().replace(R.id.container, f).commit();
    }

    private void showSetupDialog() {
        showEmailDialog();
    }

    private void showEmailDialog() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL); ll.setPadding(48, 32, 48, 16);
        EditText ticketEt = new EditText(this);
        ticketEt.setHint("粘贴绑定码或 yunpan://bind?... 内容");
        ll.addView(ticketEt);
        AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("绑定存储节点")
                .setMessage("在网页端添加旧手机节点后，可扫码绑定，也可粘贴二维码内容或一次性绑定码。")
                .setView(ll)
                .setPositiveButton("绑定", null)
                .setNegativeButton("扫码绑定", null)
                .setCancelable(false)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String ticket = ticketEt.getText().toString().trim();
                if (ticket.isEmpty()) {
                    Toast.makeText(this, "请输入绑定码", Toast.LENGTH_SHORT).show();
                    return;
                }
                bindAndStart(ticket, dialog);
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> startBindScanner(dialog));
        });
        dialog.show();
    }

    public void startBindScanner() {
        startBindScanner(null);
    }

    private void startBindScanner(AlertDialog dialog) {
        activeBindDialog = dialog;
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("扫描网页端的旧手机节点二维码");
        options.setBeepEnabled(false);
        options.setOrientationLocked(true);
        options.setCaptureActivity(BindCaptureActivity.class);
        bindScanLauncher.launch(options);
    }

    private void bindAndStart(String ticket, AlertDialog dialog) {
        new Thread(() -> {
            try {
                new AppBindClient(this).bind(ticket);
                runOnUiThread(() -> {
                    try {
                        ContextCompat.startForegroundService(this, new Intent(this, NodeService.class));
                        Toast.makeText(this, "节点已绑定并启动", Toast.LENGTH_LONG).show();
                        if (dialog != null) dialog.dismiss();
                    } catch (Exception serviceError) {
                        Toast.makeText(this,
                                "绑定成功，但节点服务启动失败：" + serviceError.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Throwable e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "绑定失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "app-bind").start();
    }
}
