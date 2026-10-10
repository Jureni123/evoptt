/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package se.lublin.mumla.app;

import static java.util.Objects.requireNonNull;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.jetbrains.annotations.NotNull;

import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import se.lublin.humla.HumlaService;
import se.lublin.humla.IHumlaService;
import se.lublin.humla.IHumlaSession;
import se.lublin.humla.model.Server;
import se.lublin.humla.net.HandshakeFailure;
import se.lublin.humla.net.HumlaCertificateGenerator;
import se.lublin.humla.net.TlsHostnameVerifier;
import se.lublin.humla.protobuf.Mumble;
import se.lublin.humla.util.HumlaException;
import se.lublin.humla.util.HumlaObserver;
import se.lublin.humla.util.MumbleURLParser;
import se.lublin.mumla.BuildConfig;
import se.lublin.mumla.R;
import se.lublin.mumla.Settings;
import se.lublin.mumla.channel.AccessTokenFragment;
import se.lublin.mumla.channel.ChannelFragment;
import se.lublin.mumla.channel.ServerInfoFragment;
import se.lublin.mumla.db.DatabaseCertificate;
import se.lublin.mumla.db.DatabaseProvider;
import se.lublin.mumla.db.MumlaDatabase;
import se.lublin.mumla.db.MumlaSQLiteDatabase;
import se.lublin.mumla.preference.MumlaCertificateGenerateTask;
import se.lublin.mumla.preference.SettingsActivity;
import se.lublin.mumla.servers.FavouriteServerListFragment;
import se.lublin.mumla.servers.ServerEditFragment;
import se.lublin.mumla.service.IMumlaService;
import se.lublin.mumla.service.MumlaService;
import se.lublin.mumla.util.HumlaServiceFragment;
import se.lublin.mumla.util.HumlaServiceProvider;
import se.lublin.mumla.util.MumlaTrustStore;

