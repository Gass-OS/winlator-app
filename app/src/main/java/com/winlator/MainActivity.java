package com.winlator;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.winlator.contentdialog.AboutDialog;
import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.core.LocaleHelper;
import com.winlator.core.PreloaderDialog;
import com.winlator.xenvironment.RootFSInstaller;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    public static final boolean DEBUG_MODE = false; // FIXME change to false
    public static final @IntRange(from = 1, to = 19) byte CONTAINER_PATTERN_COMPRESSION_LEVEL = 9;
    public static final byte PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 1;
    public static final byte OPEN_FILE_REQUEST_CODE = 2;
    public static final byte EDIT_INPUT_CONTROLS_REQUEST_CODE = 3;
    public static final byte OPEN_DIRECTORY_REQUEST_CODE = 4;
    private DrawerLayout drawerLayout;
    public final PreloaderDialog preloaderDialog = new PreloaderDialog(this);
    private boolean editInputControls = false;
    private int selectedProfileId;
    private Callback<Uri> openFileCallback;
    private SharedPreferences preferences;
    private Fragment currentFragment;

    // WINEHUB_BOOT_SCREEN_PATCH
    private final Handler bootHandler = new Handler(Looper.getMainLooper());
    private FrameLayout bootOverlay;
    private MediaPlayer introPlayer;
    private ObjectAnimator bootPulse;
    private final Runnable dismissBootRunnable = this::dismissBootScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        drawerLayout = findViewById(R.id.DrawerLayout);
        NavigationView navigationView = findViewById(R.id.NavigationView);
        navigationView.setNavigationItemSelectedListener(this);

        setSupportActionBar(findViewById(R.id.Toolbar));
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        Intent intent = getIntent();
        showWelcomeBootScreenIfNeeded(intent, savedInstanceState);
        navigationView.post(this::installMenuLongPressDescriptions);
        editInputControls = intent.getBooleanExtra("edit_input_controls", false);
        if (editInputControls) {
            selectedProfileId = intent.getIntExtra("selected_profile_id", 0);
            actionBar.setHomeAsUpIndicator(R.drawable.icon_action_bar_back);
            onNavigationItemSelected(navigationView.getMenu().findItem(R.id.menu_item_input_controls));
            navigationView.setCheckedItem(R.id.menu_item_input_controls);
        }
        else {
            boolean showShortcutsFirst = preferences.getBoolean("show_shortcuts_first", false);
            int selectedMenuItemId = intent.getIntExtra("selected_menu_item_id", 0);
            int menuItemId = selectedMenuItemId > 0 ? selectedMenuItemId : (showShortcutsFirst ? R.id.menu_item_shortcuts : R.id.menu_item_containers);

            actionBar.setHomeAsUpIndicator(R.drawable.icon_action_bar_menu);
            onNavigationItemSelected(navigationView.getMenu().findItem(menuItemId));
            navigationView.setCheckedItem(menuItemId);
            if (!requestAppPermissions()) RootFSInstaller.installIfNeeded(this);

            int containerId = intent.getIntExtra("container_id", 0);
            String startPath = intent.getStringExtra("start_path");
            if (containerId > 0 && startPath != null) {
                showFragment(new ContainerFileManagerFragment(containerId, startPath));
            }
        }
    }


    // WINEHUB_BOOT_SCREEN_PATCH
    private void showWelcomeBootScreenIfNeeded(Intent intent, Bundle savedInstanceState) {
        if (savedInstanceState != null || intent == null) return;

        boolean launcherStart =
            Intent.ACTION_MAIN.equals(intent.getAction()) &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER);

        boolean specialStart =
            intent.getBooleanExtra("edit_input_controls", false) ||
            intent.getIntExtra("container_id", 0) > 0 ||
            intent.hasExtra("start_path") ||
            intent.hasExtra("selected_menu_item_id");

        if (!launcherStart || specialStart) return;

        bootOverlay = new FrameLayout(this);
        bootOverlay.setClickable(true);
        bootOverlay.setFocusable(true);

        GradientDrawable backdrop = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[] { Color.rgb(5, 5, 7), Color.rgb(34, 17, 8), Color.rgb(5, 5, 7) }
        );
        bootOverlay.setBackground(backdrop);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(28), dp(24), dp(28), dp(24));

        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        bootOverlay.addView(content, contentParams);

        TextView emblem = new TextView(this);
        emblem.setText("W");
        emblem.setTextColor(Color.rgb(255, 138, 36));
        emblem.setTextSize(62);
        emblem.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        emblem.setGravity(Gravity.CENTER);
        emblem.setBackground(makeBootCircle());
        LinearLayout.LayoutParams emblemParams = new LinearLayout.LayoutParams(dp(112), dp(112));
        emblemParams.gravity = Gravity.CENTER;
        content.addView(emblem, emblemParams);

        TextView title = new TextView(this);
        title.setText("WINE-HUB");
        title.setTextColor(Color.WHITE);
        title.setTextSize(34);
        title.setLetterSpacing(0.12f);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(22), 0, dp(4));
        content.addView(title, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText("WINDOWS APPS  •  ARM64 ANDROID");
        subtitle.setTextColor(Color.rgb(255, 178, 104));
        subtitle.setTextSize(12);
        subtitle.setLetterSpacing(0.10f);
        subtitle.setGravity(Gravity.CENTER);
        content.addView(subtitle, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView signature = new TextView(this);
        signature.setText("BUILT BY ZOZ\nCOOL O 7ARA CHRAB");
        signature.setTextColor(Color.rgb(204, 204, 211));
        signature.setTextSize(13);
        signature.setGravity(Gravity.CENTER);
        signature.setLineSpacing(dp(5), 1f);
        signature.setPadding(0, dp(30), 0, dp(24));
        content.addView(signature, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(
            ColorStateList.valueOf(Color.rgb(255, 138, 36)));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(52), dp(4));
        progressParams.gravity = Gravity.CENTER;
        content.addView(progress, progressParams);

        TextView status = new TextView(this);
        status.setText("INITIALIZING WINE-HUB");
        status.setTextColor(Color.rgb(255, 178, 104));
        status.setTextSize(10);
        status.setLetterSpacing(0.16f);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(16), 0, dp(10));
        content.addView(status, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView dateTime = new TextView(this);
        dateTime.setText(new SimpleDateFormat(
            "EEE, dd MMM  •  HH:mm", Locale.getDefault()).format(new Date()));
        dateTime.setTextColor(Color.rgb(153, 153, 163));
        dateTime.setTextSize(12);
        dateTime.setGravity(Gravity.CENTER);
        content.addView(dateTime, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView skip = new TextView(this);
        skip.setText("TAP ANYWHERE TO CONTINUE");
        skip.setTextColor(Color.rgb(125, 125, 135));
        skip.setTextSize(10);
        skip.setGravity(Gravity.CENTER);
        skip.setPadding(0, dp(32), 0, 0);
        content.addView(skip, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bootOverlay.setAlpha(0f);
        ViewGroup rootContent = findViewById(android.R.id.content);
        rootContent.addView(bootOverlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bootOverlay.animate().alpha(1f).setDuration(420).start();

        bootPulse = ObjectAnimator.ofFloat(emblem, View.ALPHA, 0.72f, 1f);
        bootPulse.setDuration(850);
        bootPulse.setRepeatMode(ValueAnimator.REVERSE);
        bootPulse.setRepeatCount(ValueAnimator.INFINITE);
        bootPulse.start();

        bootOverlay.setOnClickListener(v -> dismissBootScreen());

        try {
            introPlayer = new MediaPlayer();
            android.content.res.AssetFileDescriptor afd = getAssets().openFd("intro.mp3");
            try {
                introPlayer.setDataSource(
                    afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            } finally {
                afd.close();
            }
            introPlayer.setOnCompletionListener(mp -> releaseIntroPlayer());
            introPlayer.setOnErrorListener((mp, what, extra) -> {
                releaseIntroPlayer();
                return true;
            });
            introPlayer.setVolume(0.65f, 0.65f);
            introPlayer.setOnPreparedListener(mp -> {
                if (bootOverlay != null && !isFinishing()) {
                    try { mp.start(); } catch (IllegalStateException ignored) {}
                } else {
                    releaseIntroPlayer();
                }
            });
            introPlayer.prepareAsync();
        } catch (Exception ignored) {
            releaseIntroPlayer();
        }

        // Short, skippable brand intro; it never blocks app initialization.
        bootHandler.postDelayed(dismissBootRunnable, 2800);
    }

    private GradientDrawable makeBootCircle() {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.rgb(22, 14, 10));
        circle.setStroke(dp(2), Color.rgb(255, 138, 36));
        return circle;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void dismissBootScreen() {
        bootHandler.removeCallbacks(dismissBootRunnable);
        if (bootPulse != null) {
            bootPulse.cancel();
            bootPulse = null;
        }
        if (bootOverlay != null) {
            FrameLayout oldOverlay = bootOverlay;
            bootOverlay = null;
            oldOverlay.animate().cancel();
            oldOverlay.animate().alpha(0f).setDuration(220).withEndAction(() -> {
                if (oldOverlay.getParent() instanceof ViewGroup) {
                    ((ViewGroup) oldOverlay.getParent()).removeView(oldOverlay);
                }
            }).start();
        }
        releaseIntroPlayer();
    }

    private void releaseIntroPlayer() {
        MediaPlayer player = introPlayer;
        introPlayer = null;
        if (player != null) {
            try { player.setOnCompletionListener(null); } catch (Exception ignored) {}
            try { player.setOnErrorListener(null); } catch (Exception ignored) {}
            try { player.setOnPreparedListener(null); } catch (Exception ignored) {}
            try { if (player.isPlaying()) player.stop(); } catch (Exception ignored) {}
            try { player.reset(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
        }
    }

    private void installMenuLongPressDescriptions() {
        if (isFinishing()) return;

        setMenuDescription(R.id.menu_item_shortcuts,
            "Shortcuts — quick access to your favourite Windows apps and launch entries.");
        setMenuDescription(R.id.menu_item_containers,
            "Containers — create, configure and launch isolated Windows environments.");
        setMenuDescription(R.id.menu_item_input_controls,
            "Input Controls — configure touch controls, buttons and input profiles.");
        setMenuDescription(R.id.menu_item_settings,
            "Settings — customize WINE-HUB preferences and system behaviour.");
        setMenuDescription(R.id.menu_item_about,
            "About — view WINE-HUB version and project information.");
    }

    private void setMenuDescription(int itemId, String description) {
        NavigationView nav = findViewById(R.id.NavigationView);
        if (nav == null) return;

        MenuItem item = nav.getMenu().findItem(itemId);
        if (item != null && android.os.Build.VERSION.SDK_INT >= 26) {
            item.setTooltipText(description);
        }

        View itemView = nav.findViewById(itemId);
        if (itemView != null) {
            itemView.setOnLongClickListener(v -> {
                Toast.makeText(MainActivity.this, description, Toast.LENGTH_LONG).show();
                return true;
            });
            itemView.setTooltipText(description);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(true);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setSystemLocale(newBase));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                RootFSInstaller.installIfNeeded(this);
            }
            else finish();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MainActivity.OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (openFileCallback != null) {
                openFileCallback.call(data.getData());
                openFileCallback = null;
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if ((newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) && currentFragment instanceof BaseFileManagerFragment) {
            ((BaseFileManagerFragment)currentFragment).onOrientationChanged();
        }
    }

    @Override
    public void onBackPressed() {
        if (currentFragment != null && currentFragment.isVisible()) {
            if (currentFragment instanceof BaseFileManagerFragment) {
                BaseFileManagerFragment fileManagerFragment = (BaseFileManagerFragment)currentFragment;
                if (fileManagerFragment.onBackPressed()) return;
            }
            else if (currentFragment instanceof ContainersFragment) {
                finish();
            }
        }

        showFragment(new ContainersFragment());
    }

    public void setOpenFileCallback(Callback<Uri> openFileCallback) {
        this.openFileCallback = openFileCallback;
    }

    private boolean requestAppPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return false;

        String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        if (itemId == R.id.menu_item_add ||
            itemId == R.id.menu_item_home ||
            itemId == R.id.menu_item_view_style ||
            itemId == R.id.menu_item_new_folder) {
            return super.onOptionsItemSelected(menuItem);
        }
        else {
            if (editInputControls) {
                setResult(RESULT_OK);
                finish();
            }
            else {
                if (currentFragment instanceof BaseFileManagerFragment) {
                    BaseFileManagerFragment fileManagerFragment = (BaseFileManagerFragment)currentFragment;
                    if (fileManagerFragment.onOptionsMenuClicked()) return true;
                }
                drawerLayout.openDrawer(GravityCompat.START);
            }
            return true;
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        switch (item.getItemId()) {
            case R.id.menu_item_shortcuts:
                preferences.edit().putBoolean("show_shortcuts_first", true).apply();
                showFragment(new ShortcutsFragment());
                break;
            case R.id.menu_item_containers:
                preferences.edit().putBoolean("show_shortcuts_first", false).apply();
                showFragment(new ContainersFragment());
                break;
            case R.id.menu_item_input_controls:
                showFragment(new InputControlsFragment(selectedProfileId));
                break;
            case R.id.menu_item_settings:
                showFragment(new SettingsFragment());
                break;
            case R.id.menu_item_about:
                (new AboutDialog(this)).show();
                break;
        }
        return true;
    }

    @Override
    protected void onPause() {
        // Stop the intro immediately if the app leaves the foreground.
        dismissBootScreen();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        bootHandler.removeCallbacksAndMessages(null);
        if (bootPulse != null) {
            bootPulse.cancel();
            bootPulse = null;
        }
        releaseIntroPlayer();
        if (bootOverlay != null && bootOverlay.getParent() instanceof ViewGroup) {
            ((ViewGroup) bootOverlay.getParent()).removeView(bootOverlay);
            bootOverlay = null;
        }
        super.onDestroy();
    }

    public void showFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
            .replace(R.id.FLFragmentContainer, fragment)
            .commit();

        drawerLayout.closeDrawer(GravityCompat.START);
        currentFragment = fragment;
    }
}