public class MumlaActivity extends BaseActivity implements ListView.OnItemClickListener,
        FavouriteServerListFragment.ServerConnectHandler, HumlaServiceProvider, DatabaseProvider,
        DrawerAdapter.DrawerDataProvider, ServerEditFragment.ServerEditListener {
    private static final String TAG = MumlaActivity.class.getName();

    // --- AUTO-CONNECT LOGIC: KONFIGURASI SERVER ---
    // GANTI DENGAN ALAMAT SERVER DAN PORT ASLIMU DI SINI
    private static final String EMBEDDED_SERVER_HOST = "mumble.samto.my.id"; 
    private static final int EMBEDDED_SERVER_PORT = 22222;
    private static final String EMBEDDED_SERVER_NAME = "Blambangan Online";
    private static final String PREF_EMBEDDED_SETUP_DONE = "embedded_server_setup_done";
    
    private boolean mAutoConnectAttempted = false;
    // ----------------------------------------------

    public static final String EXTRA_DRAWER_FRAGMENT = "drawer_fragment";

    private IMumlaService mService;
    private MumlaDatabase mDatabase;
    private Settings mSettings;

    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
    private DrawerAdapter mDrawerAdapter;

    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;
    private static final int PERMISSIONS_REQUEST_POST_NOTIFICATIONS = 2;
    private static final int PERMISSIONS_REQUEST_BLUETOOTH_CONNECT = 3;
    private Server mServerPendingPerm = null;
    private boolean mPermPostNotificationsAsked = false;
    private boolean mPermBluetoothAsked = false;
    private static final String STATE_SERVER_PENDING = "server_pending";
    private static final String STATE_POST_NOTIF_ASKED = "post_notif_asked";
    private static final String STATE_BT_ASKED = "bt_asked";

    private AlertDialog mConnectingDialog;
    private AlertDialog mErrorDialog;
    private AlertDialog mCertDialog;
    private AlertDialog mFirstRunDialog;
    private boolean mFirstRunGenerateInFlight = false;

    private final List<HumlaServiceFragment> mServiceFragments = new ArrayList<HumlaServiceFragment>();

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((MumlaService.MumlaBinder) service).getService();
            mService.setSuppressNotifications(true);
            mService.registerObserver(mObserver);
            mService.clearChatNotifications();
            mDrawerAdapter.notifyDataSetChanged();
            setVolumeControlStream(useVoiceCallVolume() ?
                    AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);

            for (HumlaServiceFragment fragment : mServiceFragments)
                fragment.setServiceBound(true);

            if (getSupportFragmentManager().findFragmentById(R.id.content_frame) instanceof HumlaServiceFragment &&
                    !mService.isConnected()) {
                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES);
            }

            // --- AUTO-CONNECT LOGIC: CEK DAN CONNECT SETELAH SERVICE SIAP ---
            if (!EMBEDDED_SERVER_HOST.isEmpty() && !mAutoConnectAttempted) {
                mAutoConnectAttempted = true;
                
                boolean isSetupDone = PreferenceManager.getDefaultSharedPreferences(MumlaActivity.this)
                        .getBoolean(PREF_EMBEDDED_SETUP_DONE, false);
                
                if (isSetupDone) {
                    Server embedded = findOrCreateEmbeddedServer();
                    if (embedded != null) {
                        connectToServer(embedded);
                    } else {
                        loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES);
                    }
                } else {
                    loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES);
                }
            }
            // ----------------------------------------------------------------

            updateConnectionState(getService());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    private final HumlaObserver mObserver = new HumlaObserver() {
        @Override
        public void onConnected() {
            if (mSettings.shouldStartUpInPinnedMode()) {
                loadDrawerFragment(DrawerAdapter.ITEM_PINNED_CHANNELS);
            } else {
                loadDrawerFragment(DrawerAdapter.ITEM_SERVER);
            }
            getSupportFragmentManager().executePendingTransactions();
            mDrawerAdapter.notifyDataSetChanged();
            supportInvalidateOptionsMenu();
            updateConnectionState(getService());
        }

        @Override
        public void onConnecting() {
            updateConnectionState(getService());
        }

        @Override
        public void onDisconnected(HumlaException e) {
            if (getSupportFragmentManager().findFragmentById(R.id.content_frame) instanceof HumlaServiceFragment) {
                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES);
                getSupportFragmentManager().executePendingTransactions();
            }
            mDrawerAdapter.notifyDataSetChanged();
            supportInvalidateOptionsMenu();
            updateConnectionState(getService());
        }

        @Override
        public void onBluetoothScoChanged(boolean active) {
            setVolumeControlStream(useVoiceCallVolume() ?
                    AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
            supportInvalidateOptionsMenu();
        }

        @Override
        public void onTLSHandshakeFailed(X509Certificate[] chain, HandshakeFailure failure, String verifiedHost) {
            if (chain == null || chain.length == 0 || chain[0] == null) {
                return;
            }
            if (getService() == null || getService().getTargetServer() == null) {
                return;
            }
            final Server lastServer = getService().getTargetServer();
            
            // --- AUTO-CONNECT LOGIC: AUTO-TRUST SERTIFIKAT SERVER KITA ---
            if (lastServer != null && 
                EMBEDDED_SERVER_HOST.equalsIgnoreCase(lastServer.getHost()) &&
                lastServer.getPort() == EMBEDDED_SERVER_PORT) {
                try {
                    X509Certificate x509 = chain[0];
                    String alias = lastServer.getHost();
                    KeyStore trustStore = MumlaTrustStore.getTrustStore(MumlaActivity.this);
                    trustStore.setCertificateEntry(alias, x509);
                    MumlaTrustStore.saveTrustStore(MumlaActivity.this, trustStore);
                    Log.d(TAG, "Auto-trusted certificate for embedded server: " + alias);
                    connectToServer(lastServer);
                    return; // Keluar, jangan tampilkan dialog
                } catch (Exception e) {
                    Log.e(TAG, "Failed to auto-trust embedded server cert", e);
                }
            }
            // -------------------------------------------------------------

            final X509Certificate x509 = chain[0];
            String host = verifiedHost != null ? verifiedHost : lastServer.getHost();
            host = TlsHostnameVerifier.canonicalizeHost(host);
            if (host == null) {
                return;
            }
            if (failure == HandshakeFailure.HOSTNAME_MISMATCH) {
                showMismatchDialog(host, x509);
                return;
            }
            X509Certificate pinned = pinnedForHost(host);
            if (failure == HandshakeFailure.PIN_CHANGED || pinned != null) {
                showChangedDialog(lastServer, host, x509, pinned);
                return;
            }
            showFirstTrustDialog(lastServer, host, x509);
        }

        private void showMismatchDialog(String host, X509Certificate x509) {
            List<String> claimed = TlsHostnameVerifier.claimedNames(x509);
            String names = claimed.isEmpty() ? getString(R.string.unknown) : TextUtils.join(", ", claimed);
            View layout = getLayoutInflater().inflate(R.layout.certificate_info, null);
            TextView textView = layout.findViewById(R.id.certificate_info_text);
            textView.setText(getString(R.string.certificate_identity_mismatch_body, host, names)
                    + "\n\n" + certificateDetails(x509));
            dismissCertDialog();
            mCertDialog = new MaterialAlertDialogBuilder(MumlaActivity.this)
                    .setTitle(R.string.certificate_identity_mismatch)
                    .setView(layout)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }

        private void showFirstTrustDialog(Server server, String host, X509Certificate x509) {
            View layout = getLayoutInflater().inflate(R.layout.certificate_info, null);
            TextView textView = layout.findViewById(R.id.certificate_info_text);
            textView.setText(getString(R.string.untrusted_certificate_body, host)
                    + "\n\n" + certificateDetails(x509));
            dismissCertDialog();
            mCertDialog = new MaterialAlertDialogBuilder(MumlaActivity.this)
                    .setTitle(R.string.untrusted_certificate)
                    .setView(layout)
                    .setPositiveButton(R.string.allow, (dialog, which) -> pinAndReconnect(server, host, x509))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private void showChangedDialog(Server server, String host, X509Certificate presented, X509Certificate pinned) {
            View layout = getLayoutInflater().inflate(R.layout.certificate_info, null);
            TextView textView = layout.findViewById(R.id.certificate_info_text);
            String oldPrint = pinned != null ? sha256Fingerprint(pinned) : getString(R.string.unknown);
            textView.setText(getString(R.string.certificate_changed_body, host, oldPrint, sha256Fingerprint(presented))
                    + "\n\n" + certificateDetails(presented));
            dismissCertDialog();
            mCertDialog = new MaterialAlertDialogBuilder(MumlaActivity.this)
                    .setTitle(R.string.certificate_changed_title)
                    .setView(layout)
                    .setPositiveButton(R.string.replace_certificate, (dialog, which) -> pinAndReconnect(server, host, presented))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private X509Certificate pinnedForHost(String host) {
            try {
                KeyStore trustStore = MumlaTrustStore.getTrustStore(MumlaActivity.this);
                if (host != null && trustStore.containsAlias(host)) {
                    Certificate pinned = trustStore.getCertificate(host);
                    if (pinned instanceof X509Certificate) {
                        return (X509Certificate) pinned;
                    }
                }
            } catch (CertificateException | IOException | KeyStoreException | NoSuchAlgorithmException e) {
                Log.e(TAG, "Could not read trust store", e);
            }
            return null;
        }

        private void pinAndReconnect(Server server, String host, X509Certificate x509) {
            try {
                KeyStore trustStore = MumlaTrustStore.getTrustStore(MumlaActivity.this);
                trustStore.setCertificateEntry(host, x509);
                MumlaTrustStore.saveTrustStore(MumlaActivity.this, trustStore);
                Toast.makeText(MumlaActivity.this, R.string.trust_added, Toast.LENGTH_LONG).show();
                connectToServer(server);
            } catch (CertificateException | IOException | KeyStoreException | NoSuchAlgorithmException e) {
                Log.e(TAG, "Could not write trust store", e);
                Toast.makeText(MumlaActivity.this, R.string.trust_add_failed, Toast.LENGTH_LONG).show();
            }
        }

        private String certificateDetails(X509Certificate x509) {
            try {
                String hexDigest1 = colonHex(MessageDigest.getInstance("SHA-1").digest(x509.getEncoded()));
                String hexDigest2 = colonHex(MessageDigest.getInstance("SHA-256").digest(x509.getEncoded()));

                return getString(R.string.certificate_info,
                        x509.getSubjectX500Principal().getName(),
                        x509.getNotBefore().toString(),
                        x509.getNotAfter().toString(),
                        hexDigest1,
                        hexDigest2);
            } catch (NoSuchAlgorithmException | CertificateException e) {
                Log.e(TAG, "Could not fingerprint certificate", e);
                return x509.toString();
            }
        }

        private String sha256Fingerprint(X509Certificate x509) {
            try {
                return colonHex(MessageDigest.getInstance("SHA-256").digest(x509.getEncoded()));
            } catch (NoSuchAlgorithmException | CertificateException e) {
                Log.e(TAG, "Could not fingerprint certificate", e);
                return getString(R.string.unknown);
            }
        }

        private String colonHex(byte[] bytes) {
            String hex = bytesToHex(bytes);
            StringBuilder out = new StringBuilder(hex.length() + hex.length() / 2);
            for (int i = 0; i < hex.length(); i += 2) {
                if (i > 0) {
                    out.append(':');
                }
                out.append(hex, i, Math.min(i + 2, hex.length()));
            }
            return out.toString();
        }

        @Override
        public void onPermissionDenied(String reason) {
            new MaterialAlertDialogBuilder(MumlaActivity.this)
                    .setTitle(R.string.perm_denied)
                    .setMessage(reason)
                    .show();
        }
    };

    private void dismissCertDialog() {
        if (mCertDialog != null) {
            mCertDialog.dismiss();
            mCertDialog = null;
        }
    }

    private void dismissFirstRunDialog() {
        if (mFirstRunDialog != null) {
            mFirstRunDialog.dismiss();
            mFirstRunDialog = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mSettings = Settings.getInstance(this);

        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mPermBluetoothAsked = savedInstanceState.getBoolean(STATE_BT_ASKED, false);
            mPermPostNotificationsAsked = savedInstanceState.getBoolean(STATE_POST_NOTIF_ASKED, false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mServerPendingPerm = savedInstanceState.getParcelable(STATE_SERVER_PENDING, Server.class);
            } else {
                mServerPendingPerm = savedInstanceState.getParcelable(STATE_SERVER_PENDING);
            }
        }
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mService != null && mService.isConnected()) {
                    new MaterialAlertDialogBuilder(MumlaActivity.this)
                            .setMessage(getString(R.string.disconnectSure, mService.getTargetServer().getName()))
                            .setPositiveButton(R.string.confirm, (dialog, which) -> {
                                mService.disconnect();
                                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES);
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        setStayAwake(mSettings.shouldStayAwake());

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);

        mDatabase = new MumlaSQLiteDatabase(this);
        mDatabase.open();

        mDrawerLayout = findViewById(R.id.drawer_layout);
        ListView mDrawerList = findViewById(R.id.left_drawer);

        View headerView = getLayoutInflater().inflate(R.layout.list_drawer_headerlogo, mDrawerList, false);
        mDrawerList.addHeaderView(headerView, null, false);

        mDrawerList.setOnItemClickListener(this);
        mDrawerAdapter = new DrawerAdapter(this, this);
        mDrawerList.setAdapter(mDrawerAdapter);
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close) {
            @Override
            public void onDrawerClosed(View drawerView) {
                supportInvalidateOptionsMenu();
            }

            @Override
            public void onDrawerStateChanged(int newState) {
                super.onDrawerStateChanged(newState);
                if (getService() != null && getService().isConnected()) {
                    IHumlaSession session = getService().HumlaSession();
                    if (session.isTalking() && !mSettings.isPushToTalkToggle()) {
                        session.setTalkingState(false);
                    }
                }
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                supportInvalidateOptionsMenu();
            }
        };

        mDrawerLayout.setDrawerListener(mDrawerToggle);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        if (savedInstanceState == null) {
            if (getIntent() != null && getIntent().hasExtra(EXTRA_DRAWER_FRAGMENT)) {
                loadDrawerFragment(getIntent().getIntExtra(EXTRA_DRAWER_FRAGMENT, DrawerAdapter.ITEM_FAVOURITES));
            } else {
                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES);
            }
        }

        if (savedInstanceState == null) {
            handleViewIntent(getIntent());
        }

        setVolumeControlStream(useVoiceCallVolume() ?
                AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);

        if (savedInstanceState == null) {
            // --- MATIKAN DIALOG NATIVE FIRST-RUN JIKA KITA HANDLE SENDIRI ---
            boolean isEmbeddedSetupDone = PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean(PREF_EMBEDDED_SETUP_DONE, false);

            if (!isEmbeddedSetupDone) {
                mSettings.setFirstRun(false); // Cegah dialog "Welcome to Mumla" muncul
            } else {
                if (mSettings.isFirstRun()) {
                    mSettings.setFirstRun(false);
                }
                new StartupAction().execute(this);
            }
            // ----------------------------------------------------------------
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent connectIntent = new Intent(this, MumlaService.class);
        bindService(connectIntent, mConnection, 0);

        // --- TAMPILKAN DIALOG NAMA DI ATAS LAYAR FAVORITES JIKA BELUM SETUP ---
        if (!EMBEDDED_SERVER_HOST.isEmpty() && !mAutoConnectAttempted) {
            boolean isSetupDone = PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean(PREF_EMBEDDED_SETUP_DONE, false);
            
            if (!isSetupDone) {
                Server embedded = findOrCreateEmbeddedServer();
                if (embedded != null) {
                    showEmbeddedServerCredentialsDialog(embedded);
                    mAutoConnectAttempted = true;
                }
            }
        }
        // ----------------------------------------------------------------------
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mErrorDialog != null) mErrorDialog.dismiss();
        if (mConnectingDialog != null) mConnectingDialog.dismiss();
        dismissCertDialog();
        dismissFirstRunDialog();

        if (mService != null) {
            mService.onTalkKeyCancel();
            for (HumlaServiceFragment fragment : mServiceFragments) {
                fragment.setServiceBound(false);
            }
            mService.unregisterObserver(mObserver);
            mService.setSuppressNotifications(false);
        }
        unbindService(mConnection);
    }

    private void handleViewIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return;
        }
        try {
            Server server = MumbleURLParser.parseURL(intent.getDataString());
            String urlPassword = server.getPassword();
            boolean hasUrlPassword = urlPassword != null && !urlPassword.isEmpty();
            DialogFragment fragment = ServerEditFragment.createServerEditDialog(
                    server, ServerEditFragment.Action.CONNECT_ACTION, true, hasUrlPassword);
            fragment.show(getSupportFragmentManager(), "url_edit");
        } catch (MalformedURLException e) {
            Toast.makeText(this, getString(R.string.mumble_url_parse_failed), Toast.LENGTH_LONG).show();
            Log.w(TAG, "Could not parse Mumble URL", e);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleViewIntent(intent);
    }

    @Override
    protected void onDestroy() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        mDatabase.close();
        super.onDestroy();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem disconnectButton = menu.findItem(R.id.action_disconnect);
        disconnectButton.setVisible(mService != null && mService.isConnected());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.mumla, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NotNull MenuItem item) {
        if (mDrawerToggle.onOptionsItemSelected(item)) return true;
        if (item.getItemId() == R.id.action_disconnect) {
            getService().disconnect();
            return true;
        }
        return false;
    }

    private boolean useVoiceCallVolume() {
        if (mSettings.isHandsetMode()) {
            return true;
        }
        return mService != null && mService.isBluetoothScoActive();
    }

    public void setBluetoothHeadset(boolean enabled) {
        if (mSettings.isBluetoothHeadset() == enabled) {
            return;
        }
        mSettings.setBluetoothHeadset(enabled);
    }

    @Override
    public void onConfigurationChanged(@NotNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int pttKey = mSettings.getPushToTalkKey();
        if (mService != null && Settings.isPttKeyBound(pttKey, keyCode)) {
            mService.onTalkKeyDown();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        int pttKey = mSettings.getPushToTalkKey();
        if (mService != null && Settings.isPttKeyBound(pttKey, keyCode)) {
            mService.onTalkKeyUp();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && mService != null) {
            mService.onTalkKeyCancel();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mDrawerLayout.closeDrawers();
        loadDrawerFragment((int) id);
    }

    private void showFirstRunGuide() {
        if (mSettings.isUsingCertificate()) {
            mSettings.setFirstRun(false);
            new StartupAction().execute(this);
            return;
        }
        String msg = getString(R.string.first_run_generate_certificate);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.first_run_generate_certificate_title)
                .setMessage(msg)
                .setPositiveButton(R.string.generate, (DialogInterface dialog, int which) -> {
                    mFirstRunGenerateInFlight = true;
                    MumlaCertificateGenerateTask generateTask = new MumlaCertificateGenerateTask(MumlaActivity.this) {
                        @Override
                        protected void onPostExecute(DatabaseCertificate result) {
                            super.onPostExecute(result);
                            mFirstRunGenerateInFlight = false;
                            if (result != null) mSettings.setDefaultCertificateId(result.getId());
                            if (!isFinishing() && !isDestroyed()) {
                                new StartupAction().execute(MumlaActivity.this);
                            }
                        }
                    };
                    generateTask.execute();
                    mSettings.setFirstRun(false);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(dialogInterface -> {
                    if (mFirstRunGenerateInFlight) return;
                    mSettings.setFirstRun(false);
                    if (isFinishing() || isDestroyed() || isChangingConfigurations()) return;
                    new StartupAction().execute(MumlaActivity.this);
                });
        mFirstRunDialog = builder.create();
        mFirstRunDialog.show();
    }

    private void loadDrawerFragment(int fragmentId) {
        Class<? extends Fragment> fragmentClass = null;
        Bundle args = new Bundle();
        switch (fragmentId) {
            case DrawerAdapter.ITEM_SERVER:
                fragmentClass = ChannelFragment.class;
                break;
            case DrawerAdapter.ITEM_INFO:
                fragmentClass = ServerInfoFragment.class;
                break;
            case DrawerAdapter.ITEM_ACCESS_TOKENS:
                fragmentClass = AccessTokenFragment.class;
                Server connectedServer = getService().getTargetServer();
                args.putLong("server", connectedServer.getId());
                args.putStringArrayList("access_tokens", (ArrayList<String>) mDatabase.getAccessTokens(connectedServer.getId()));
                break;
            case DrawerAdapter.ITEM_PINNED_CHANNELS:
                fragmentClass = ChannelFragment.class;
                args.putBoolean("pinned", true);
                break;
            case DrawerAdapter.ITEM_FAVOURITES:
                fragmentClass = FavouriteServerListFragment.class;
                break;
            case DrawerAdapter.ITEM_SETTINGS:
                Intent prefIntent = new Intent(this, SettingsActivity.class);
                startActivity(prefIntent);
                return;
            default:
                return;
        }
        Fragment fragment = Fragment.instantiate(this, fragmentClass.getName(), args);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content_frame, fragment, fragmentClass.getName())
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit();
        requireNonNull(getSupportActionBar()).setTitle(mDrawerAdapter.getItemWithId(fragmentId).title);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_BT_ASKED, mPermBluetoothAsked);
        outState.putBoolean(STATE_POST_NOTIF_ASKED, mPermPostNotificationsAsked);
        outState.putParcelable(STATE_SERVER_PENDING, mServerPendingPerm);
    }

    public void connectToServer(final Server server) {
        mServerPendingPerm = server;
        connectToServerWithPerm();
    }

    public void connectToServerWithPerm() {
        if (ContextCompat.checkSelfPermission(MumlaActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MumlaActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            return;
        }

        if (mSettings.isBluetoothHeadset() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(MumlaActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            if (!mPermBluetoothAsked) {
                mPermBluetoothAsked = true;
                ActivityCompat.requestPermissions(MumlaActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSIONS_REQUEST_BLUETOOTH_CONNECT);
                return;
            }
            mSettings.setBluetoothHeadset(false);
            Toast.makeText(MumlaActivity.this, getString(R.string.grant_perm_bluetooth), Toast.LENGTH_LONG).show();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !mPermPostNotificationsAsked) {
            mPermPostNotificationsAsked = true;
            if (ContextCompat.checkSelfPermission(MumlaActivity.this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MumlaActivity.this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSIONS_REQUEST_POST_NOTIFICATIONS);
                return;
            }
        }

        if (mServerPendingPerm == null) {
            Log.w(TAG, "No pending server after getting permissions");
            return;
        }

        Server server = mServerPendingPerm;
        mServerPendingPerm = null;

        if (mService != null && mService.isConnected()) {
            if (isSameServer(mService.getTargetServer(), server)) {
                Toast.makeText(this, R.string.already_connected, Toast.LENGTH_SHORT).show();
                return;
            }
            new MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.reconnect_dialog_message)
                    .setPositiveButton(R.string.connect, (dialog, which) -> {
                        mService.registerObserver(new HumlaObserver() {
                            @Override
                            public void onDisconnected(HumlaException e) {
                                connectToServer(server);
                                mService.unregisterObserver(this);
                            }
                        });
                        mService.disconnect();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        if (mService != null && mService.getConnectionState() == HumlaService.ConnectionState.CONNECTING) {
            if (isSameServer(mService.getTargetServer(), server)) {
                Toast.makeText(this, R.string.mumlaConnecting, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.already_connecting, Toast.LENGTH_LONG).show();
            }
            return;
        }

        ServerConnectTask connectTask = new ServerConnectTask(this, mDatabase);
        connectTask.execute(server);
    }

    static boolean isSameServer(Server a, Server b) {
        if (a == null || b == null) return a == b;
        if (a.isSaved() && b.isSaved()) return a.getId() == b.getId();
        return MumlaService.getServerKey(a).equals(MumlaService.getServerKey(b));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length == 0) {
            if (requestCode == PERMISSIONS_REQUEST_BLUETOOTH_CONNECT) {
                if (mServerPendingPerm != null) {
                    mSettings.setBluetoothHeadset(false);
                    Toast.makeText(MumlaActivity.this, getString(R.string.grant_perm_bluetooth), Toast.LENGTH_LONG).show();
                    connectToServerWithPerm();
                }
            } else if (requestCode == PERMISSIONS_REQUEST_POST_NOTIFICATIONS) {
                connectToServerWithPerm();
            } else if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
                Toast.makeText(MumlaActivity.this, getString(R.string.grant_perm_microphone), Toast.LENGTH_LONG).show();
            }
            return;
        }

        switch (requestCode) {
            case PERMISSIONS_REQUEST_RECORD_AUDIO:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    connectToServerWithPerm();
                } else {
                    Toast.makeText(MumlaActivity.this, getString(R.string.grant_perm_microphone), Toast.LENGTH_LONG).show();
                }
                break;
            case PERMISSIONS_REQUEST_BLUETOOTH_CONNECT:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    connectToServerWithPerm();
                } else {
                    mSettings.setBluetoothHeadset(false);
                    Toast.makeText(MumlaActivity.this, getString(R.string.grant_perm_bluetooth), Toast.LENGTH_LONG).show();
                    connectToServerWithPerm();
                }
                break;
            case PERMISSIONS_REQUEST_POST_NOTIFICATIONS:
                mPermPostNotificationsAsked = true;
                if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(MumlaActivity.this, Manifest.permission.POST_NOTIFICATIONS)) {
                        Toast.makeText(MumlaActivity.this, getString(R.string.grant_perm_notifications), Toast.LENGTH_LONG).show();
                    }
                }
                connectToServerWithPerm();
                break;
        }
    }

    private void setStayAwake(boolean stayAwake) {
        if (stayAwake) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void updateConnectionState(IHumlaService service) {
        if (mConnectingDialog != null) mConnectingDialog.dismiss();
        if (mErrorDialog != null) mErrorDialog.dismiss();

        if (service == null) return;

        switch (service.getConnectionState()) {
            case CONNECTING:
                Server server = service.getTargetServer();
                String host = (server != null && server.getHost() != null) ? server.getHost() : "";
                mConnectingDialog = new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.connecting_to_server, host))
                        .setView(R.layout.dialog_progress)
                        .setCancelable(true)
                        .setOnCancelListener(dialog -> {
                            service.disconnect();
                            Toast.makeText(MumlaActivity.this, R.string.cancelled, Toast.LENGTH_SHORT).show();
                        })
                        .create();
                mConnectingDialog.show();
                break;
            case CONNECTION_LOST:
                if (getService() != null && !getService().isErrorShown()) {
                    if (getService() == null) break;
                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(MumlaActivity.this);
                    builder.setTitle(getString(R.string.connectionRefused));
                    HumlaException error = getService().getConnectionError();
                    if (error != null && service.isReconnecting()) {
                        builder.setMessage(error.getMessage() + "\n\n" + getString(R.string.attempting_reconnect, error.getCause() != null ? error.getCause().getMessage() : "unknown"));
                        builder.setPositiveButton(R.string.cancel_reconnect, (dialog, which) -> {
                            if (getService() != null) {
                                getService().cancelReconnect();
                                getService().markErrorShown();
                            }
                        });
                    } else if (error != null && error.getReason() == HumlaException.HumlaDisconnectReason.REJECT &&
                            (error.getReject().getType() == Mumble.Reject.RejectType.WrongUserPW || error.getReject().getType() == Mumble.Reject.RejectType.WrongServerPW)) {
                        final EditText passwordField = new EditText(this);
                        passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        passwordField.setHint(R.string.password);
                        builder.setTitle(R.string.invalid_password);
                        builder.setMessage(error.getMessage());
                        builder.setView(passwordField);
                        builder.setPositiveButton(R.string.reconnect, (dialog, which) -> {
                            Server server1 = getService().getTargetServer();
                            if (server1 == null) return;
                            String password = passwordField.getText().toString();
                            server1.setPassword(password);
                            if (server1.isSaved()) mDatabase.updateServer(server1);
                            if (mService != null && mService.getConnectionState() == HumlaService.ConnectionState.CONNECTING) {
                                mService.registerObserver(new HumlaObserver() {
                                    @Override
                                    public void onDisconnected(HumlaException e) {
                                        connectToServer(server1);
                                        mService.unregisterObserver(this);
                                    }
                                });
                                mService.disconnect();
                            } else {
                                connectToServer(server1);
                            }
                        });
                        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                            if (getService() != null) getService().markErrorShown();
                        });
                    } else {
                        String msg = error != null ? error.getMessage() : getString(R.string.unknown);
                        builder.setMessage(msg);
                        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            if (getService() != null) getService().markErrorShown();
                        });
                    }
                    builder.setCancelable(false);
                    mErrorDialog = builder.show();
                }
                break;
        }
    }

    @Override
    public IMumlaService getService() {
        return mService;
    }

    @Override
    public MumlaDatabase getDatabase() {
        return mDatabase;
    }

    @Override
    public void addServiceFragment(HumlaServiceFragment fragment) {
        mServiceFragments.add(fragment);
    }

    @Override
    public void removeServiceFragment(HumlaServiceFragment fragment) {
        mServiceFragments.remove(fragment);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);
        if (key == null) return;
        switch (key) {
            case Settings.PREF_STAY_AWAKE:
                setStayAwake(mSettings.shouldStayAwake());
                break;
            case Settings.PREF_HANDSET_MODE:
            case Settings.PREF_BLUETOOTH_HEADSET:
                setVolumeControlStream(useVoiceCallVolume() ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
                break;
        }
    }

    @Override
    public boolean isConnected() {
        return mService != null && mService.isConnected();
    }

    @Override
    public String getConnectedServerName() {
        if (mService != null && mService.isConnected()) {
            Server server = mService.getTargetServer();
            return server.getName().isEmpty() ? server.getHost() : server.getName();
        }
        if (BuildConfig.DEBUG) throw new RuntimeException("getConnectedServerName should only be called if connected!");
        return "";
    }

    @Override
    public void onServerEdited(ServerEditFragment.Action action, Server server) {
        switch (action) {
            case ADD_ACTION:
                mDatabase.addServer(server);
                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES);
                break;
            case EDIT_ACTION:
                mDatabase.updateServer(server);
                loadDrawerFragment(DrawerAdapter.ITEM_FAVOURITES);
                break;
            case CONNECT_ACTION:
                connectToServer(server);
                break;
        }
    }

    // --- METODE PEMBANTU UNTUK AUTO-CONNECT (DIPERBAIKI TOTAL) ---
    
    private void showEmbeddedServerCredentialsDialog(final Server embedded) {
        int pad = (int) (getResources().getDisplayMetrics().density * 16.0f);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);
        
        TextView userLabel = new TextView(this);
        userLabel.setText("Masukkan Nama Panggilan:");
        layout.addView(userLabel);
        
        final EditText userEdit = new EditText(this);
        userEdit.setHint(mSettings.getDefaultUsername());
        userEdit.setSingleLine(true);
        layout.addView(userEdit);
        
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(layout);
        
        userEdit.post(() -> userEdit.requestFocus());
        
        new MaterialAlertDialogBuilder(this)
                .setTitle("Selamat Datang di " + EMBEDDED_SERVER_NAME)
                .setView(scrollView)
                .setCancelable(false)
                .setPositiveButton("SIMPAN & MASUK", (dialog, which) -> {
                    String username = userEdit.getText().toString().trim();
                    if (username.isEmpty()) {
                        username = mSettings.getDefaultUsername();
                    }
                    embedded.setUsername(username);
                    
                    // 1. Generate Cert Dulu (Diam-diam di background)
                    generateEmbeddedCertificateSilently(embedded);
                })
                .show();
    }

    private void generateEmbeddedCertificateSilently(final Server embedded) {
        final Context appContext = getApplicationContext();
        new AsyncTask<Void, Void, DatabaseCertificate>() {
            @Override
            protected DatabaseCertificate doInBackground(Void... params) {
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    HumlaCertificateGenerator.generateCertificate(baos);
                    String fileName = "AutoCert_" + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault()).format(new Date());
                    
                    MumlaSQLiteDatabase db = new MumlaSQLiteDatabase(appContext);
                    DatabaseCertificate dc = db.addCertificate(fileName, baos.toByteArray());
                    db.close();
                    return dc;
                } catch (Exception e) {
                    Log.e(TAG, "silent certificate generation failed", e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(DatabaseCertificate result) {
                if (result != null) {
                    // 2. Set Cert ID
                    mSettings.setDefaultCertificateId(result.getId());
                }
                
                // 3. BARU Simpan Server ke DB (setelah Cert siap & Nama ada)
                if (embedded.getId() == -1L) {
                    mDatabase.addServer(embedded);
                } else {
                    mDatabase.updateServer(embedded);
                }
                
                // 4. Tandai setup selesai
                PreferenceManager.getDefaultSharedPreferences(MumlaActivity.this)
                        .edit()
                        .putBoolean(PREF_EMBEDDED_SETUP_DONE, true)
                        .apply();
                
                // 5. Terakhir: Connect! (Server dan Cert sudah 100% siap)
                connectToServer(embedded);
            }
        }.execute();
    }

    private Server findOrCreateEmbeddedServer() {
        if (EMBEDDED_SERVER_HOST.isEmpty()) return null;
        
        for (Server existing : mDatabase.getServers()) {
            if (EMBEDDED_SERVER_HOST.equalsIgnoreCase(existing.getHost()) && 
                existing.getPort() == EMBEDDED_SERVER_PORT) {
                return existing;
            }
        }
        
        // Buat objek saja di memori, JANGAN simpan ke DB dulu (mencegah data kosong)
        return new Server(-1L, EMBEDDED_SERVER_NAME, EMBEDDED_SERVER_HOST, EMBEDDED_SERVER_PORT, "", "");
    }
    // --------------------------------------------------------

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}